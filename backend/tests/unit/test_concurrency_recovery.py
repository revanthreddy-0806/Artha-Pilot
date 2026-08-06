"""Tests for concurrent-write recovery paths (refresh rotation, budget upsert).

These simulate the losing side of a unique-constraint race: the check-then-act
window passes for both requests, then the second insert collides. Each test
asserts the collision is recovered (401 / convergence) instead of a 500.
"""
import pytest
from sqlalchemy.exc import IntegrityError

from app.core.errors import AppError
from app.core.security import create_refresh_token
from app.repositories.budget_repository import BudgetRepository
from app.repositories.user_repository import UserRepository
from app.services.auth_service import AuthService


def test_refresh_race_on_revoke_returns_401_not_500(db_session, monkeypatch):
    user = UserRepository(db_session).create("race@example.com", "Race User", "hash")
    token = create_refresh_token(str(user.id))
    service = AuthService(db_session)

    monkeypatch.setattr(service._revoked, "is_revoked", lambda jti: False)

    def boom(jti, user_id, expires_at):
        raise IntegrityError("duplicate key value violates unique constraint", None, None)

    monkeypatch.setattr(service._revoked, "revoke", boom)

    with pytest.raises(AppError) as exc_info:
        service.refresh(token)
    assert exc_info.value.status_code == 401
    assert exc_info.value.error_code == "invalid_refresh_token"


def test_refresh_sequential_reuse_is_rejected(client):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "reuse@example.com", "full_name": "Reuse User", "password": "StrongPass123"},
    )
    refresh = resp.json()["refresh_token"]
    first = client.post("/api/v1/auth/refresh", json={"refresh_token": refresh})
    assert first.status_code == 200
    second = client.post("/api/v1/auth/refresh", json={"refresh_token": refresh})
    assert second.status_code == 401
    assert second.json()["error"]["code"] == "invalid_refresh_token"


def test_budget_upsert_race_converges(db_session, monkeypatch):
    repo = BudgetRepository(db_session)
    existing = repo.upsert(1, "Rent", 1000.0)

    real_get = repo.get
    calls = {"n": 0}

    def fake_get(user_id, category):
        calls["n"] += 1
        if calls["n"] == 1:
            return None  # first check loses the read race
        return real_get(user_id, category)

    monkeypatch.setattr(repo, "get", fake_get)

    result = repo.upsert(1, "Rent", 2500.0)
    assert result.id == existing.id
    assert result.category == "Rent"
    assert repo.get(1, "Rent").monthly_limit == 1000.0
    assert len(repo.list_for_user(1)) == 1
