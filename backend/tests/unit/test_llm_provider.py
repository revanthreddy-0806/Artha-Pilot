"""Unit tests for the LLM provider abstraction (no network, no secrets).

The provider is async (httpx); tests drive it with asyncio.run since the
project has no pytest-asyncio plugin.
"""
import asyncio
import json

import httpx
import pytest

from app.services.llm_provider import (
    LLMConfig,
    LLMError,
    LLMTurn,
    OpenAICompatProvider,
)


class RecordingClient:
    """httpx-compatible stub that captures the request and returns a canned body."""

    def __init__(self, status: int = 200, body: dict | None = None, exc: Exception | None = None):
        self.status = status
        self.body = body or {"choices": [{"message": {"content": "safe answer"}}]}
        self.exc = exc
        self.calls: list[dict] = []

    async def post(self, url: str, headers: dict, content: str) -> "RecordingClient":
        self.calls.append({"url": url, "headers": headers, "content": content})
        if self.exc is not None:
            raise self.exc
        self._url = url
        self._headers = headers
        return self

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return None

    @property
    def status_code(self) -> int:
        return self.status

    def json(self):
        return self.body


def make_provider(client: RecordingClient) -> OpenAICompatProvider:
    return OpenAICompatProvider(
        LLMConfig(base_url="https://llm.example.com/v1", api_key="test-key", model="test-model"),
        client_factory=lambda: client,
    )


def run(coro):
    return asyncio.run(coro)


def test_complete_sends_expected_payload():
    client = RecordingClient()
    provider = make_provider(client)

    turns = [LLMTurn(role="system", content="sys"), LLMTurn(role="user", content="hello")]
    answer = run(provider.complete(turns))

    assert answer == "safe answer"
    assert client.calls[0]["url"] == "https://llm.example.com/v1/chat/completions"
    assert client.calls[0]["headers"]["Authorization"] == "Bearer test-key"
    body = json.loads(client.calls[0]["content"])
    assert body["model"] == "test-model"
    assert body["messages"] == [
        {"role": "system", "content": "sys"},
        {"role": "user", "content": "hello"},
    ]


def test_complete_error_mapping():
    cases = [
        (httpx.TimeoutException("t"), "llm_timeout"),
        (httpx.ConnectError("c"), "llm_unreachable"),
    ]
    for exc, expected in cases:
        with pytest.raises(LLMError) as err:
            run(make_provider(RecordingClient(exc=exc)).complete([LLMTurn(role="user", content="x")]))
        assert err.value.category == expected


def test_complete_http_error_statuses():
    for status in (429, 500, 502, 400, 418):
        with pytest.raises(LLMError) as err:
            run(make_provider(RecordingClient(status=status)).complete([LLMTurn(role="user", content="x")]))
        if status == 429:
            assert err.value.category == "llm_rate_limited"
        elif status >= 500:
            assert err.value.category == "llm_upstream_error"
        else:
            assert err.value.category == "llm_request_error"


def test_complete_bad_and_empty_responses():
    with pytest.raises(LLMError) as err:
        run(make_provider(RecordingClient(body={"unexpected": True})).complete(
            [LLMTurn(role="user", content="x")]
        ))
    assert err.value.category == "llm_bad_response"

    with pytest.raises(LLMError) as err:
        run(make_provider(RecordingClient(body={"choices": [{"message": {"content": "   "}}]})).complete(
            [LLMTurn(role="user", content="x")]
        ))
    assert err.value.category == "llm_empty_response"


def test_api_key_never_leaked_in_error_messages():
    provider = make_provider(RecordingClient(status=400))
    with pytest.raises(LLMError) as err:
        run(provider.complete([LLMTurn(role="user", content="x")]))
    assert "test-key" not in str(err.value)