"""RiskQuestionnaire ORM model: a user's stored risk-tolerance answers."""
import json
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class RiskQuestionnaire(Base):
    __tablename__ = "risk_questionnaires"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False, unique=True
    )
    answers: Mapped[str] = mapped_column(Text, nullable=False)
    tolerance_score: Mapped[int] = mapped_column(Integer, nullable=False)
    tolerance_band: Mapped[str] = mapped_column(String(16), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False
    )

    def answers_dict(self) -> dict[str, str]:
        """Deserialize the stored answers (question id -> selected option id)."""
        return json.loads(self.answers)