"""Statement ORM model."""
from datetime import date, datetime

from sqlalchemy import Date, DateTime, Enum as SqlEnum, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base
from app.domain.entities import ParseStatus


class Statement(Base):
    __tablename__ = "statements"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False)
    filename: Mapped[str] = mapped_column(String(255), nullable=False)
    file_type: Mapped[str] = mapped_column(String(10), nullable=False)
    bank_name: Mapped[str | None] = mapped_column(String(100))
    account_number: Mapped[str | None] = mapped_column(String(50))
    period_start: Mapped[date | None] = mapped_column(Date)
    period_end: Mapped[date | None] = mapped_column(Date)
    parse_status: Mapped[ParseStatus] = mapped_column(
        SqlEnum(ParseStatus, name="parse_status"), default=ParseStatus.PENDING, nullable=False
    )
    transaction_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    error_message: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    user = relationship("User", backref="statements")
    transactions = relationship("Transaction", back_populates="statement", cascade="all, delete-orphan")
