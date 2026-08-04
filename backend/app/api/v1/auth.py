"""Auth endpoints."""
from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.api.deps import get_auth_service, get_current_user
from app.models.user import User
from app.schemas.auth import LoginRequest, RefreshRequest, RegisterRequest, TokenPair, UserResponse
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=TokenPair, status_code=status.HTTP_201_CREATED)
def register(payload: RegisterRequest, auth: Annotated[AuthService, Depends(get_auth_service)]) -> TokenPair:
    user = auth.register(payload.email, payload.full_name, payload.password)
    return TokenPair(**auth.issue_tokens(user))


@router.post("/login", response_model=TokenPair)
def login(payload: LoginRequest, auth: Annotated[AuthService, Depends(get_auth_service)]) -> TokenPair:
    user = auth.login(payload.email, payload.password)
    return TokenPair(**auth.issue_tokens(user))


@router.post("/refresh", response_model=TokenPair)
def refresh(payload: RefreshRequest, auth: Annotated[AuthService, Depends(get_auth_service)]) -> TokenPair:
    return TokenPair(**auth.refresh(payload.refresh_token))


@router.get("/me", response_model=UserResponse)
def me(current_user: Annotated[User, Depends(get_current_user)]) -> User:
    return current_user
