"""Unified error model and exception handling for the API."""
import logging
from typing import Any

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

logger = logging.getLogger(__name__)


class AppError(Exception):
    """Base application error carrying an HTTP status code and error code."""

    def __init__(
        self,
        message: str,
        status_code: int = status.HTTP_400_BAD_REQUEST,
        error_code: str = "app_error",
        details: Any | None = None,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.error_code = error_code
        self.details = details


def _error_response(status_code: int, error_code: str, message: str, details: Any | None = None) -> JSONResponse:
    body: dict[str, Any] = {
        "error": {
            "code": error_code,
            "message": message,
        }
    }
    if details is not None:
        body["error"]["details"] = details
    return JSONResponse(status_code=status_code, content=body)


def _sanitize_validation_errors(errors: Any) -> list[dict[str, Any]]:
    """Convert RequestValidationError.errors() into JSON-safe structures.

    The ``input`` field is dropped so request bodies (which may contain
    credentials such as plaintext passwords) are never echoed back or logged.
    """
    cleaned: list[dict[str, Any]] = []
    for err in errors:
        item = dict(err)
        item.pop("input", None)
        ctx = item.get("ctx")
        if isinstance(ctx, dict):
            item["ctx"] = {k: str(v) for k, v in ctx.items()}
        elif ctx is not None:
            item["ctx"] = str(ctx)
        cleaned.append(item)
    return cleaned


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
        logger.warning("AppError %s on %s %s: %s", exc.status_code, request.method, request.url.path, exc.message)
        return _error_response(exc.status_code, exc.error_code, exc.message, exc.details)

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        safe_errors = _sanitize_validation_errors(exc.errors())
        logger.info("Validation error on %s %s: %s", request.method, request.url.path, safe_errors)
        return _error_response(
            status.HTTP_422_UNPROCESSABLE_ENTITY,
            "validation_error",
            "Request validation failed",
            safe_errors,
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_error_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        codes = {
            401: ("unauthorized", "Authentication failed"),
            403: ("forbidden", "You do not have permission to perform this action"),
            404: ("not_found", "The requested resource was not found"),
            405: ("method_not_allowed", "This method is not allowed for this resource"),
        }
        error_code, message = codes.get(exc.status_code, ("http_error", str(exc.detail)))
        logger.info("HTTP %s on %s %s", exc.status_code, request.method, request.url.path)
        return _error_response(exc.status_code, error_code, message)

    @app.exception_handler(Exception)
    async def unhandled_error_handler(request: Request, exc: Exception) -> JSONResponse:
        logger.exception("Unhandled error on %s %s", request.method, request.url.path)
        return _error_response(
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "internal_error",
            "An unexpected error occurred",
        )
