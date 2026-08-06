"""Auth endpoints."""
from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.api.deps import get_auth_service, get_current_user
from app.core.config import get_settings
from app.core.rate_limit import rate_limit_dependency
from app.models.user import User
from app.schemas.auth import LoginRequest, RefreshRequest, RegisterRequest, TokenPair, UserResponse
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])

settings = get_settings()

_register_limiter = rate_limit_dependency(settings.RATE_LIMIT_REGISTER_PER_MINUTE)
_login_limiter = rate_limit_dependency(settings.RATE_LIMIT_LOGIN_PER_MINUTE)
_refresh_limiter = rate_limit_dependency(settings.RATE_LIMIT_REFRESH_PER_MINUTE)


@router.post("/register", response_model=TokenPair, status_code=status.HTTP_201_CREATED)
def register(
    payload: RegisterRequest,
    auth: Annotated[AuthService, Depends(get_auth_service)],
    rate_guard: Annotated[None, Depends(_register_limiter)],
) -> TokenPair:
    user = auth.register(payload.email, payload.full_name, payload.password)
    return TokenPair(**auth.issue_tokens(user))


@router.post("/login", response_model=TokenPair)
def login(
    payload: LoginRequest,
    auth: Annotated[AuthService, Depends(get_auth_service)],
    rate_guard: Annotated[None, Depends(_login_limiter)],
) -> TokenPair:
    user = auth.login(payload.email, payload.password)
    return TokenPair(**auth.issue_tokens(user))


@router.post("/refresh", response_model=TokenPair)
def refresh(
    payload: RefreshRequest,
    auth: Annotated[AuthService, Depends(get_auth_service)],
    rate_guard: Annotated[None, Depends(_refresh_limiter)],
) -> TokenPair:
    return TokenPair(**auth.refresh(payload.refresh_token))


@router.get("/me", response_model=UserResponse)
def me(current_user: Annotated[User, Depends(get_current_user)]) -> User:
    return current_user
