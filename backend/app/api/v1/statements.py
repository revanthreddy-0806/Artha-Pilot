"""Statement upload endpoints."""
from typing import Annotated

from fastapi import APIRouter, Depends, File, Query, Request, UploadFile, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.config import get_settings
from app.core.errors import AppError
from app.db import get_db
from app.models.user import User
from app.repositories.statement_repository import StatementRepository
from app.schemas.statement import StatementListResponse, StatementOut, StatementUploadResponse
from app.services.statement_service import StatementService

router = APIRouter(prefix="/statements", tags=["statements"])

_CHUNK_SIZE = 1024 * 1024


@router.get("", response_model=StatementListResponse)
def list_statements(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    page: int = Query(1, ge=1),
    page_size: int = Query(50, ge=1, le=100),
) -> StatementListResponse:
    items, total = StatementRepository(db).list_for_user(current_user.id, page=page, page_size=page_size)
    return StatementListResponse(
        items=[StatementOut.model_validate(s) for s in items],
        total=total,
        page=page,
        page_size=page_size,
    )


async def _read_upload_limited(request: Request, file: UploadFile, max_bytes: int) -> bytes:
    """Read an upload in chunks, rejecting bodies that exceed max_bytes."""
    content_length = request.headers.get("content-length")
    if content_length and content_length.isdigit() and int(content_length) > max_bytes:
        raise AppError(
            f"File exceeds {max_bytes // (1024 * 1024)} MB limit",
            status_code=413,
            error_code="file_too_large",
        )
    chunks: list[bytes] = []
    size = 0
    while True:
        chunk = await file.read(_CHUNK_SIZE)
        if not chunk:
            break
        size += len(chunk)
        if size > max_bytes:
            raise AppError(
                f"File exceeds {max_bytes // (1024 * 1024)} MB limit",
                status_code=413,
                error_code="file_too_large",
            )
        chunks.append(chunk)
    return b"".join(chunks)


@router.post("/upload", response_model=StatementUploadResponse, status_code=status.HTTP_201_CREATED)
async def upload_statement(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    request: Request,
    file: UploadFile = File(...),
) -> StatementUploadResponse:
    if not file.filename:
        raise AppError("No file provided", status_code=400, error_code="missing_file")
    content = await _read_upload_limited(request, file, get_settings().max_upload_bytes)
    statement = StatementService(db).ingest(current_user.id, file.filename, content)
    return StatementUploadResponse.model_validate(statement)
