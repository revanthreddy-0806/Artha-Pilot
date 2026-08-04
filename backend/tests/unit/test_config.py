"""Unit tests for application settings and production guards."""
import pytest

from app.core.config import DEFAULT_SECRET_KEYS, Settings


def test_defaults_for_development():
    settings = Settings()
    assert settings.ENVIRONMENT == "test"
    assert settings.max_upload_bytes == 10 * 1024 * 1024
    assert settings.CORS_ORIGINS == ["*"]


def test_production_rejects_default_secret():
    with pytest.raises(ValueError):
        Settings(ENVIRONMENT="production", JWT_SECRET_KEY="dev-only-secret-change-me")


@pytest.mark.parametrize("default", sorted(DEFAULT_SECRET_KEYS))
def test_production_rejects_any_default_secret(default):
    with pytest.raises(ValueError):
        Settings(ENVIRONMENT="production", JWT_SECRET_KEY=default)


def test_production_accepts_strong_secret():
    settings = Settings(ENVIRONMENT="production", JWT_SECRET_KEY="a-strong-random-secret-0123456789")
    assert settings.is_production


def test_cors_origins_parsed():
    settings = Settings(CORS_ORIGINS=["http://localhost:3000", "https://app.example.com"])
    assert settings.CORS_ORIGINS == ["http://localhost:3000", "https://app.example.com"]


def test_max_upload_bytes_scales_with_mb():
    assert Settings(MAX_UPLOAD_MB=2).max_upload_bytes == 2 * 1024 * 1024
