"""Integration tests for the auth API."""
import pytest


def test_register_success(client):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "new@example.com", "full_name": "New User", "password": "StrongPass123"},
    )
    assert resp.status_code == 201
    body = resp.json()
    assert body["access_token"]
    assert body["refresh_token"]
    assert body["token_type"] == "bearer"


def test_register_duplicate_email_conflict(client, auth_headers):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "tester@example.com", "full_name": "Other", "password": "StrongPass123"},
    )
    assert resp.status_code == 409
    assert resp.json()["error"]["code"] == "email_already_registered"


def test_register_weak_password_rejected(client):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "weak@example.com", "full_name": "Weak", "password": "short"},
    )
    assert resp.status_code == 422


def test_login_success(client):
    client.post(
        "/api/v1/auth/register",
        json={"email": "login@example.com", "full_name": "Login User", "password": "StrongPass123"},
    )
    resp = client.post(
        "/api/v1/auth/login",
        json={"email": "login@example.com", "password": "StrongPass123"},
    )
    assert resp.status_code == 200
    assert resp.json()["access_token"]


def test_login_wrong_password(client):
    client.post(
        "/api/v1/auth/register",
        json={"email": "wrong@example.com", "full_name": "Wrong", "password": "StrongPass123"},
    )
    resp = client.post(
        "/api/v1/auth/login",
        json={"email": "wrong@example.com", "password": "WrongPass123"},
    )
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "invalid_credentials"


def test_login_unknown_email(client):
    resp = client.post(
        "/api/v1/auth/login",
        json={"email": "ghost@example.com", "password": "StrongPass123"},
    )
    assert resp.status_code == 401


def test_refresh_rotation(client):
    reg = client.post(
        "/api/v1/auth/register",
        json={"email": "refresh@example.com", "full_name": "Refresh", "password": "StrongPass123"},
    ).json()
    resp = client.post("/api/v1/auth/refresh", json={"refresh_token": reg["refresh_token"]})
    assert resp.status_code == 200
    assert resp.json()["access_token"]


def test_refresh_with_access_token_rejected(client):
    reg = client.post(
        "/api/v1/auth/register",
        json={"email": "ref2@example.com", "full_name": "Ref", "password": "StrongPass123"},
    ).json()
    resp = client.post("/api/v1/auth/refresh", json={"refresh_token": reg["access_token"]})
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "invalid_refresh_token"


def test_refresh_garbage_token(client):
    resp = client.post("/api/v1/auth/refresh", json={"refresh_token": "garbage.token.here"})
    assert resp.status_code == 401


def test_me_with_valid_token(client, auth_headers):
    resp = client.get("/api/v1/auth/me", headers=auth_headers)
    assert resp.status_code == 200
    body = resp.json()
    assert body["email"] == "tester@example.com"
    assert body["full_name"] == "Test User"


def test_me_without_token(client):
    resp = client.get("/api/v1/auth/me")
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "missing_token"


def test_me_with_garbage_token(client):
    resp = client.get("/api/v1/auth/me", headers={"Authorization": "Bearer garbage.token.here"})
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "invalid_token"


@pytest.mark.parametrize(
    "payload",
    [
        {"email": "bad-email", "full_name": "X", "password": "StrongPass123"},
        {"email": "ok@example.com", "full_name": "X", "password": "strongpass123"},
        {"email": "ok@example.com", "full_name": "X", "password": "STRONGPASS123"},
        {"email": "ok@example.com", "full_name": "X", "password": "StrongPass"},
        {"email": "ok@example.com", "full_name": "X"},
    ],
)
def test_register_validation_errors(client, payload):
    resp = client.post("/api/v1/auth/register", json=payload)
    assert resp.status_code == 422
