"""Auth endpoints."""
from typing import Annotated

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.errors import AppError
from app.db import get_db
from app.models.user import User
from app.schemas.auth import LoginRequest, RefreshRequest, RegisterRequest, TokenPair, UserResponse
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=TokenPair, status_code=status.HTTP_201_CREATED)
def register(payload: RegisterRequest, db: Annotated[Session, Depends(get_db)]) -> TokenPair:
    user = AuthService(db).register(payload.email, payload.full_name, payload.password)
    return TokenPair(**AuthService(db).issue_tokens(user))


@router.post("/login", response_model=TokenPair)
def login(payload: LoginRequest, db: Annotated[Session, Depends(get_db)]) -> TokenPair:
    user = AuthService(db).login(payload.email, payload.password)
    return TokenPair(**AuthService(db).issue_tokens(user))


@router.post("/refresh", response_model=TokenPair)
def refresh(payload: RefreshRequest, db: Annotated[Session, Depends(get_db)]) -> TokenPair:
    return TokenPair(**AuthService(db).refresh(payload.refresh_token))


@router.get("/me", response_model=UserResponse)
def me(current_user: Annotated[User, Depends(get_current_user)]) -> User:
    return current_user
