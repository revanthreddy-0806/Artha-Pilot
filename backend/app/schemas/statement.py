"""Statement-related Pydantic schemas."""
from datetime import date, datetime

from pydantic import BaseModel, ConfigDict

from app.schemas.transaction import TransactionOut


class StatementOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    filename: str
    file_type: str
    bank_name: str | None
    account_number: str | None
    period_start: date | None
    period_end: date | None
    parse_status: str
    transaction_count: int
    error_message: str | None
    created_at: datetime


class StatementListResponse(BaseModel):
    items: list[StatementOut]
    total: int
    page: int
    page_size: int


class StatementUploadResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    filename: str
    file_type: str
    bank_name: str | None
    account_number: str | None
    period_start: date | None
    period_end: date | None
    parse_status: str
    transaction_count: int
    error_message: str | None
    transactions: list[TransactionOut] = []
