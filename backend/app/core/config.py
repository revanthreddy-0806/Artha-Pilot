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
    DEBUG: bool = False
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

    # Rate limiting (requests per minute per client, per endpoint group)
    RATE_LIMIT_REGISTER_PER_MINUTE: int = 10
    RATE_LIMIT_LOGIN_PER_MINUTE: int = 60
    RATE_LIMIT_REFRESH_PER_MINUTE: int = 60

    # Market reference data (daily refresh, not real-time)
    MARKET_DATA_SYMBOLS: str = "^NSEI:NIFTY 50,GC=F:Gold (COMEX)"
    MARKET_DATA_REFRESH_HOURS: int = 24
    MARKET_DATA_TIMEOUT_SECONDS: int = 10
    MARKET_DATA_URL_TEMPLATE: str = (
        "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?interval=1d&range=5d"
    )

    # Grounded AI assistant (LLM provider; keys come from the environment only).
    # The assistant only explains verified backend data; no LLM call is made
    # until all three values below are configured.
    LLM_PROVIDER: str = "openai-compatible"
    LLM_BASE_URL: str = ""  # e.g. https://api.openai.com/v1 (any OpenAI-compatible API)
    LLM_API_KEY: str = ""  # NEVER commit a real key; set via environment / .env
    LLM_MODEL: str = ""  # e.g. gpt-4o-mini
    LLM_TIMEOUT_SECONDS: int = 30
    LLM_MAX_RESPONSE_CHARS: int = 2400

    @property
    def llm_configured(self) -> bool:
        return bool(self.LLM_BASE_URL and self.LLM_API_KEY and self.LLM_MODEL)

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
