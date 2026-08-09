"""Daily-refreshed market reference data (not real-time tick data).

Fetches public, keyless daily chart closes for a few well-known symbols
(Yahoo Finance public JSON), stores the latest snapshot per symbol in the
database and always fails gracefully: when the feed is unavailable the
service returns the last successful snapshot, or marks quotes as
unavailable so the recommendation engine can explain the gap instead of
fabricating numbers.
"""
from __future__ import annotations

import json
import logging
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Callable

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import Settings, get_settings
from app.models.market_data import MarketDataSnapshot

logger = logging.getLogger(__name__)

SOURCE_NAME = "Yahoo Finance (daily close)"
USER_AGENT = (
    "Mozilla/5.0 (Artha Pilot; educational personal finance app; "
    "daily-reference-data) compatible; +https://example.in/artha-pilot"
)


@dataclass(frozen=True)
class MarketQuote:
    """One reference quote with its data-quality status."""

    symbol: str
    name: str
    price: float
    change_pct: float | None
    as_of: datetime
    source: str
    status: str  # "live" (fresh) | "cached" (stale) | "unavailable"


def parse_symbol_defs(raw: str) -> list[tuple[str, str]]:
    """Parse "SYMBOL:Display Name,SYMBOL2:Name 2" config into (symbol, name)."""
    result: list[tuple[str, str]] = []
    for part in raw.split(","):
        part = part.strip()
        if not part:
            continue
        symbol, _, name = part.partition(":")
        result.append((symbol.strip(), name.strip() or symbol.strip()))
    return result


def fetch_chart_payload(
    symbol: str,
    *,
    url_template: str,
    timeout_seconds: int = 10,
) -> dict:
    """GET the public daily chart for a symbol; raise on any transport issue."""
    url = url_template.format(symbol=urllib.parse.quote(symbol))
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return json.loads(response.read().decode("utf-8"))


def parse_chart_payload(payload: dict) -> tuple[float, float | None, datetime]:
    """Extract (close price, change % vs previous close, as-of datetime).

    Raises ValueError when the payload does not contain a usable result
    (e.g. symbol not found or rate-limited response).
    """
    chart = payload.get("chart") or {}
    error = chart.get("error")
    if error:
        raise ValueError(f"Feed error: {error}")
    try:
        meta = chart["result"][0]["meta"]
    except (KeyError, IndexError, TypeError) as exc:
        raise ValueError("Unexpected feed payload") from exc

    price = meta.get("regularMarketPrice")
    if not isinstance(price, (int, float)):
        raise ValueError("Feed payload has no regularMarketPrice")

    prev_close = meta.get("chartPreviousClose") or meta.get("previousClose")
    change_pct = None
    if isinstance(prev_close, (int, float)) and prev_close != 0:
        change_pct = (price / prev_close - 1.0) * 100.0

    epoch = meta.get("regularMarketTime")
    if isinstance(epoch, (int, float)) and epoch > 0:
        as_of = datetime.fromtimestamp(epoch, tz=timezone.utc)
    else:
        as_of = datetime.now(timezone.utc)
    return float(price), change_pct, as_of


class MarketDataRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def get(self, symbol: str) -> MarketDataSnapshot | None:
        return self._db.scalar(select(MarketDataSnapshot).where(MarketDataSnapshot.symbol == symbol))

    def upsert(self, row: MarketDataSnapshot) -> MarketDataSnapshot:
        existing = self.get(row.symbol)
        if existing is None:
            self._db.add(row)
        else:
            existing.name = row.name
            existing.price = row.price
            existing.change_pct = row.change_pct
            existing.as_of = row.as_of
            existing.source = row.source
        self._db.commit()
        return row


class MarketDataService:
    def __init__(
        self,
        db: Session,
        settings: Settings | None = None,
        fetcher: Callable = fetch_chart_payload,
    ) -> None:
        self._db = db
        self._settings = settings or get_settings()
        self._fetcher = fetcher

    def _symbols(self) -> list[tuple[str, str]]:
        return parse_symbol_defs(self._settings.MARKET_DATA_SYMBOLS)

    def _is_stale(self, row: MarketDataSnapshot | None) -> bool:
        if row is None:
            return True
        max_lag = timedelta(hours=self._settings.MARKET_DATA_REFRESH_HOURS)
        return datetime.now(timezone.utc) - row.as_of.astimezone(timezone.utc) > max_lag

    def quotes(self) -> list[MarketQuote]:
        """Current quotes, refreshing missing/stale symbols; never raises."""
        repo = MarketDataRepository(self._db)
        result: list[MarketQuote] = []
        for symbol, name in self._symbols():
            row = repo.get(symbol)
            if self._is_stale(row):
                refreshed, _ = self._refresh_symbol(repo, symbol, name)
                if refreshed is not None:
                    row = refreshed
            if row is None:
                result.append(
                    MarketQuote(
                        symbol=symbol,
                        name=name,
                        price=0.0,
                        change_pct=None,
                        as_of=datetime.now(timezone.utc),
                        source=SOURCE_NAME,
                        status="unavailable",
                    )
                )
                continue
            result.append(
                MarketQuote(
                    symbol=row.symbol,
                    name=row.name,
                    price=row.price,
                    change_pct=row.change_pct,
                    as_of=row.as_of.astimezone(timezone.utc),
                    source=row.source,
                    status="live" if not self._is_stale(row) else "cached",
                )
            )
        return result

    def force_refresh(self) -> list[MarketQuote]:
        """Refresh every configured symbol now (manual/periodic call)."""
        repo = MarketDataRepository(self._db)
        for symbol, name in self._symbols():
            self._refresh_symbol(repo, symbol, name)
        return self._quotes_after_refresh()

    def _quotes_after_refresh(self) -> list[MarketQuote]:
        return self.quotes()

    def _refresh_symbol(
        self,
        repo: MarketDataRepository,
        symbol: str,
        name: str,
    ) -> tuple[MarketDataSnapshot | None, bool]:
        try:
            payload = self._fetcher(
                symbol,
                url_template=self._settings.MARKET_DATA_URL_TEMPLATE,
                timeout_seconds=self._settings.MARKET_DATA_TIMEOUT_SECONDS,
            )
            price, change_pct, as_of = parse_chart_payload(payload)
        except Exception:  # noqa: BLE001 - feed failures are routine, not fatal
            logger.warning("market data fetch failed for %s", symbol)
            return None, False
        row = repo.upsert(
            MarketDataSnapshot(
                symbol=symbol,
                name=name,
                price=price,
                change_pct=change_pct,
                as_of=as_of,
                source=SOURCE_NAME,
            )
        )
        return row, True