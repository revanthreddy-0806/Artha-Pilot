"""Risk-appetite endpoints: questionnaire and combined risk assessment."""
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.errors import AppError
from app.db import get_db
from app.models.user import User
from app.repositories.risk_repository import RiskRepository
from app.schemas.risk import (
    QuestionListResponse,
    QuestionOption,
    RiskAssessment,
    RiskQuestion,
    RiskQuestionnaireResult,
    RiskQuestionnaireSubmit,
)
from app.services.insights_service import InsightsService
from app.services.risk_service import RISK_QUESTIONNAIRE, score_questionnaire

router = APIRouter(prefix="/risk", tags=["risk"])


@router.get("/questions", response_model=QuestionListResponse)
def get_questions() -> QuestionListResponse:
    """Standard financial risk-tolerance question set (static, public)."""
    questions = [
        RiskQuestion(
            id=q["id"],
            text=q["text"],
            options=[QuestionOption(id=o["id"], label=o["label"]) for o in q["options"]],
        )
        for q in RISK_QUESTIONNAIRE
    ]
    return QuestionListResponse(questions=questions)


@router.put("/questionnaire", response_model=RiskQuestionnaireResult)
def submit_questionnaire(
    payload: RiskQuestionnaireSubmit,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> RiskQuestionnaireResult:
    """Store answers and recompute the combined risk assessment."""
    try:
        tolerance_score, tolerance_band = score_questionnaire(payload.answers)
    except ValueError as exc:
        raise AppError(
            str(exc),
            status_code=422,
            error_code="invalid_questionnaire",
        ) from None

    RiskRepository(db).upsert(
        current_user.id,
        payload.answers,
        tolerance_score,
        tolerance_band,
    )
    assessment = InsightsService(db).risk_assessment(current_user.id)
    return RiskQuestionnaireResult(
        tolerance_score=tolerance_score,
        tolerance_band=tolerance_band,
        assessment=assessment,
    )


@router.get("/profile", response_model=RiskAssessment)
def get_profile(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> RiskAssessment:
    """Combined risk appetite: questionnaire answers + spending behaviour."""
    return InsightsService(db).risk_assessment(current_user.id)