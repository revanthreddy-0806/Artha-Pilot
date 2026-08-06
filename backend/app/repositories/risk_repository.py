"""Repository pattern: persistence access for the RiskQuestionnaire aggregate."""
import json

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.risk_questionnaire import RiskQuestionnaire


class RiskRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def get_for_user(self, user_id: int) -> RiskQuestionnaire | None:
        return self._db.scalar(
            select(RiskQuestionnaire).where(RiskQuestionnaire.user_id == user_id)
        )

    def upsert(
        self,
        user_id: int,
        answers: dict[str, str],
        tolerance_score: int,
        tolerance_band: str,
    ) -> RiskQuestionnaire:
        serialized = json.dumps(answers, sort_keys=True)
        row = self.get_for_user(user_id)
        if row is None:
            row = RiskQuestionnaire(
                user_id=user_id,
                answers=serialized,
                tolerance_score=tolerance_score,
                tolerance_band=tolerance_band,
            )
            self._db.add(row)
        else:
            row.answers = serialized
            row.tolerance_score = tolerance_score
            row.tolerance_band = tolerance_band
        self._db.commit()
        self._db.refresh(row)
        return row