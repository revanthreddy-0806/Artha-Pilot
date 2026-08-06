"""Insights, recurring detection and risk-assessment Pydantic schemas."""
from datetime import date

from pydantic import BaseModel


class CategoryAmount(BaseModel):
    category: str
    amount: float
    percentage: float


class InsightsSummary(BaseModel):
    month: str
    income: float
    expenses: float
    savings: float
    savings_rate: float | None
    transaction_count: int
    top_categories: list[CategoryAmount]


class MonthTrend(BaseModel):
    month: str
    income: float
    expenses: float
    savings: float


class MonthlyTrendsResponse(BaseModel):
    months: list[MonthTrend]


class CategoryBreakdownItem(BaseModel):
    category: str
    amount: float
    percentage: float
    transaction_count: int


class CategoryBreakdownResponse(BaseModel):
    month: str
    categories: list[CategoryBreakdownItem]


class RecurringItem(BaseModel):
    transaction_id: int
    description: str
    amount: float
    category: str
    kind: str  # "emi" | "recurring"
    interval_days: int
    occurrences: int
    confidence: float
    first_date: date
    last_date: date
    next_expected_date: date


class RecurringListResponse(BaseModel):
    items: list[RecurringItem]


class RiskFactor(BaseModel):
    name: str
    value: str
    impact: str  # "positive" | "negative" | "neutral"


class RiskProfile(BaseModel):
    score: int
    band: str  # "Poor" | "Fair" | "Good" | "Excellent"
    summary: str
    factors: list[RiskFactor]
    income: float | None
    monthly_expenses: float
    savings_rate: float | None
    emi_load_ratio: float | None
    safe_emi_amount: float
    max_loan_amount: float | None
    max_loan_tenure_months: int
