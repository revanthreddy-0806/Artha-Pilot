"""Unit tests for validation-error sanitization (no credential material in
logs or responses)."""
import pytest

from app.core.errors import _sanitize_validation_errors


def test_sanitize_drops_input_field():
    errors = [
        {
            "type": "string_too_short",
            "loc": ["body", "full_name"],
            "msg": "String should have at least 2 characters",
            "input": "D",
            "ctx": {"min_length": 2},
        },
        {
            "type": "string_too_short",
            "loc": ["body", "password"],
            "msg": "String should have at least 8 characters",
            "input": "secretpassword123",
            "ctx": {"min_length": 8},
        },
    ]
    cleaned = _sanitize_validation_errors(errors)
    for item in cleaned:
        assert "input" not in item
        assert "password" not in item
    assert cleaned[0]["ctx"] == {"min_length": "2"}
    assert cleaned[1]["loc"] == ["body", "password"]


def test_sanitize_handles_non_dict_ctx():
    errors = [{"type": "value_error", "loc": ["body"], "msg": "bad", "input": "x", "ctx": None}]
    cleaned = _sanitize_validation_errors(errors)
    assert "input" not in cleaned[0]
    assert cleaned[0]["ctx"] is None


def test_422_response_never_echoes_request_body(client):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "weak@example.com", "full_name": "Weak", "password": "TopSecret"},
    )
    assert resp.status_code == 422
    details = resp.json()["error"]["details"]
    assert isinstance(details, list)
    for item in details:
        assert "input" not in item
    assert all("TopSecret" not in str(item) for item in details)


def test_error_envelope_shape(client):
    resp = client.post(
        "/api/v1/auth/register",
        json={"email": "weak2@example.com", "full_name": "Weak", "password": "short"},
    )
    assert resp.status_code == 422
    body = resp.json()
    assert body["error"]["code"] == "validation_error"
    assert body["error"]["message"] == "Request validation failed"
