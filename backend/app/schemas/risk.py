"""Risk-appetite questionnaire and combined risk-assessment schemas."""
from pydantic import BaseModel

from app.schemas.insights import RiskFactor


class QuestionOption(BaseModel):
    id: str
    label: str


class RiskQuestion(BaseModel):
    id: str
    text: str
    options: list[QuestionOption]


class QuestionListResponse(BaseModel):
    questions: list[RiskQuestion]


class RiskQuestionnaireSubmit(BaseModel):
    answers: dict[str, str]


class RiskAssessment(BaseModel):
    """Combined risk-appetite assessment (questionnaire + spending behaviour)."""

    score: int  # 0-100 combined risk appetite
    band: str  # "Conservative" | "Moderate" | "Aggressive"
    summary: str
    factors: list[RiskFactor]
    tolerance_score: int | None
    tolerance_band: str | None
    behavior_score: int
    questionnaire_completed: bool
    income: float | None
    monthly_expenses: float
    savings_rate: float | None
    emi_load_ratio: float | None
    safe_emi_amount: float
    max_loan_amount: float | None
    max_loan_tenure_months: int


class RiskQuestionnaireResult(BaseModel):
    tolerance_score: int
    tolerance_band: str
    assessment: RiskAssessment