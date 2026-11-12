"""Integration tests for the transactions API."""
def _upload_csv(client, headers, sample_csv_bytes):
    resp = client.post(
        "/api/v1/statements/upload",
        headers=headers,
        files={"file": ("statement.csv", sample_csv_bytes, "text/csv")},
    )
    assert resp.status_code == 201
    return resp.json()


def test_list_transactions_paginated(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    resp = client.get("/api/v1/transactions", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] == 7
    assert len(body["items"]) == 7
    assert body["page"] == 1


def test_list_transactions_page_size(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    resp = client.get("/api/v1/transactions?page=2&page_size=3", headers=auth_headers)
    body = resp.json()
    assert body["total"] == 7
    assert len(body["items"]) == 3
    assert body["page"] == 2


def test_list_transactions_filter_by_category(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    resp = client.get("/api/v1/transactions?category=Salary", headers=auth_headers)
    body = resp.json()
    assert body["total"] == 1
    assert body["items"][0]["category"] == "Salary"


def test_list_transactions_filter_by_type(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    resp = client.get("/api/v1/transactions?transaction_type=credit", headers=auth_headers)
    body = resp.json()
    assert body["total"] == 1
    assert all(t["transaction_type"] == "credit" for t in body["items"])


def test_list_transactions_invalid_type(client, auth_headers):
    resp = client.get("/api/v1/transactions?transaction_type=bogus", headers=auth_headers)
    assert resp.status_code == 422


def test_list_transactions_date_range(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    resp = client.get("/api/v1/transactions?start_date=2025-04-05&end_date=2025-04-06", headers=auth_headers)
    body = resp.json()
    assert body["total"] == 2


def test_update_category_success(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]
    resp = client.patch(f"/api/v1/transactions/{txn_id}/category", headers=auth_headers, json={"category": "Shopping"})
    assert resp.status_code == 200
    assert resp.json()["category"] == "Shopping"


def test_update_category_invalid(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]
    resp = client.patch(f"/api/v1/transactions/{txn_id}/category", headers=auth_headers, json={"category": "Bogus"})
    assert resp.status_code == 422
    assert resp.json()["error"]["code"] == "invalid_category"


def test_update_category_not_found(client, auth_headers):
    resp = client.patch("/api/v1/transactions/99999/category", headers=auth_headers, json={"category": "Shopping"})
    assert resp.status_code == 404


def test_data_isolation_between_users(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]

    other = client.post(
        "/api/v1/auth/register",
        json={"email": "other@example.com", "full_name": "Other", "password": "StrongPass123"},
    )
    other_headers = {"Authorization": f"Bearer {other.json()['access_token']}"}

    resp = client.patch(f"/api/v1/transactions/{txn_id}/category", headers=other_headers, json={"category": "Rent"})
    assert resp.status_code == 404

    resp = client.get("/api/v1/transactions", headers=other_headers)
    assert resp.json()["total"] == 0


def test_create_transaction_success(client, auth_headers):
    payload = {
        "transaction_type": "debit",
        "amount": 125.50,
        "date": "2026-01-02",
        "description": "Manual grocery purchase",
        "category": "Groceries",
        "balance": 1000.0,
    }
    resp = client.post("/api/v1/transactions", headers=auth_headers, json=payload)
    assert resp.status_code == 201, resp.text
    body = resp.json()
    assert body["statement_id"] is None
    assert body["transaction_type"] == "debit"
    assert body["amount"] == 125.50
    assert body["description"] == "Manual grocery purchase"
    assert body["category"] == "Groceries"
    assert body["balance"] == 1000.0
    assert body["is_recurring"] is False

    listed = client.get("/api/v1/transactions", headers=auth_headers).json()
    assert listed["total"] == 1
    assert listed["items"][0]["id"] == body["id"]


def test_create_transaction_validation(client, auth_headers):
    bad_amount = {
        "transaction_type": "debit",
        "amount": 0,
        "date": "2026-01-02",
        "description": "Bad",
        "category": "Groceries",
    }
    resp = client.post("/api/v1/transactions", headers=auth_headers, json=bad_amount)
    assert resp.status_code == 422

    bad_type = {
        "transaction_type": "bogus",
        "amount": 10,
        "date": "2026-01-02",
        "description": "Bad",
        "category": "Groceries",
    }
    resp = client.post("/api/v1/transactions", headers=auth_headers, json=bad_type)
    assert resp.status_code == 422

    bad_category = {
        "transaction_type": "debit",
        "amount": 10,
        "date": "2026-01-02",
        "description": "Bad",
        "category": "NotACategory",
    }
    resp = client.post("/api/v1/transactions", headers=auth_headers, json=bad_category)
    assert resp.status_code == 422


def test_update_transaction_success(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]

    payload = {
        "transaction_type": "credit",
        "amount": 999.0,
        "date": "2025-04-01",
        "description": "Updated description",
        "category": "Shopping",
        "balance": 500.0,
    }
    resp = client.patch(f"/api/v1/transactions/{txn_id}", headers=auth_headers, json=payload)
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["transaction_type"] == "credit"
    assert body["amount"] == 999.0
    assert body["description"] == "Updated description"
    assert body["category"] == "Shopping"
    assert body["balance"] == 500.0
    assert body["statement_id"] == uploaded["transactions"][0]["statement_id"]


def test_update_transaction_partial_preserves_fields(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]
    original = uploaded["transactions"][0]

    resp = client.patch(
        f"/api/v1/transactions/{txn_id}",
        headers=auth_headers,
        json={"category": "Travel"},
    )
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["category"] == "Travel"
    assert body["amount"] == original["amount"]
    assert body["description"] == original["description"]
    assert body["transaction_type"] == original["transaction_type"]


def test_update_transaction_not_found(client, auth_headers):
    resp = client.patch(
        "/api/v1/transactions/99999",
        headers=auth_headers,
        json={"category": "Shopping"},
    )
    assert resp.status_code == 404


def test_update_transaction_validation(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]

    resp = client.patch(
        f"/api/v1/transactions/{txn_id}",
        headers=auth_headers,
        json={"amount": 0},
    )
    assert resp.status_code == 422

    resp = client.patch(
        f"/api/v1/transactions/{txn_id}",
        headers=auth_headers,
        json={"category": "Bogus"},
    )
    assert resp.status_code == 422

    resp = client.patch(
        f"/api/v1/transactions/{txn_id}",
        headers=auth_headers,
        json={"transaction_type": "bogus"},
    )
    assert resp.status_code == 422


def test_delete_transaction_success(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]

    resp = client.delete(f"/api/v1/transactions/{txn_id}", headers=auth_headers)
    assert resp.status_code == 204

    listed = client.get("/api/v1/transactions", headers=auth_headers).json()
    assert listed["total"] == 6
    assert all(item["id"] != txn_id for item in listed["items"])

    resp = client.delete(f"/api/v1/transactions/{txn_id}", headers=auth_headers)
    assert resp.status_code == 404


def test_transaction_mutation_ownership(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    txn_id = uploaded["transactions"][0]["id"]

    other = client.post(
        "/api/v1/auth/register",
        json={"email": "other2@example.com", "full_name": "Other", "password": "StrongPass123"},
    )
    other_headers = {"Authorization": f"Bearer {other.json()['access_token']}"}

    resp = client.patch(
        f"/api/v1/transactions/{txn_id}",
        headers=other_headers,
        json={"category": "Rent"},
    )
    assert resp.status_code == 404

    resp = client.delete(f"/api/v1/transactions/{txn_id}", headers=other_headers)
    assert resp.status_code == 404

    created = client.post(
        "/api/v1/transactions",
        headers=auth_headers,
        json={
            "transaction_type": "credit",
            "amount": 50.0,
            "date": "2026-01-01",
            "description": "Manual",
            "category": "Salary",
        },
    ).json()
    resp = client.patch(
        f"/api/v1/transactions/{created['id']}",
        headers=other_headers,
        json={"category": "Rent"},
    )
    assert resp.status_code == 404

    resp = client.delete(f"/api/v1/transactions/{created['id']}", headers=other_headers)
    assert resp.status_code == 404


def test_search_description_case_insensitive(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    resp = client.get("/api/v1/transactions?q=zomato", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] >= 1
    assert all("zomato" in item["description"].lower() for item in body["items"])

    resp = client.get("/api/v1/transactions?q=ZOMATO", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == body["total"]

    resp = client.get("/api/v1/transactions?q=", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 7


def test_combined_filters_and_sort(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    resp = client.get(
        "/api/v1/transactions?transaction_type=debit&category=Food%20%26%20Dining&min_amount=100&max_amount=1000&sort_by=amount&sort_dir=asc",
        headers=auth_headers,
    )
    assert resp.status_code == 200
    body = resp.json()
    amounts = [item["amount"] for item in body["items"]]
    assert amounts == sorted(amounts)


def test_amount_range_validation(client, auth_headers):
    resp = client.get("/api/v1/transactions?min_amount=100&max_amount=50", headers=auth_headers)
    assert resp.status_code == 422

    resp = client.get("/api/v1/transactions?sort_by=not_a_field", headers=auth_headers)
    assert resp.status_code == 422

    resp = client.get("/api/v1/transactions?sort_dir=sideways", headers=auth_headers)
    assert resp.status_code == 422


def test_date_range_validation(client, auth_headers):
    resp = client.get(
        "/api/v1/transactions?start_date=2025-04-06&end_date=2025-04-05",
        headers=auth_headers,
    )
    assert resp.status_code == 422


def test_search_user_isolation(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)

    other = client.post(
        "/api/v1/auth/register",
        json={"email": "search_other@example.com", "full_name": "Other", "password": "StrongPass123"},
    )
    other_headers = {"Authorization": f"Bearer {other.json()['access_token']}"}

    resp = client.get("/api/v1/transactions?q=zomato", headers=other_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 0


def test_search_and_filters_boundaries(client, auth_headers, sample_csv_bytes):
    _upload_csv(client, auth_headers, sample_csv_bytes)
    manual = client.post(
        "/api/v1/transactions",
        headers=auth_headers,
        json={
            "transaction_type": "debit",
            "amount": 500.0,
            "date": "2025-04-05",
            "description": "Manual boundary transaction",
            "category": "Groceries",
        },
    ).json()

    # description search finds both imported and manual records
    resp = client.get("/api/v1/transactions?q=boundary", headers=auth_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 1

    # amount boundary values are inclusive
    resp = client.get("/api/v1/transactions?min_amount=500&max_amount=500", headers=auth_headers)
    assert resp.status_code == 200
    assert all(item["amount"] == 500.0 for item in resp.json()["items"])

    # date boundary values are inclusive
    resp = client.get(
        "/api/v1/transactions?start_date=2025-04-05&end_date=2025-04-05",
        headers=auth_headers,
    )
    assert resp.status_code == 200
    assert all(item["date"] == "2025-04-05" for item in resp.json()["items"])

    # deterministic tie-break ordering for equal amounts
    resp = client.get("/api/v1/transactions?sort_by=amount&sort_dir=asc&page_size=100", headers=auth_headers)
    assert resp.status_code == 200
    amounts = [item["amount"] for item in resp.json()["items"]]
    assert amounts == sorted(amounts)

    # all allowed sort fields/directions are accepted
    for field in ("date", "amount", "description", "category", "created_at"):
        for direction in ("asc", "desc"):
            resp = client.get(
                f"/api/v1/transactions?sort_by={field}&sort_dir={direction}&page=1&page_size=1",
                headers=auth_headers,
            )
            assert resp.status_code == 200, (field, direction, resp.text)

    # pagination remains stable with filters
    page1 = client.get("/api/v1/transactions?page=1&page_size=2&sort_by=date&sort_dir=asc", headers=auth_headers).json()
    page2 = client.get("/api/v1/transactions?page=2&page_size=2&sort_by=date&sort_dir=asc", headers=auth_headers).json()
    ids1 = [item["id"] for item in page1["items"]]
    ids2 = [item["id"] for item in page2["items"]]
    assert set(ids1).isdisjoint(ids2)


def test_health_endpoint(client):
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    assert resp.json()["status"] in {"ok", "degraded"}

def test_list_transactions_filter_by_statement_id(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    statement_id = uploaded["id"]

    client.post(
        "/api/v1/transactions",
        headers=auth_headers,
        json={
            "transaction_type": "debit",
            "amount": 10.0,
            "date": "2026-01-03",
            "description": "Manual transaction",
            "category": "Groceries",
        },
    )

    resp = client.get(f"/api/v1/transactions?statement_id={statement_id}", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["total"] == 7
    assert all(item["statement_id"] == statement_id for item in body["items"])


def test_list_transactions_statement_id_invalid(client, auth_headers):
    resp = client.get("/api/v1/transactions?statement_id=0", headers=auth_headers)
    assert resp.status_code == 422


def test_list_transactions_statement_id_other_user(client, auth_headers, sample_csv_bytes):
    uploaded = _upload_csv(client, auth_headers, sample_csv_bytes)
    statement_id = uploaded["id"]

    other = client.post(
        "/api/v1/auth/register",
        json={"email": "statement-other@example.com", "full_name": "Other", "password": "StrongPass123"},
    )
    other_headers = {"Authorization": f"Bearer {other.json()['access_token']}"}

    resp = client.get(f"/api/v1/transactions?statement_id={statement_id}", headers=other_headers)
    assert resp.status_code == 200
    assert resp.json()["total"] == 0
