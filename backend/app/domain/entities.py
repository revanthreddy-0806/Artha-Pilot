"""Pure domain entities, independent of persistence and transport."""
from dataclasses import dataclass, field
from datetime import date, datetime
from enum import Enum


class TransactionType(str, Enum):
    CREDIT = "credit"
    DEBIT = "debit"


class ParseStatus(str, Enum):
    PENDING = "pending"
    PROCESSED = "processed"
    FAILED = "failed"


@dataclass(slots=True)
class User:
    id: int
    email: str
    full_name: str
    is_active: bool = True
    created_at: datetime = field(default_factory=datetime.utcnow)


@dataclass(slots=True)
class RawTransaction:
    """A transaction extracted from a statement, before categorization."""

    date: date
    description: str
    amount: float
    transaction_type: TransactionType
    balance: float | None = None
    raw_line: str = ""


@dataclass(slots=True)
class ParsedStatement:
    """Result of parsing an uploaded statement file."""

    bank_name: str | None
    account_number: str | None
    period_start: date | None
    period_end: date | None
    transactions: list[RawTransaction] = field(default_factory=list)
