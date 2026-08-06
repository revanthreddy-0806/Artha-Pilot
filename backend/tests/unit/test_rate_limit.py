"""Unit tests for the in-memory rate limiter and its FastAPI integration."""
import time

from fastapi import Depends, FastAPI, Request
from fastapi.testclient import TestClient

from app.core.errors import AppError, register_exception_handlers
from app.core.rate_limit import InMemoryRateLimiter, client_key, rate_limit_dependency


def test_limiter_allows_up_to_limit():
    limiter = InMemoryRateLimiter(limit=3, window_seconds=60)
    assert all(limiter.allow("client-a") for _ in range(3))
    assert limiter.allow("client-a") is False


def test_limiter_is_per_client():
    limiter = InMemoryRateLimiter(limit=2, window_seconds=60)
    assert limiter.allow("client-a")
    assert limiter.allow("client-a")
    assert limiter.allow("client-a") is False
    assert limiter.allow("client-b")
    assert limiter.allow("client-b")
    assert limiter.allow("client-b") is False


def test_limiter_window_expires():
    limiter = InMemoryRateLimiter(limit=1, window_seconds=1)
    assert limiter.allow("client-a")
    assert limiter.allow("client-a") is False
    time.sleep(1.1)
    assert limiter.allow("client-a")


def test_reset_clears_state():
    limiter = InMemoryRateLimiter(limit=1, window_seconds=60)
    assert limiter.allow("client-a")
    assert limiter.allow("client-a") is False
    limiter.reset()
    assert limiter.allow("client-a")


def _build_throttled_app(limit: int) -> FastAPI:
    limiter = InMemoryRateLimiter(limit=limit, window_seconds=60)

    def guard(request: Request) -> None:
        if not limiter.allow(client_key(request)):
            raise AppError(
                "Too many requests. Please try again later",
                status_code=429,
                error_code="rate_limited",
            )

    app = FastAPI()

    @app.get("/ping", dependencies=[Depends(guard)])
    def ping() -> dict[str, str]:
        return {"ok": "yes"}

    register_exception_handlers(app)
    return app


def test_dependency_rejects_over_limit_with_429():
    app = _build_throttled_app(limit=2)
    client = TestClient(app)
    assert client.get("/ping").status_code == 200
    assert client.get("/ping").status_code == 200
    resp = client.get("/ping")
    assert resp.status_code == 429
    body = resp.json()
    assert body["error"]["code"] == "rate_limited"
    assert "Too many requests" in body["error"]["message"]


def test_dependency_factory_returns_callable():
    dep = rate_limit_dependency(limit=5)
    assert callable(dep)
