"""Integration tests for the recommendation and what-if endpoints."""
from datetime import date

import pytest

from app.domain.entities import ParseStatus, TransactionType
from app.models.statement import Statement
from app.models.transaction import Transaction


@pytest.fixture(autouse=True)
def _no_network_market_data(monkeypatch):
    """Keep integrations hermetic: the endpoint's market lookup is stubbed."""

    class FakeMarketDataService:
        def __init__(self, db, settings=None, fetcher=None):
            pass

        def quotes(self):
            return []

    import app.api.v1.recommendations as rec_api

    monkeypatch.setattr(rec_api, "MarketDataService", FakeMarketDataService)

CSV_FIXTURE = """\
Date,Narration,Chq/Ref Number,Value Dat,Withdrawal Amt.,Deposit Amt.,Closing Balance
01/06/2026,SALARY CREDIT,1001,01/06/2026,,100000.00,200000.00
02/06/2026,RENT PAYMENT,1002,02/06/2026,30000.00,,170000.00
05/06/2026,ZOMATO,1003,05/06/2026,12000.00,,158000.00
01/07/2026,SALARY CREDIT,1006,01/07/2026,,100000.00,229000.00
02/07/2026,RENT PAYMENT,1007,02/07/2026,30000.00,,199000.00
05/07/2026,ZOMATO,1008,05/07/2026,12000.00,,187000.00
"""


def _register_user(client, email) -> tuple[dict[str, str], int]:
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": email, "full_name": "Rec User", "password": "StrongPass123"},
    )
    assert resp.status_code == 201, resp.text
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = client.get("/api/v1/auth/me", headers=headers).json()
    return headers, me["id"]


def _insert_transactions(db_session, user_id: int, rows):
    statement = Statement(
        user_id=user_id,
        filename="rec.csv",
        file_type="csv",
        parse_status=ParseStatus.PROCESSED,
        transaction_count=len(rows),
    )
    db_session.add(statement)
    db_session.flush()
    for day, month, amount, description, txn_type, category in rows:
        db_session.add(
            Transaction(
                statement_id=statement.id,
                user_id=user_id,
                date=date(2026, month, day),
                description=description,
                amount=amount,
                transaction_type=TransactionType(txn_type),
                category=category,
            )
        )
    db_session.commit()


def test_recommendations_requires_auth(client):
    resp = client.get("/api/v1/recommendations")
    assert resp.status_code == 401


def test_what_if_requires_auth(client):
    resp = client.post(
        "/api/v1/recommendations/what-if",
        json={"category": "Dining", "monthly_reduction": 1000},
    )
    assert resp.status_code == 401


def test_recommendations_insufficient_for_new_user(client, auth_headers):
    resp = client.get("/api/v1/recommendations", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "insufficient"
    assert body["allocation"] == []
    assert body["insufficient_reason"]
    assert body["risk_band"] in {"Conservative", "Moderate", "Aggressive"}


def test_recommendations_ok_with_real_statement_data(client):
    """End-to-end via uploaded CSV: bands drive the fixed mix."""
    headers, _ = _register_user(client, "recy@example.com")
    resp = client.post(
        "/api/v1/statements/upload",
        headers=headers,
        files={"file": ("statement.csv", CSV_FIXTURE.encode(), "text/csv")},
    )
    assert resp.status_code == 201, resp.text

    resp = client.get("/api/v1/recommendations", headers=headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["context"]["monthly_income"] == 100_000
    assert body["context"]["monthly_expenses"] == 42_000
    assert body["context"]["investable_monthly"] == 58_000
    assert {a["asset_class"]: a["percentage"] for a in body["allocation"]}.keys() == {
        "equity",
        "debt",
        "gold",
    }
    assert sum(a["percentage"] for a in body["allocation"]) == 100
    for item in body["allocation"]:
        assert item["rationale"]
    assert "not SEBI-registered" in body["educational_disclaimer"]


def test_recommendations_band_is_deterministic(client, db_session):
    """Same input twice yields identical output (no randomness)."""
    headers, user_id = _register_user(client, "recband@example.com")
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 7, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, 7, -30_000, "RENT", "debit", "Rent"),
        ],
    )
    first = client.get("/api/v1/recommendations", headers=headers).json()
    second = client.get("/api/v1/recommendations", headers=headers).json()
    assert first["status"] == "ok"
    assert first["allocation"] == second["allocation"]
    assert first["risk_band"] == second["risk_band"]


def test_what_if_reduces_expenses_only_in_output(client, db_session):
    headers, user_id = _register_user(client, "recwhat@example.com")
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 7, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, 7, -30_000, "RENT", "debit", "Rent"),
            (5, 7, -12_000, "ZOMATO", "debit", "Dining"),
        ],
    )
    resp = client.post(
        "/api/v1/recommendations/what-if",
        headers=headers,
        json={"category": "Dining", "monthly_reduction": 5_000},
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["is_simulation"] is True
    assert body["original"]["monthly_expenses"] == 42_000
    assert body["simulated"]["monthly_expenses"] == 37_000
    assert body["simulated"]["monthly_savings"] == body["original"]["monthly_savings"] + 5_000
    assert body["impact"]["annualised_extra_investable"] == 60_000

    profile = client.get("/api/v1/risk/profile", headers=headers).json()
    assert profile["monthly_expenses"] == 42_000  # unchanged by the simulation


def test_what_if_rejects_unknown_category(client, db_session):
    headers, _ = _register_user(client, "recbad@example.com")
    resp = client.post(
        "/api/v1/recommendations/what-if",
        headers=headers,
        json={"category": "Travel", "monthly_reduction": 1_000},
    )
    assert resp.status_code == 422
    assert resp.json()["error"]["code"] == "invalid_scenario"


def test_what_if_rejects_reduction_beyond_spend(client, db_session):
    headers, user_id = _register_user(client, "recbig@example.com")
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 7, 100_000, "SALARY", "credit", "Salary"),
            (2, 7, -10_000, "ZOMATO", "debit", "Dining"),
        ],
    )
    resp = client.post(
        "/api/v1/recommendations/what-if",
        headers=headers,
        json={"category": "Dining", "monthly_reduction": 50_000},
    )
    assert resp.status_code == 422
    assert "exceeds" in resp.json()["error"]["message"]


def test_what_if_insufficient_data_explained(client, auth_headers):
    resp = client.post(
        "/api/v1/recommendations/what-if",
        headers=auth_headers,
        json={"category": "Dining", "monthly_reduction": 1_000},
    )
    assert resp.status_code == 422
    assert "upload" in resp.json()["error"]["message"].lower()


def test_users_are_isolated(client, db_session):
    headers_a, user_a = _register_user(client, "recisola@example.com")
    headers_b, user_b = _register_user(client, "recisolb@example.com")
    _insert_transactions(
        db_session,
        user_a,
        [
            (1, 7, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, 7, -30_000, "RENT", "debit", "Rent"),
        ],
    )
    resp_a = client.get("/api/v1/recommendations", headers=headers_a)
    resp_b = client.get("/api/v1/recommendations", headers=headers_b)
    assert resp_a.json()["status"] == "ok"
    assert resp_b.json()["status"] == "insufficient"