"""Integration tests for the budgets endpoints."""
from datetime import date

from app.domain.entities import ParseStatus, TransactionType
from app.models.statement import Statement
from app.models.transaction import Transaction


def _register_user(client) -> tuple[dict[str, str], int]:
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "budget@example.com", "full_name": "Budget User", "password": "StrongPass123"},
    )
    assert resp.status_code == 201, resp.text
    headers = {"Authorization": f"Bearer {resp.json()['access_token']}"}
    me = client.get("/api/v1/auth/me", headers=headers).json()
    return headers, me["id"]


def _insert_current_month_transactions(db_session, user_id: int, rows: list[tuple[int, float, str, str]]):
    """Insert rows as (day, amount, description, category) in the current month."""
    today = date.today()
    statement = Statement(
        user_id=user_id,
        filename="budget.csv",
        file_type="csv",
        parse_status=ParseStatus.PROCESSED,
        transaction_count=len(rows),
    )
    db_session.add(statement)
    db_session.flush()
    for day, amount, description, category in rows:
        db_session.add(
            Transaction(
                statement_id=statement.id,
                user_id=user_id,
                date=today.replace(day=min(day, 28)),
                description=description,
                amount=amount,
                transaction_type=TransactionType("debit"),
                category=category,
            )
        )
    db_session.commit()


def test_set_and_list_budget_with_spending(client, db_session):
    headers, user_id = _register_user(client)
    _insert_current_month_transactions(
        db_session, user_id, [(2, -6_000, "SUPERMART", "Groceries"), (10, -4_000, "FARM FRESH", "Groceries")]
    )
    resp = client.put("/api/v1/budgets/Groceries", headers=headers, json={"monthly_limit": 8_000})
    assert resp.status_code == 200
    body = resp.json()
    assert body["category"] == "Groceries"
    assert body["monthly_limit"] == 8_000
    assert body["spent"] == 10_000
    assert body["percentage"] == 125.0
    assert body["overrun"] is True

    resp = client.get("/api/v1/budgets", headers=headers)
    items = resp.json()["items"]
    assert len(items) == 1
    assert items[0]["overrun"] is True


def test_set_budget_within_limit_is_not_overrun(client, db_session):
    headers, user_id = _register_user(client)
    _insert_current_month_transactions(db_session, user_id, [(2, -6_000, "SUPERMART", "Groceries")])
    resp = client.put("/api/v1/budgets/Groceries", headers=headers, json={"monthly_limit": 10_000})
    body = resp.json()
    assert body["overrun"] is False
    assert body["percentage"] == 60.0


def test_set_budget_invalid_category(client, auth_headers):
    resp = client.put("/api/v1/budgets/Bogus", headers=auth_headers, json={"monthly_limit": 5_000})
    assert resp.status_code == 422
    assert resp.json()["error"]["code"] == "invalid_category"


def test_set_budget_invalid_limit(client, auth_headers):
    resp = client.put("/api/v1/budgets/Rent", headers=auth_headers, json={"monthly_limit": 0})
    assert resp.status_code == 422


def test_update_budget_replaces_limit(client, auth_headers):
    client.put("/api/v1/budgets/Rent", headers=auth_headers, json={"monthly_limit": 20_000})
    resp = client.put("/api/v1/budgets/Rent", headers=auth_headers, json={"monthly_limit": 25_000})
    assert resp.status_code == 200
    assert resp.json()["monthly_limit"] == 25_000
    items = client.get("/api/v1/budgets", headers=auth_headers).json()["items"]
    assert len(items) == 1


def test_delete_budget(client, auth_headers):
    client.put("/api/v1/budgets/Rent", headers=auth_headers, json={"monthly_limit": 20_000})
    resp = client.delete("/api/v1/budgets/Rent", headers=auth_headers)
    assert resp.status_code == 204
    assert client.get("/api/v1/budgets", headers=auth_headers).json()["total"] == 0


def test_delete_missing_budget_404(client, auth_headers):
    resp = client.delete("/api/v1/budgets/Rent", headers=auth_headers)
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "budget_not_found"
