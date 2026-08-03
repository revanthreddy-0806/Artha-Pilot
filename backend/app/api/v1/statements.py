"""Statement upload endpoints."""
from typing import Annotated

from fastapi import APIRouter, Depends, File, UploadFile, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.errors import AppError
from app.db import get_db
from app.models.user import User
from app.schemas.statement import StatementUploadResponse
from app.services.statement_service import StatementService

router = APIRouter(prefix="/statements", tags=["statements"])


@router.post("/upload", response_model=StatementUploadResponse, status_code=status.HTTP_201_CREATED)
async def upload_statement(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    file: UploadFile = File(...),
) -> StatementUploadResponse:
    if not file.filename:
        raise AppError("No file provided", status_code=400, error_code="missing_file")
    content = await file.read()
    statement = StatementService(db).ingest(current_user.id, file.filename, content)
    response = StatementUploadResponse.model_validate(statement)
    response.transactions = [
        {
            "id": t.id,
            "statement_id": t.statement_id,
            "date": t.date,
            "description": t.description,
            "amount": t.amount,
            "transaction_type": t.transaction_type.value,
            "balance": t.balance,
            "category": t.category,
            "confidence": t.confidence,
            "is_recurring": t.is_recurring,
        }
        for t in statement.transactions
    ]
    return response
