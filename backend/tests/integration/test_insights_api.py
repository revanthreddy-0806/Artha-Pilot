"""Integration tests for the insights endpoints."""
from datetime import date

from app.domain.entities import ParseStatus, TransactionType
from app.models.statement import Statement
from app.models.transaction import Transaction


def _register_user(client) -> tuple[dict[str, str], int]:
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "insights@example.com", "full_name": "Insight User", "password": "StrongPass123"},
    )
    assert resp.status_code == 201, resp.text
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = client.get("/api/v1/auth/me", headers=headers).json()
    return headers, me["id"]


def _insert_transactions(db_session, user_id: int, rows: list[tuple[int, int, float, str, str, str]]):
    """Insert rows as (day, month, amount, description, type, category) in 2026."""
    statement = Statement(
        user_id=user_id,
        filename="insights.csv",
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


def test_summary_computes_monthly_totals_and_top_categories(client, db_session):
    headers, user_id = _register_user(client)
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 7, 100_000, "ACME SALARY", "credit", "Salary"),
            (1, 7, -25_000, "RENT PAYMENT", "debit", "Rent"),
            (2, 7, -12_000, "SUPERMART GROCERIES", "debit", "Groceries"),
            (3, 7, -8_000, "UBER RIDES", "debit", "Transport"),
            (4, 7, -5_000, "ZOMATO", "debit", "Food & Dining"),
        ],
    )
    resp = client.get("/api/v1/insights/summary?month=2026-07", headers=headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["month"] == "2026-07"
    assert body["income"] == 100_000
    assert body["expenses"] == 50_000
    assert body["savings"] == 50_000
    assert body["savings_rate"] == 0.5
    assert body["transaction_count"] == 5
    assert body["top_categories"][0]["category"] == "Rent"
    assert body["top_categories"][0]["percentage"] == 50.0


def test_summary_invalid_month_rejected(client, auth_headers):
    resp = client.get("/api/v1/insights/summary?month=2026-13", headers=auth_headers)
    assert resp.status_code == 422


def test_summary_requires_auth(client):
    resp = client.get("/api/v1/insights/summary")
    assert resp.status_code == 401


def test_monthly_trends_covers_requested_months(client, db_session):
    headers, user_id = _register_user(client)
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 5, 90_000, "ACME SALARY", "credit", "Salary"),
            (2, 5, -30_000, "RENT PAYMENT", "debit", "Rent"),
            (1, 6, 95_000, "ACME SALARY", "credit", "Salary"),
            (2, 6, -30_000, "RENT PAYMENT", "debit", "Rent"),
            (1, 7, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, 7, -30_000, "RENT PAYMENT", "debit", "Rent"),
        ],
    )
    resp = client.get("/api/v1/insights/monthly-trends?months=4", headers=headers)
    assert resp.status_code == 200
    body = resp.json()
    assert len(body["months"]) == 4
    labels = [m["month"] for m in body["months"]]
    assert labels == sorted(labels)
    may = next(m for m in body["months"] if m["month"] == "2026-05")
    assert may["income"] == 90_000
    assert may["expenses"] == 30_000
    assert may["savings"] == 60_000


def test_category_breakdown_sorted_with_counts(client, db_session):
    headers, user_id = _register_user(client)
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 7, -25_000, "RENT PAYMENT", "debit", "Rent"),
            (2, 7, -10_000, "SUPERMART", "debit", "Groceries"),
            (3, 7, -10_000, "FARM FRESH", "debit", "Groceries"),
            (4, 7, -5_000, "UBER", "debit", "Transport"),
        ],
    )
    resp = client.get("/api/v1/insights/category-breakdown?month=2026-07", headers=headers)
    body = resp.json()
    assert [c["category"] for c in body["categories"]] == ["Rent", "Groceries", "Transport"]
    rent = body["categories"][0]
    assert rent["amount"] == 25_000
    assert rent["percentage"] == 50.0
    groceries = body["categories"][1]
    assert groceries["amount"] == 20_000
    assert groceries["transaction_count"] == 2
    total = sum(c["percentage"] for c in body["categories"])
    assert round(total) == 100


def test_recurring_detection_endpoint(client, db_session):
    headers, user_id = _register_user(client)
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 5, 100_000, "ACME SALARY", "credit", "Salary"),
            (5, 5, -25_000, "RENT PAYMENT", "debit", "Rent"),
            (5, 6, -25_000, "RENT PAYMENT", "debit", "Rent"),
            (5, 7, -25_000, "RENT PAYMENT", "debit", "Rent"),
            (2, 6, -12_500, "SBI CAR LOAN EMI", "debit", "Bills & Recurring"),
            (2, 7, -12_500, "SBI CAR LOAN EMI", "debit", "Bills & Recurring"),
        ],
    )
    resp = client.get("/api/v1/insights/recurring", headers=headers)
    assert resp.status_code == 200
    items = resp.json()["items"]
    by_description = {item["description"]: item for item in items}
    assert "RENT PAYMENT" in by_description
    assert by_description["RENT PAYMENT"]["kind"] == "recurring"
    assert by_description["RENT PAYMENT"]["occurrences"] == 3
    assert "SBI CAR LOAN EMI" in by_description
    assert by_description["SBI CAR LOAN EMI"]["kind"] == "emi"
    assert by_description["SBI CAR LOAN EMI"]["interval_days"] == 30


def test_risk_profile_healthy_user(client, db_session):
    headers, user_id = _register_user(client)
    _insert_transactions(
        db_session,
        user_id,
        [
            (1, 7, 100_000, "ACME SALARY", "credit", "Salary"),
            (2, 7, -25_000, "RENT PAYMENT", "debit", "Rent"),
            (3, 7, -15_000, "GROCERIES", "debit", "Groceries"),
        ],
    )
    resp = client.get("/api/v1/insights/risk-profile", headers=headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["score"] >= 60
    assert body["band"] in {"Good", "Excellent"}
    assert body["income"] == 100_000
    assert body["monthly_expenses"] == 40_000
    assert body["savings_rate"] == 0.6
    assert body["safe_emi_amount"] == 30_000
    assert body["max_loan_amount"] is not None
    assert len(body["factors"]) >= 3


def test_risk_profile_empty_user(client, auth_headers):
    resp = client.get("/api/v1/insights/risk-profile", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["score"] == 0
    assert body["band"] == "Poor"
    assert body["income"] is None
