"""Authentication business logic."""
import logging

from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import (
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)
from app.models.user import User
from app.repositories.user_repository import UserRepository

logger = logging.getLogger(__name__)


class AuthService:
    def __init__(self, db: Session) -> None:
        self._users = UserRepository(db)

    def register(self, email: str, full_name: str, password: str) -> User:
        existing = self._users.get_by_email(email)
        if existing:
            logger.info("Registration attempted with existing email: %s", email)
            raise AppError(
                "An account with this email already exists",
                status_code=409,
                error_code="email_already_registered",
            )
        user = self._users.create(email, full_name, hash_password(password))
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
        subject = decode_token(refresh_token, "refresh")
        if subject is None:
            raise AppError(
                "Invalid or expired refresh token",
                status_code=401,
                error_code="invalid_refresh_token",
            )
        user = self._users.get_by_id(int(subject))
        if user is None or not user.is_active:
            raise AppError("Account not found or disabled", status_code=401, error_code="invalid_refresh_token")
        logger.info("Refreshed tokens for user id=%s", user.id)
        return self.issue_tokens(user)
