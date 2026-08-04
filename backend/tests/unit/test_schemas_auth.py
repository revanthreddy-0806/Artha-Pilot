"""Unit tests for auth schemas validation."""
import pytest
from pydantic import ValidationError

from app.schemas.auth import RegisterRequest


def _register(password: str) -> RegisterRequest:
    return RegisterRequest(email="ok@example.com", full_name="Test User", password=password)


def test_valid_password_accepted():
    assert _register("StrongPass123").password == "StrongPass123"


def test_password_exceeding_72_bytes_rejected():
    with pytest.raises(ValidationError):
        _register("Aa1" + "x" * 70)


def test_password_at_72_bytes_accepted():
    assert _register("Aa1" + "x" * 69).password


@pytest.mark.parametrize(
    "password",
    ["short", "lowercase123", "UPPERCASE123", "NoDigitsHere", "nouppercase1"],
)
def test_weak_passwords_rejected(password):
    with pytest.raises(ValidationError):
        _register(password)
