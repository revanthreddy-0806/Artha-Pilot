"""Grounded AI assistant endpoint (Phase 4)."""
import logging
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.config import Settings, get_settings
from app.db import get_db
from app.models.user import User
from app.schemas.assistant import ChatRequest, ChatResponse
from app.services.assistant_service import AssistantService, provider_from_settings
from app.services.llm_provider import LLMCompleter

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/assistant", tags=["assistant"])

DbDep = Annotated[Session, Depends(get_db)]
CurrentUserDep = Annotated[User, Depends(get_current_user)]


def _provider(settings: Settings) -> LLMCompleter:
    """Build the LLM provider from env-only settings (503 when unconfigured)."""
    return provider_from_settings(settings)


@router.post("/chat", response_model=ChatResponse)
async def assistant_chat(
    payload: ChatRequest,
    current_user: CurrentUserDep,
    db: DbDep,
) -> ChatResponse:
    settings = get_settings()
    return await AssistantService(db, settings, _provider(settings)).chat(current_user.id, payload)