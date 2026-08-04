"""Integration tests for the auth API."""
import pytest

from app.core.errors import AppError
from app.core.security import create_access_token
from app.services.auth_service import AuthService


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


def test_refresh_token_reuse_rejected_after_rotation(client):
    reg = client.post(
        "/api/v1/auth/register",
        json={"email": "reuse@example.com", "full_name": "Reuse", "password": "StrongPass123"},
    ).json()
    old_token = reg["refresh_token"]
    first = client.post("/api/v1/auth/refresh", json={"refresh_token": old_token})
    assert first.status_code == 200
    second = client.post("/api/v1/auth/refresh", json={"refresh_token": old_token})
    assert second.status_code == 401
    assert second.json()["error"]["code"] == "invalid_refresh_token"


def test_refresh_with_rotated_token_still_works(client):
    reg = client.post(
        "/api/v1/auth/register",
        json={"email": "rotate@example.com", "full_name": "Rotate", "password": "StrongPass123"},
    ).json()
    first = client.post("/api/v1/auth/refresh", json={"refresh_token": reg["refresh_token"]}).json()
    second = client.post("/api/v1/auth/refresh", json={"refresh_token": first["refresh_token"]})
    assert second.status_code == 200


def test_me_with_non_numeric_sub_rejected(client, db_session):
    from app.repositories.user_repository import UserRepository

    user = UserRepository(db_session).create("subtest@example.com", "Sub Test", "hash")
    token = create_access_token(f"user-{user.id}")
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["code"] == "invalid_token"


def test_unknown_route_uses_error_contract(client):
    resp = client.get("/api/v1/does-not-exist")
    assert resp.status_code == 404
    assert resp.json()["error"]["code"] == "not_found"


def test_wrong_method_uses_error_contract(client):
    resp = client.post("/api/v1/health")
    assert resp.status_code in (404, 405)
    assert "error" in resp.json()


def test_register_race_returns_conflict(client, db_session, monkeypatch):
    from app.repositories.user_repository import UserRepository

    UserRepository(db_session).create("race@example.com", "Race", "hash")
    monkeypatch.setattr(UserRepository, "get_by_email", lambda self, email: None)
    service = AuthService(db_session)
    with pytest.raises(AppError) as exc_info:
        service.register("race@example.com", "Race User", "StrongPass123")
    assert exc_info.value.status_code == 409
    assert exc_info.value.error_code == "email_already_registered"


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
