from __future__ import annotations

import asyncio
import os
import random
import logging
import time
from dataclasses import dataclass
from typing import Protocol
from urllib.parse import urlparse

import httpx

LOGGER = logging.getLogger("uvicorn.error")

class LlmError(Exception):
    code = "LLM_PROVIDER_ERROR"
    retryable = False

    def __init__(self, message: str = ""):
        super().__init__(message or self.code)


class LlmNotConfiguredError(LlmError):
    code = "LLM_NOT_CONFIGURED"


class LlmUnauthorizedError(LlmError):
    code = "LLM_UNAUTHORIZED"


class LlmForbiddenError(LlmError):
    code = "LLM_FORBIDDEN"


class LlmRateLimitedError(LlmError):
    code = "LLM_RATE_LIMITED"
    retryable = True


class LlmTimeoutError(LlmError):
    code = "LLM_TIMEOUT"
    retryable = True


class LlmConnectionError(LlmError):
    code = "LLM_CONNECTION_ERROR"
    retryable = True


class LlmBadRequestError(LlmError):
    code = "LLM_BAD_REQUEST"


class LlmBadResponseError(LlmError):
    code = "LLM_BAD_RESPONSE"


class LlmProviderError(LlmError):
    code = "LLM_PROVIDER_ERROR"

    def __init__(self, message: str = "", retryable: bool = False):
        super().__init__(message)
        self.retryable = retryable


@dataclass(frozen=True)
class LlmUsage:
    input_tokens: int | None = None
    output_tokens: int | None = None


@dataclass(frozen=True)
class LlmResponse:
    text: str
    model: str
    usage: LlmUsage
    request_id: str | None = None


@dataclass(frozen=True)
class LlmSettings:
    provider: str = "komapi"
    openai_api_key: str = ""
    openai_api_url: str = "https://api.openai.com/v1/chat/completions"
    openai_model: str = "gpt-4o-mini"
    openai_timeout_seconds: float = 45.0
    komapi_api_key: str = ""
    komapi_base_url: str = "https://www.komapi.top"
    komapi_model: str = "claude-haiku-4-5"
    komapi_anthropic_version: str = "2023-06-01"
    komapi_max_tokens: int = 700
    komapi_timeout_seconds: float = 60.0
    max_concurrency: int = 3
    max_retries: int = 3
    retry_base_delay_seconds: float = 1.0


class LlmClient(Protocol):
    provider: str
    model: str

    async def generate_text(self, *, system_prompt: str, user_prompt: str, max_tokens: int | None = None) -> LlmResponse:
        ...


_SEMAPHORE = asyncio.Semaphore(max(1, int(os.environ.get("COVER_LETTER_MAX_CONCURRENCY", "3"))))


def load_llm_settings() -> LlmSettings:
    return LlmSettings(
        provider=os.environ.get("LLM_PROVIDER", "komapi").strip().casefold() or "komapi",
        openai_api_key=os.environ.get("LLM_API_KEY", ""),
        openai_api_url=os.environ.get("LLM_API_URL", "https://api.openai.com/v1/chat/completions"),
        openai_model=os.environ.get("LLM_MODEL", "gpt-4o-mini"),
        openai_timeout_seconds=float(os.environ.get("LLM_TIMEOUT_SECONDS", "45")),
        komapi_api_key=os.environ.get("KOMAPI_API_KEY", ""),
        komapi_base_url=os.environ.get("KOMAPI_BASE_URL", "https://www.komapi.top").rstrip("/"),
        komapi_model=os.environ.get("KOMAPI_MODEL", "claude-haiku-4-5"),
        komapi_anthropic_version=os.environ.get("KOMAPI_ANTHROPIC_VERSION", "2023-06-01"),
        komapi_max_tokens=int(os.environ.get("KOMAPI_MAX_TOKENS", "700")),
        komapi_timeout_seconds=float(os.environ.get("KOMAPI_TIMEOUT_SECONDS", "60")),
        max_concurrency=max(1, int(os.environ.get("COVER_LETTER_MAX_CONCURRENCY", "3"))),
        max_retries=max(0, int(os.environ.get("COVER_LETTER_MAX_RETRIES", "3"))),
        retry_base_delay_seconds=max(0.0, float(os.environ.get("COVER_LETTER_RETRY_BASE_DELAY_SECONDS", "1"))),
    )


def validate_llm_settings(settings: LlmSettings) -> None:
    if settings.provider not in {"komapi", "openai"}:
        raise LlmNotConfiguredError("unsupported LLM_PROVIDER")
    base = settings.komapi_base_url if settings.provider == "komapi" else settings.openai_api_url
    parsed = urlparse(base)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise LlmNotConfiguredError("LLM base URL must be http/https")
    model = settings.komapi_model if settings.provider == "komapi" else settings.openai_model
    if not model.strip():
        raise LlmNotConfiguredError("LLM model is empty")
    if settings.komapi_max_tokens <= 0:
        raise LlmNotConfiguredError("KOMAPI_MAX_TOKENS must be positive")
    if settings.komapi_timeout_seconds <= 0 or settings.openai_timeout_seconds <= 0:
        raise LlmNotConfiguredError("LLM timeout must be positive")
    if settings.max_concurrency < 1:
        raise LlmNotConfiguredError("COVER_LETTER_MAX_CONCURRENCY must be >= 1")
    if settings.max_retries < 0:
        raise LlmNotConfiguredError("COVER_LETTER_MAX_RETRIES must be >= 0")


def extract_anthropic_text(payload: dict) -> str:
    blocks = payload.get("content")
    if isinstance(blocks, str):
        result = blocks.strip()
        if result:
            return result
        raise LlmBadResponseError("Response content is empty")
    if not isinstance(blocks, list):
        raise LlmBadResponseError("Response content is missing")
    parts = []
    for block in blocks:
        if not isinstance(block, dict) or block.get("type") != "text":
            continue
        text = block.get("text")
        if isinstance(text, str) and text.strip():
            parts.append(text.strip())
    result = "\n".join(parts).strip()
    if not result:
        raise LlmBadResponseError("Response contains no text blocks")
    return result


def _error_for_status(status_code: int, retryable_provider: bool = False) -> LlmError:
    if status_code == 400:
        return LlmBadRequestError("LLM_BAD_REQUEST")
    if status_code == 401:
        return LlmUnauthorizedError("LLM_UNAUTHORIZED")
    if status_code == 403:
        return LlmForbiddenError("LLM_FORBIDDEN")
    if status_code == 429:
        return LlmRateLimitedError("LLM_RATE_LIMITED")
    if status_code in {500, 502, 503, 504}:
        return LlmProviderError("LLM_PROVIDER_ERROR", retryable=True)
    return LlmProviderError("LLM_PROVIDER_ERROR", retryable=retryable_provider)


def _retry_delay(response: httpx.Response | None, settings: LlmSettings, attempt: int) -> float:
    retry_after = response.headers.get("Retry-After") if response is not None else ""
    try:
        if retry_after:
            return max(0.0, min(float(retry_after), 30.0))
    except ValueError:
        pass
    return settings.retry_base_delay_seconds * (2 ** attempt) + random.uniform(0, 0.25)


class KomapiAnthropicClient:
    provider = "komapi"

    def __init__(self, settings: LlmSettings | None = None, http_client: httpx.AsyncClient | None = None):
        self.settings = settings or load_llm_settings()
        validate_llm_settings(self.settings)
        self.model = self.settings.komapi_model
        self._client = http_client or httpx.AsyncClient(
            base_url=self.settings.komapi_base_url,
            timeout=self.settings.komapi_timeout_seconds,
            limits=httpx.Limits(max_connections=20, max_keepalive_connections=10),
        )
        self._owns_client = http_client is None

    async def aclose(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    async def generate_text(self, *, system_prompt: str, user_prompt: str, max_tokens: int | None = None) -> LlmResponse:
        if not self.settings.komapi_api_key:
            raise LlmNotConfiguredError("LLM_NOT_CONFIGURED")
        payload = {
            "model": self.model,
            "max_tokens": int(max_tokens or self.settings.komapi_max_tokens),
            "system": system_prompt,
            "messages": [{"role": "user", "content": user_prompt}],
        }
        headers = {
            "x-api-key": self.settings.komapi_api_key,
            "anthropic-version": self.settings.komapi_anthropic_version,
            "content-type": "application/json",
        }
        async with _SEMAPHORE:
            return await self._post_messages(payload, headers)

    async def _post_messages(self, payload: dict, headers: dict) -> LlmResponse:
        last_error: LlmError | None = None
        for attempt in range(self.settings.max_retries + 1):
            response: httpx.Response | None = None
            started = time.monotonic()
            try:
                LOGGER.info("llm_request start provider=%s model=%s endpoint=/v1/messages attempt=%s", self.provider, self.model, attempt + 1)
                response = await self._client.post("/v1/messages", headers=headers, json=payload)
                LOGGER.info(
                    "llm_request response provider=%s model=%s status=%s attempt=%s elapsed=%.2fs",
                    self.provider,
                    self.model,
                    response.status_code,
                    attempt + 1,
                    time.monotonic() - started,
                )
                if response.status_code >= 400:
                    error = _error_for_status(response.status_code)
                    if error.retryable and attempt < self.settings.max_retries:
                        LOGGER.warning("llm_request retry provider=%s code=%s attempt=%s", self.provider, error.code, attempt + 1)
                        await asyncio.sleep(_retry_delay(response, self.settings, attempt))
                        continue
                    raise error
                data = response.json()
                usage = data.get("usage") if isinstance(data.get("usage"), dict) else {}
                return LlmResponse(
                    text=extract_anthropic_text(data),
                    model=str(data.get("model") or self.model),
                    usage=LlmUsage(usage.get("input_tokens"), usage.get("output_tokens")),
                    request_id=response.headers.get("request-id") or response.headers.get("x-request-id") or data.get("id"),
                )
            except httpx.TimeoutException:
                LOGGER.warning("llm_request timeout provider=%s model=%s attempt=%s elapsed=%.2fs", self.provider, self.model, attempt + 1, time.monotonic() - started)
                last_error = LlmTimeoutError("LLM_TIMEOUT")
            except httpx.TransportError:
                LOGGER.warning("llm_request connection_error provider=%s model=%s attempt=%s elapsed=%.2fs", self.provider, self.model, attempt + 1, time.monotonic() - started)
                last_error = LlmConnectionError("LLM_CONNECTION_ERROR")
            except LlmError as exc:
                LOGGER.warning("llm_request error provider=%s model=%s code=%s attempt=%s elapsed=%.2fs", self.provider, self.model, exc.code, attempt + 1, time.monotonic() - started)
                last_error = exc
            if not last_error.retryable or attempt >= self.settings.max_retries:
                raise last_error
            LOGGER.warning("llm_request retry provider=%s code=%s next_attempt=%s", self.provider, last_error.code, attempt + 2)
            await asyncio.sleep(_retry_delay(response, self.settings, attempt))
        raise last_error or LlmProviderError("LLM_PROVIDER_ERROR")

    async def check_status(self) -> dict:
        if not self.settings.komapi_api_key:
            return {
                "configured": False,
                "provider": self.provider,
                "reachable": False,
                "model": self.model,
                "modelAvailable": False,
                "errorCode": "LLM_NOT_CONFIGURED",
                "message": "KOMAPI_API_KEY не настроен",
            }
        try:
            response = await self._client.get("/v1/models", headers={"Authorization": f"Bearer {self.settings.komapi_api_key}"})
            if response.status_code >= 400:
                error = _error_for_status(response.status_code)
                return {
                    "configured": True,
                    "provider": self.provider,
                    "reachable": False,
                    "model": self.model,
                    "modelAvailable": False,
                    "errorCode": error.code,
                    "message": "Не удалось проверить ключ KomAPI",
                }
            data = response.json()
            raw_models = data.get("data", data if isinstance(data, list) else [])
            models = []
            if isinstance(raw_models, list):
                for item in raw_models:
                    if isinstance(item, str):
                        models.append(item)
                    elif isinstance(item, dict) and isinstance(item.get("id"), str):
                        models.append(item["id"])
            return {
                "configured": True,
                "provider": self.provider,
                "reachable": True,
                "model": self.model,
                "modelAvailable": self.model in models,
                "availableModels": models,
            }
        except httpx.TimeoutException:
            code = "LLM_TIMEOUT"
        except httpx.TransportError:
            code = "LLM_CONNECTION_ERROR"
        except Exception:
            code = "LLM_BAD_RESPONSE"
        return {
            "configured": True,
            "provider": self.provider,
            "reachable": False,
            "model": self.model,
            "modelAvailable": False,
            "errorCode": code,
            "message": "Не удалось проверить подключение KomAPI",
        }


class OpenAiCompatibleClient:
    provider = "openai"

    def __init__(self, settings: LlmSettings | None = None, http_client: httpx.AsyncClient | None = None):
        self.settings = settings or load_llm_settings()
        validate_llm_settings(self.settings)
        self.model = self.settings.openai_model
        self._client = http_client or httpx.AsyncClient(timeout=self.settings.openai_timeout_seconds)
        self._owns_client = http_client is None

    async def aclose(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    async def generate_text(self, *, system_prompt: str, user_prompt: str, max_tokens: int | None = None) -> LlmResponse:
        if not self.settings.openai_api_key:
            raise LlmNotConfiguredError("LLM_NOT_CONFIGURED")
        payload = {
            "model": self.model,
            "messages": [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_prompt}],
            "temperature": 0.55,
        }
        if max_tokens:
            payload["max_tokens"] = int(max_tokens)
        headers = {"Authorization": f"Bearer {self.settings.openai_api_key}", "content-type": "application/json"}
        async with _SEMAPHORE:
            last_error: LlmError | None = None
            for attempt in range(self.settings.max_retries + 1):
                response: httpx.Response | None = None
                try:
                    response = await self._client.post(self.settings.openai_api_url, headers=headers, json=payload)
                    if response.status_code >= 400:
                        error = _error_for_status(response.status_code)
                        if error.retryable and attempt < self.settings.max_retries:
                            await asyncio.sleep(_retry_delay(response, self.settings, attempt))
                            continue
                        raise error
                    data = response.json()
                    content = data["choices"][0]["message"]["content"]
                    usage = data.get("usage") if isinstance(data.get("usage"), dict) else {}
                    return LlmResponse(
                        text=str(content or "").strip(),
                        model=str(data.get("model") or self.model),
                        usage=LlmUsage(usage.get("prompt_tokens"), usage.get("completion_tokens")),
                        request_id=response.headers.get("request-id") or response.headers.get("x-request-id") or data.get("id"),
                    )
                except httpx.TimeoutException:
                    last_error = LlmTimeoutError("LLM_TIMEOUT")
                except httpx.TransportError:
                    last_error = LlmConnectionError("LLM_CONNECTION_ERROR")
                except (KeyError, IndexError, TypeError):
                    last_error = LlmBadResponseError("LLM_BAD_RESPONSE")
                except LlmError as exc:
                    last_error = exc
                if not last_error.retryable or attempt >= self.settings.max_retries:
                    raise last_error
                await asyncio.sleep(_retry_delay(response, self.settings, attempt))
            raise last_error or LlmProviderError("LLM_PROVIDER_ERROR")


def create_llm_client(settings: LlmSettings | None = None) -> LlmClient:
    settings = settings or load_llm_settings()
    validate_llm_settings(settings)
    if settings.provider == "komapi":
        return KomapiAnthropicClient(settings)
    if settings.provider == "openai":
        return OpenAiCompatibleClient(settings)
    raise LlmNotConfiguredError("unsupported LLM_PROVIDER")
