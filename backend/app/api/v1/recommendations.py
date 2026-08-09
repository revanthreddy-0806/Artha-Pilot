"""Recommendation endpoints: allocation suggestions and the what-if simulator."""
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.core.errors import AppError
from app.db import get_db
from app.models.user import User
from app.repositories.transaction_repository import TransactionRepository
from app.schemas.recommendations import (
    RecommendationResponse,
    WhatIfRequest,
    WhatIfResponse,
)
from app.services.insights_service import InsightsService
from app.services.market_data_service import MarketDataService
from app.services.recommendation_service import (
    EDUCATIONAL_DISCLAIMER,
    build_recommendation,
    simulate_expense_reduction,
)

router = APIRouter(prefix="/recommendations", tags=["recommendations"])


@router.get("", response_model=RecommendationResponse)
def get_recommendations(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> RecommendationResponse:
    """Rule-based equity/debt/gold allocation with a why-explanation."""
    assessment = InsightsService(db).risk_assessment(current_user.id)
    transactions = TransactionRepository(db).all_for_user(current_user.id)
    quotes = MarketDataService(db).quotes()
    return RecommendationResponse(**build_recommendation(assessment, transactions, quotes))


@router.post("/what-if", response_model=WhatIfResponse)
def post_what_if(
    payload: WhatIfRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> WhatIfResponse:
    """Simulate cutting a category by a monthly amount (data never mutated)."""
    transactions = TransactionRepository(db).all_for_user(current_user.id)
    try:
        result = simulate_expense_reduction(
            transactions,
            payload.category,
            payload.monthly_reduction,
        )
    except ValueError as exc:
        raise AppError(str(exc), status_code=422, error_code="invalid_scenario") from None
    result["educational_disclaimer"] = EDUCATIONAL_DISCLAIMER
    return WhatIfResponse(**result)