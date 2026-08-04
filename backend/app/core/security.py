"""Security primitives: bcrypt password hashing and JWT token handling."""
import logging
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import bcrypt
import jwt
from jwt import PyJWTError

from app.core.config import get_settings

logger = logging.getLogger(__name__)

settings = get_settings()


def hash_password(plain: str) -> str:
    """Hash a plaintext password with bcrypt (cost factor 12)."""
    salt = bcrypt.gensalt(rounds=12)
    return bcrypt.hashpw(plain.encode("utf-8"), salt).decode("utf-8")


def verify_password(plain: str, hashed: str) -> bool:
    """Verify a plaintext password against a bcrypt hash."""
    try:
        return bcrypt.checkpw(plain.encode("utf-8"), hashed.encode("utf-8"))
    except ValueError:
        logger.warning("Password verification failed: malformed hash")
        return False


def _create_token(subject: str, token_type: str, expires_delta: timedelta, jti: str | None = None) -> str:
    now = datetime.now(timezone.utc)
    payload: dict[str, Any] = {
        "sub": subject,
        "type": token_type,
        "iat": now,
        "exp": now + expires_delta,
    }
    if jti is not None:
        payload["jti"] = jti
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def create_access_token(subject: str) -> str:
    return _create_token(
        subject,
        "access",
        timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES),
    )


def create_refresh_token(subject: str) -> str:
    return _create_token(
        subject,
        "refresh",
        timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS),
        jti=uuid.uuid4().hex,
    )


def decode_token(token: str, expected_type: str) -> str | None:
    """Decode and validate a JWT. Returns the subject (user id) or None on failure."""
    payload = decode_token_details(token, expected_type)
    if payload is None:
        return None
    return payload.get("sub")


def decode_token_details(token: str, expected_type: str) -> dict[str, Any] | None:
    """Decode and validate a JWT, returning the full payload (subject, jti, exp...)."""
    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM],
        )
    except PyJWTError as exc:
        logger.debug("Token decode failed: %s", exc)
        return None
    if payload.get("type") != expected_type:
        logger.debug("Token type mismatch: expected=%s", expected_type)
        return None
    return payload
