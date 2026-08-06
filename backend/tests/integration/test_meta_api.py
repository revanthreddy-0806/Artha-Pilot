"""Integration tests for meta endpoints (category taxonomy)."""


def test_list_categories_requires_auth(client):
    resp = client.get("/api/v1/categories")
    assert resp.status_code == 401


def test_list_categories_returns_taxonomy(client, auth_headers):
    resp = client.get("/api/v1/categories", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert "categories" in body
    categories = body["categories"]
    assert "Food & Dining" in categories
    assert "Uncategorized" in categories
    assert len(categories) == 18
    assert len(categories) == len(set(categories))