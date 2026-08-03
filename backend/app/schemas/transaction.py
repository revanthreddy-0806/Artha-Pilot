"""Transaction-related Pydantic schemas."""
from datetime import date

from pydantic import BaseModel, ConfigDict, Field


class TransactionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    statement_id: int
    date: date
    description: str
    amount: float
    transaction_type: str
    balance: float | None
    category: str
    confidence: float | None
    is_recurring: bool


class TransactionUpdate(BaseModel):
    category: str = Field(min_length=1, max_length=60)


class TransactionPage(BaseModel):
    items: list[TransactionOut]
    total: int
    page: int
    page_size: int
