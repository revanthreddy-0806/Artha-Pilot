"""Unit tests for the daily-refresh market data layer (no network)."""
from datetime import datetime, timedelta, timezone

import pytest

from app.core.config import Settings
from app.models.market_data import MarketDataSnapshot
from app.services import market_data_service as mds


def _payload(
    price=23_600.0,
    prev_close=23_500.0,
    epoch=None,
    error=None,
    missing_meta=False,
) -> dict:
    if error is not None:
        return {"chart": {"error": error}}
    if missing_meta:
        return {"chart": {"result": [{"meta": {}}]}}
    meta = {"regularMarketPrice": price}
    if prev_close is not None:
        meta["chartPreviousClose"] = prev_close
    if epoch is not None:
        meta["regularMarketTime"] = epoch
    return {"chart": {"result": [{"meta": meta}]}}


def test_parse_chart_payload_extracts_close_change_and_time():
    epoch = int(datetime(2026, 8, 8, tzinfo=timezone.utc).timestamp())
    price, change_pct, as_of = mds.parse_chart_payload(_payload(epoch=epoch))
    assert price == 23_600.0
    assert change_pct == pytest.approx(0.4255, abs=0.001)
    assert as_of == datetime(2026, 8, 8, tzinfo=timezone.utc)


def test_parse_chart_payload_missing_previous_close():
    price, change_pct, as_of = mds.parse_chart_payload(_payload(prev_close=None))
    assert price == 23_600.0
    assert change_pct is None
    assert as_of.tzinfo is not None


def test_parse_chart_payload_rejects_feed_error():
    with pytest.raises(ValueError, match="Feed error"):
        mds.parse_chart_payload(_payload(error={"code": 401, "description": "nope"}))


def test_parse_chart_payload_rejects_bad_shape():
    with pytest.raises(ValueError, match="no regularMarketPrice"):
        mds.parse_chart_payload(_payload(missing_meta=True))


def test_parse_symbol_defs():
    assert mds.parse_symbol_defs("^NSEI:NIFTY 50,GC=F:Gold (COMEX)") == [
        ("^NSEI", "NIFTY 50"),
        ("GC=F", "Gold (COMEX)"),
    ]
    assert mds.parse_symbol_defs("^NSEI:") == [("^NSEI", "^NSEI")]
    assert mds.parse_symbol_defs("  ,, ") == []


def test_quotes_keep_fresh_snapshot_when_feed_down(db_session):
    settings = Settings(MARKET_DATA_REFRESH_HOURS=24)
    healthy = mds.MarketDataService(db_session, settings=settings, fetcher=lambda symbol, **kw: _payload())
    quotes = healthy.quotes()
    assert len(quotes) == 2
    assert all(q.status == "live" for q in quotes)
    assert quotes[0].symbol == "^NSEI"

    def broken_fetcher(symbol, **kwargs):
        raise TimeoutError("feed down")

    service = mds.MarketDataService(db_session, settings=settings, fetcher=broken_fetcher)
    quotes_again = service.quotes()
    assert len(quotes_again) == 2
    assert all(q.status == "live" for q in quotes_again)
    assert quotes_again[0].price == 23_600.0


def test_stale_row_served_as_cached_when_refresh_fails(db_session):
    db_session.add(
        MarketDataSnapshot(
            symbol="^NSEI",
            name="NIFTY 50",
            price=10_000.0,
            change_pct=None,
            as_of=datetime.now(timezone.utc) - timedelta(days=5),
            source="old feed",
        )
    )
    db_session.commit()

    def broken_fetcher(symbol, **kwargs):
        raise TimeoutError("feed down")

    service = mds.MarketDataService(
        db_session,
        settings=Settings(MARKET_DATA_REFRESH_HOURS=24),
        fetcher=broken_fetcher,
    )
    quotes = service.quotes()
    nsei = next(q for q in quotes if q.symbol == "^NSEI")
    assert nsei.status == "cached"
    assert nsei.price == 10_000.0


def test_quotes_unavailable_when_nothing_cached(db_session):
    def broken_fetcher(symbol, **kwargs):
        raise TimeoutError("feed down")

    service = mds.MarketDataService(db_session, settings=Settings(MARKET_DATA_REFRESH_HOURS=24), fetcher=broken_fetcher)
    quotes = service.quotes()
    assert len(quotes) == 2
    assert all(q.status == "unavailable" for q in quotes)
    assert all(q.price == 0.0 for q in quotes)


def test_stale_row_is_refreshed(db_session):
    db_session.add(
        MarketDataSnapshot(
            symbol="^NSEI",
            name="NIFTY 50",
            price=10_000.0,
            change_pct=None,
            as_of=datetime.now(timezone.utc) - timedelta(days=5),
            source="previous",
        )
    )
    db_session.commit()

    service = mds.MarketDataService(
        db_session,
        settings=Settings(MARKET_DATA_REFRESH_HOURS=24),
        fetcher=lambda symbol, **kw: _payload(),
    )
    quotes = service.quotes()
    nsei = next(q for q in quotes if q.symbol == "^NSEI")
    assert nsei.status == "live"
    assert nsei.price == 23_600.0


def test_market_data_snapshot_upsert_replaces_values(db_session):
    repo = mds.MarketDataRepository(db_session)
    repo.upsert(
        MarketDataSnapshot(
            symbol="^NSEI", name="NIFTY 50", price=1.0, change_pct=0.0,
            as_of=datetime.now(timezone.utc), source="old",
        )
    )
    repo.upsert(
        MarketDataSnapshot(
            symbol="^NSEI", name="NIFTY 50", price=2.0, change_pct=1.0,
            as_of=datetime.now(timezone.utc), source="new",
        )
    )
    row = repo.get("^NSEI")
    assert row is not None
    assert row.price == 2.0
    assert row.source == "new"