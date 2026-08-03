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


def test_health_endpoint(client):
    resp = client.get("/api/v1/health")
    assert resp.status_code == 200
    assert resp.json()["status"] in {"ok", "degraded"}
