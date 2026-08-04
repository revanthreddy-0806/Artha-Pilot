"""Auth-related Pydantic schemas."""
from datetime import datetime
import re

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

PASSWORD_MIN = 8
PASSWORD_MAX_BYTES = 72


class RegisterRequest(BaseModel):
    email: EmailStr
    full_name: str = Field(min_length=2, max_length=120)
    password: str = Field(min_length=PASSWORD_MIN)

    @field_validator("password")
    @classmethod
    def password_strength(cls, v: str) -> str:
        if not re.search(r"[A-Z]", v):
            raise ValueError("password must contain an uppercase letter")
        if not re.search(r"[a-z]", v):
            raise ValueError("password must contain a lowercase letter")
        if not re.search(r"\d", v):
            raise ValueError("password must contain a digit")
        if len(v.encode("utf-8")) > PASSWORD_MAX_BYTES:
            raise ValueError("password must be at most 72 bytes (bcrypt limit)")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class RefreshRequest(BaseModel):
    refresh_token: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: EmailStr
    full_name: str
    is_active: bool
    created_at: datetime
