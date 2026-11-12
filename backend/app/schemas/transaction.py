"""Transaction-related Pydantic schemas."""
from datetime import date as Date

from pydantic import BaseModel, ConfigDict, Field


class TransactionOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    statement_id: int | None
    date: Date
    description: str
    amount: float
    transaction_type: str
    balance: float | None
    category: str
    confidence: float | None
    is_recurring: bool


class TransactionCreate(BaseModel):
    transaction_type: str = Field(min_length=1, max_length=10)
    amount: float = Field(gt=0)
    date: Date
    description: str = Field(min_length=1, max_length=500)
    category: str = Field(min_length=1, max_length=60)
    balance: float | None = None


class TransactionUpdate(BaseModel):
    transaction_type: str | None = Field(default=None, min_length=1, max_length=10)
    amount: float | None = Field(default=None, gt=0)
    date: Date | None = None
    description: str | None = Field(default=None, min_length=1, max_length=500)
    category: str | None = Field(default=None, min_length=1, max_length=60)
    balance: float | None = None


class TransactionPage(BaseModel):
    items: list[TransactionOut]
    total: int
    page: int
    page_size: int
