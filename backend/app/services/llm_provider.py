"""LLM provider abstraction for the grounded financial assistant.

Only a generic OpenAI-compatible chat-completions HTTP client is shipped.
No vendor SDK or secret is ever bundled in the repository: the base URL,
API key and model id come exclusively from the runtime environment.

If the environment is not configured, the assistant API stays disabled and
returns a clear "llm_not_configured" error; the app never fabricates an
answer with a fake provider.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from typing import Awaitable, Callable, Protocol

import httpx

logger = logging.getLogger(__name__)


class LLMError(Exception):
    """Base error for provider-level failures (network, HTTP, bad payload)."""

    def __init__(self, message: str, category: str = "llm_error") -> None:
        super().__init__(message)
        self.category = category


@dataclass(frozen=True)
class LLMTurn:
    """One chat message: system/user/assistant."""

    role: str
    content: str


class LLMCompleter(Protocol):
    """Anything that can turn a message list into a completion string."""

    async def complete(self, messages: list[LLMTurn]) -> str: ...


CompleteCallable = Callable[[list[LLMTurn]], Awaitable[str]]


@dataclass(frozen=True)
class LLMConfig:
    """Provider configuration snapshot (values come from Settings)."""

    base_url: str
    api_key: str
    model: str
    timeout_seconds: int = 30


def _normalise_base_url(base_url: str) -> str:
    url = base_url.rstrip("/")
    if not url.startswith(("http://", "https://")):
        url = f"https://{url}"
    return url


class OpenAICompatProvider:
    """Minimal OpenAI-compatible chat completions client (httpx only)."""

    def __init__(self, config: LLMConfig, *, client_factory: Callable[[], httpx.AsyncClient] | None = None) -> None:
        self._config = config
        self._base_url = _normalise_base_url(config.base_url)
        self._client_factory = client_factory or (lambda: httpx.AsyncClient(timeout=config.timeout_seconds))

    async def complete(self, messages: list[LLMTurn]) -> str:
        payload = {
            "model": self._config.model,
            "messages": [{"role": turn.role, "content": turn.content} for turn in messages],
            "temperature": 0.2,
        }
        headers = {
            "Authorization": f"Bearer {self._config.api_key}",
            "Content-Type": "application/json",
        }
        try:
            async with self._client_factory() as client:
                response = await client.post(
                    f"{self._base_url}/chat/completions",
                    headers=headers,
                    content=json.dumps(payload),
                )
        except httpx.TimeoutException as exc:
            raise LLMError("LLM provider timed out", category="llm_timeout") from exc
        except httpx.HTTPError as exc:
            raise LLMError("LLM provider is unreachable", category="llm_unreachable") from exc

        if response.status_code >= 500:
            raise LLMError("LLM provider returned a server error", category="llm_upstream_error")
        if response.status_code == 429:
            raise LLMError("LLM provider rate-limited the request", category="llm_rate_limited")
        if response.status_code != 200:
            raise LLMError(
                f"LLM provider rejected the request (HTTP {response.status_code})",
                category="llm_request_error",
            )

        try:
            body = response.json()
        except ValueError as exc:
            raise LLMError("LLM provider returned an invalid response", category="llm_bad_response") from exc

        try:
            content = body["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise LLMError("LLM provider response was missing content", category="llm_bad_response") from exc

        if not isinstance(content, str) or not content.strip():
            raise LLMError("LLM provider returned an empty answer", category="llm_empty_response")

        return content.strip()