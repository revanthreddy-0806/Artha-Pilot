"""Shared FastAPI dependencies."""
import logging
from typing import Annotated

from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.core.security import decode_token
from app.db import get_db
from app.models.user import User
from app.repositories.user_repository import UserRepository

logger = logging.getLogger(__name__)

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
    user = UserRepository(db).get_by_id(int(subject))
    if user is None or not user.is_active:
        raise AppError("Account not found or disabled", status_code=401, error_code="invalid_token")
    return user
