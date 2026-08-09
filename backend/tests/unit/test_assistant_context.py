"""Unit tests for the grounded context builder (no network)."""
from datetime import date

import pytest

from app.domain.entities import ParseStatus, TransactionType
from app.models.statement import Statement
from app.models.transaction import Transaction
from app.services.assistant_context import (
    _find_amount,
    _resolve_what_if_category,
    build_grounded_context,
)


@pytest.fixture(autouse=True)
def _no_network_market_data(monkeypatch):
    """Market quotes would try the network; stub them to unavailable."""

    class FakeMarketDataService:
        def __init__(self, db, settings=None, fetcher=None):
            pass

        def quotes(self):
            return []

    import app.services.assistant_context as ctx

    monkeypatch.setattr(ctx, "MarketDataService", FakeMarketDataService)


def _register_user(client, email: str) -> int:
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": email, "full_name": "Ctx User", "password": "StrongPass123"},
    )
    assert resp.status_code == 201, resp.text
    me = client.get(
        "/api/v1/auth/me",
        headers={"Authorization": f"Bearer {resp.json()['access_token']}"},
    ).json()
    return me["id"]


def _insert_transactions(db_session, user_id: int, rows):
    statement = Statement(
        user_id=user_id,
        filename="ctx.csv",
        file_type="csv",
        parse_status=ParseStatus.PROCESSED,
        transaction_count=len(rows),
    )
    db_session.add(statement)
    db_session.flush()
    today = date.today()
    for day, month, amount, description, txn_type, category in rows:
        year = today.year if month <= today.month else today.year - 1
        db_session.add(
            Transaction(
                statement_id=statement.id,
                user_id=user_id,
                date=date(year, month, day),
                description=description,
                amount=amount,
                transaction_type=TransactionType(txn_type),
                category=category,
            )
        )
    db_session.commit()


def test_resolve_what_if_category_prefers_users_own_label():
    assert _resolve_what_if_category("cut dining by 5000", ["Dining", "Rent"]) == "Dining"
    assert (
        _resolve_what_if_category("reduce food spending", ["Food & Dining", "Rent"])
        == "Food & Dining"
    )
    assert _resolve_what_if_category("what about rent", ["Dining", "Rent"]) == "Rent"
    assert _resolve_what_if_category("gym membership", ["Dining", "Rent"]) is None


def test_find_amount_parsing():
    assert _find_amount("reduce dining by 5,000") == 5000
    assert _find_amount("what if I cut food by 15000 rupees") == 15000
    assert _find_amount("I want to save 3000 inr monthly") == 3000
    assert _find_amount("no numbers here") is None
    assert _find_amount("tiny 50 rupees") is None


def test_build_grounded_context_with_transactions(client, db_session):
    user_id = _register_user(client, "ctx@example.com")
    today = date.today()
    prev = today.replace(day=1)
    if prev.month == 1:
        prev = prev.replace(year=prev.year - 1, month=12)
    else:
        prev = prev.replace(month=prev.month - 1)
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, today.month, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, today.month, -30_000, "RENT", "debit", "Rent"),
            (5, today.month, -12_000, "ZOMATO", "debit", "Dining"),
            (1, prev.month, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, prev.month, -30_000, "RENT", "debit", "Rent"),
            (5, prev.month, -12_000, "ZOMATO", "debit", "Dining"),
        ],
    )

    context = build_grounded_context(db_session, user_id, "cut dining by 5000")

    assert context["profile"]["transaction_count"] == 3
    assert context["profile"]["current_month_income"] == 100_000
    assert context["profile"]["current_month_expenses"] == 42_000
    assert context["profile"]["current_month_savings"] == 58_000
    assert context["profile"]["current_month_savings_rate"] == 58_000 / 100_000
    assert context["insufficient_data"] is None

    assert context["what_if"] is not None
    assert context["what_if"]["category"] == "Dining"
    assert context["what_if"]["original"]["monthly_expenses"] == 42_000
    assert context["what_if"]["simulated"]["monthly_savings"] == 63_000
    assert context["what_if"]["is_simulation"] is True

    assert context["recommendation"]["status"] == "ok"
    assert context["recommendation"]["risk_band"] in {"Conservative", "Moderate", "Aggressive"}
    assert sum(a["percentage"] for a in context["recommendation"]["allocation"]) == 100

    assert context["recurring"]["items"]


def test_build_context_empty_user_reports_insufficient(client, db_session):
    user_id = _register_user(client, "ctxempty@example.com")

    context = build_grounded_context(db_session, user_id, "how am I doing?")

    assert context["profile"]["transaction_count"] == 0
    assert context["profile"]["current_month_income"] == 0.0
    assert context["what_if"] is None
    assert context["insufficient_data"] is not None
    assert context["recommendation"]["status"] == "insufficient"