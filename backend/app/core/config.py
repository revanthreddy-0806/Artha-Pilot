"""Application configuration loaded from environment variables."""
from functools import lru_cache

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

DEFAULT_SECRET_KEYS = {
    "dev-only-secret-change-me",
    "change-me-in-production",
    "generate-a-strong-random-secret",
}


class Settings(BaseSettings):
    """Runtime settings, overridable via environment variables or .env file."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    APP_NAME: str = "Artha Pilot API"
    ENVIRONMENT: str = "development"
    DEBUG: bool = True
    LOG_LEVEL: str = "INFO"

    # Database
    DATABASE_URL: str = "postgresql+psycopg2://artha:artha_dev_password@localhost:5432/artha_pilot"

    # Security / JWT
    JWT_SECRET_KEY: str = "dev-only-secret-change-me"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # CORS
    CORS_ORIGINS: list[str] = ["*"]

    # Uploads
    MAX_UPLOAD_MB: int = 10

    @property
    def is_production(self) -> bool:
        return self.ENVIRONMENT.lower() == "production"

    @property
    def max_upload_bytes(self) -> int:
        return self.MAX_UPLOAD_MB * 1024 * 1024

    @model_validator(mode="after")
    def _guard_production_secret(self) -> "Settings":
        if self.is_production and self.JWT_SECRET_KEY in DEFAULT_SECRET_KEYS:
            raise ValueError(
                "JWT_SECRET_KEY must be overridden with a strong random secret in production"
            )
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
