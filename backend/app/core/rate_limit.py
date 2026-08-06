"""Lightweight in-memory rate limiting for authenticated endpoints.

Each dependency instance tracks request timestamps per client key (client IP,
honoring X-Forwarded-For when present) and rejects requests over a per-minute
quota.  The limiter is intentionally single-process; multi-worker deployments
should move this to Redis or a similar shared store (documented in
RELEASE_READINESS_REPORT).
"""
from __future__ import annotations

import threading
import time
from collections import deque
from typing import Callable

from fastapi import Request

from app.core.config import get_settings
from app.core.errors import AppError

DEFAULT_WINDOW_SECONDS = 60


class InMemoryRateLimiter:
    """Fixed-window rate limit keyed by client identifier."""

    def __init__(self, limit: int, window_seconds: int = DEFAULT_WINDOW_SECONDS) -> None:
        self.limit = limit
        self.window_seconds = window_seconds
        self._hits: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        with self._lock:
            window = self._hits.setdefault(key, deque())
            while window and now - window[0] > self.window_seconds:
                window.popleft()
            if len(window) >= self.limit:
                return False
            window.append(now)
            return True

    def reset(self) -> None:
        with self._lock:
            self._hits.clear()


def client_key(request: Request) -> str:
    """Best-effort client identifier, trusting X-Forwarded-For when present."""
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    if request.client is not None:
        return request.client.host
    return "unknown"


def rate_limit_dependency(limit: int, window_seconds: int = DEFAULT_WINDOW_SECONDS) -> Callable[..., None]:
    """Build a FastAPI dependency enforcing limit requests per window per client.

    The limiter is a no-op when running under the test suite so existing and
    new tests are not throttled.
    """
    limiter = InMemoryRateLimiter(limit, window_seconds)

    def dependency(request: Request) -> None:
        if get_settings().ENVIRONMENT == "test":
            return
        if not limiter.allow(client_key(request)):
            raise AppError(
                "Too many requests. Please try again later",
                status_code=429,
                error_code="rate_limited",
            )

    return dependency