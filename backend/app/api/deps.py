"""Shared FastAPI dependencies."""
from typing import Annotated

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import decode_token
from app.db import get_db
from app.models.user import User
from app.repositories.user_repository import UserRepository
from app.services.auth_service import AuthService

bearer_scheme = HTTPBearer(auto_error=False)


def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    db: Annotated[Session, Depends(get_db)],
) -> User:
    if credentials is None:
        raise AppError(
            "Authentication required",
            status_code=401,
            error_code="missing_token",
        )
    subject = decode_token(credentials.credentials, "access")
    if subject is None:
        raise AppError(
            "Invalid or expired access token",
            status_code=401,
            error_code="invalid_token",
        )
    try:
        user_id = int(subject)
    except ValueError:
        raise AppError(
            "Invalid or expired access token",
            status_code=401,
            error_code="invalid_token",
        ) from None
    user = UserRepository(db).get_by_id(user_id)
    if user is None or not user.is_active:
        raise AppError("Account not found or disabled", status_code=401, error_code="invalid_token")
    return user


def get_auth_service(db: Annotated[Session, Depends(get_db)]) -> AuthService:
    """Per-request AuthService (cached by FastAPI for the request lifetime)."""
    return AuthService(db)
