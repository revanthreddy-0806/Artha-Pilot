"""Unit tests for security primitives (bcrypt + JWT)."""
import jwt

from app.core.config import get_settings
from app.core.security import (
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)


def test_password_hash_and_verify():
    hashed = hash_password("MySecret123")
    assert hashed != "MySecret123"
    assert verify_password("MySecret123", hashed)
    assert not verify_password("WrongPass123", hashed)


def test_password_hash_is_salted():
    assert hash_password("SamePass123") != hash_password("SamePass123")


def test_verify_password_malformed_hash():
    assert not verify_password("x", "not-a-valid-hash")


def test_access_token_roundtrip():
    token = create_access_token("42")
    assert decode_token(token, "access") == "42"


def test_refresh_token_roundtrip():
    token = create_refresh_token("42")
    assert decode_token(token, "refresh") == "42"


def test_refresh_token_carries_jti():
    token = create_refresh_token("42")
    payload = jwt.decode(token, get_settings().JWT_SECRET_KEY, algorithms=["HS256"])
    assert payload["jti"]
    assert len(payload["jti"]) == 32


def test_token_type_mismatch():
    access = create_access_token("42")
    assert decode_token(access, "refresh") is None


def test_garbage_token_rejected():
    assert decode_token("not.a.jwt", "access") is None


def test_expired_token_rejected():
    settings = get_settings()
    expired = jwt.encode(
        {"sub": "42", "type": "access", "iat": 0, "exp": 1},
        settings.JWT_SECRET_KEY,
        algorithm=settings.JWT_ALGORITHM,
    )
    assert decode_token(expired, "access") is None
