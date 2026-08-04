"""Authentication business logic."""
import logging
from datetime import datetime, timezone

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import (
    create_access_token,
    create_refresh_token,
    decode_token_details,
    hash_password,
    verify_password,
)
from app.models.user import User
from app.repositories.revoked_token_repository import RevokedTokenRepository
from app.repositories.user_repository import UserRepository

logger = logging.getLogger(__name__)


class AuthService:
    def __init__(self, db: Session) -> None:
        self._users = UserRepository(db)
        self._revoked = RevokedTokenRepository(db)

    def register(self, email: str, full_name: str, password: str) -> User:
        existing = self._users.get_by_email(email)
        if existing:
            logger.info("Registration attempted with existing email: %s", email)
            raise AppError(
                "An account with this email already exists",
                status_code=409,
                error_code="email_already_registered",
            )
        try:
            user = self._users.create(email, full_name, hash_password(password))
        except IntegrityError:
            self._users.rollback()
            logger.info("Registration raced on unique email: %s", email)
            raise AppError(
                "An account with this email already exists",
                status_code=409,
                error_code="email_already_registered",
            ) from None
        logger.info("Registered new user id=%s", user.id)
        return user

    def login(self, email: str, password: str) -> User:
        user = self._users.get_by_email(email)
        if user is None or not verify_password(password, user.password_hash):
            raise AppError(
                "Invalid email or password",
                status_code=401,
                error_code="invalid_credentials",
            )
        if not user.is_active:
            raise AppError("Account is disabled", status_code=403, error_code="account_disabled")
        return user

    def issue_tokens(self, user: User) -> dict[str, str]:
        subject = str(user.id)
        return {
            "access_token": create_access_token(subject),
            "refresh_token": create_refresh_token(subject),
        }

    def refresh(self, refresh_token: str) -> dict[str, str]:
        payload = decode_token_details(refresh_token, "refresh")
        if payload is None:
            raise AppError(
                "Invalid or expired refresh token",
                status_code=401,
                error_code="invalid_refresh_token",
            )
        try:
            user_id = int(payload["sub"])
            jti = payload["jti"]
            expires_at = datetime.fromtimestamp(payload["exp"], tz=timezone.utc)
        except (KeyError, ValueError, TypeError):
            raise AppError(
                "Invalid or expired refresh token",
                status_code=401,
                error_code="invalid_refresh_token",
            ) from None
        if self._revoked.is_revoked(jti):
            logger.warning("Refresh token reuse detected: jti=%s", jti)
            raise AppError(
                "Invalid or expired refresh token",
                status_code=401,
                error_code="invalid_refresh_token",
            )
        user = self._users.get_by_id(user_id)
        if user is None or not user.is_active:
            raise AppError("Account not found or disabled", status_code=401, error_code="invalid_refresh_token")
        self._revoked.revoke(jti, user_id=user.id, expires_at=expires_at)
        self._revoked.prune_expired()
        logger.info("Refreshed tokens for user id=%s", user.id)
        return self.issue_tokens(user)
