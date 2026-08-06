"""Insights endpoints: summaries, trends, breakdowns, recurring spend, risk."""
from typing import Annotated

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db import get_db
from app.models.user import User
from app.schemas.insights import (
    CategoryBreakdownResponse,
    InsightsSummary,
    MonthlyTrendsResponse,
    RecurringListResponse,
    RiskProfile,
)
from app.services.insights_service import InsightsService, current_month

router = APIRouter(prefix="/insights", tags=["insights"])

DEFAULT_MONTH_PATTERN = r"^\d{4}-\d{2}$"


@router.get("/summary", response_model=InsightsSummary)
def get_summary(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    month: str | None = Query(None, pattern=DEFAULT_MONTH_PATTERN),
) -> InsightsSummary:
    return InsightsService(db).summary(current_user.id, month or current_month())


@router.get("/monthly-trends", response_model=MonthlyTrendsResponse)
def get_monthly_trends(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    months: int = Query(6, ge=1, le=24),
) -> MonthlyTrendsResponse:
    return InsightsService(db).monthly_trends(current_user.id, months)


@router.get("/category-breakdown", response_model=CategoryBreakdownResponse)
def get_category_breakdown(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
    month: str | None = Query(None, pattern=DEFAULT_MONTH_PATTERN),
) -> CategoryBreakdownResponse:
    return InsightsService(db).category_breakdown(current_user.id, month or current_month())


@router.get("/recurring", response_model=RecurringListResponse)
def get_recurring(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> RecurringListResponse:
    return InsightsService(db).recurring(current_user.id)


@router.get("/risk-profile", response_model=RiskProfile)
def get_risk_profile(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> RiskProfile:
    return InsightsService(db).risk_profile(current_user.id)
