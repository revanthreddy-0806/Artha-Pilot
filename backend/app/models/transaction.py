"""Transaction ORM model."""
from datetime import date, datetime
from enum import Enum

from sqlalchemy import Date, DateTime, Enum as SqlEnum, Float, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


class TransactionType(str, Enum):
    CREDIT = "credit"
    DEBIT = "debit"


class Transaction(Base):
    __tablename__ = "transactions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    statement_id: Mapped[int] = mapped_column(
        ForeignKey("statements.id", ondelete="CASCADE"), index=True, nullable=False
    )
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    date: Mapped[date] = mapped_column(Date, index=True, nullable=False)
    description: Mapped[str] = mapped_column(String(500), nullable=False)
    amount: Mapped[float] = mapped_column(Float, nullable=False)
    transaction_type: Mapped[TransactionType] = mapped_column(
        SqlEnum(TransactionType, name="transaction_type"), nullable=False
    )
    balance: Mapped[float | None] = mapped_column(Float)
    category: Mapped[str] = mapped_column(String(60), index=True, default="Uncategorized", nullable=False)
    confidence: Mapped[float | None] = mapped_column(Float)
    is_recurring: Mapped[bool] = mapped_column(default=False, nullable=False)
    raw_line: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    statement = relationship("Statement", back_populates="transactions")
