"""Grounding assistant chat schemas."""
from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """One prior chat turn echoed back by the client."""

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)


class ChatRequest(BaseModel):
    """Chat request: the data-fencing history + the user's message."""

    message: str = Field(min_length=1, max_length=2000)
    history: list[ChatMessage] = Field(default_factory=list, max_length=20)


class ChatResponse(BaseModel):
    """Answer + the verified data fence used to ground it (for auditability)."""

    answer: str
    grounded_context: dict = Field(default_factory=dict)
    disclaimer: str