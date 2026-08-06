"""Budget endpoints."""
from typing import Annotated

from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db import get_db
from app.models.user import User
from app.schemas.budget import BudgetListResponse, BudgetOut, BudgetUpsert
from app.services.budget_service import BudgetService
from app.services.insights_service import InsightsService, current_month

router = APIRouter(prefix="/budgets", tags=["budgets"])


@router.get("", response_model=BudgetListResponse)
def list_budgets(
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> BudgetListResponse:
    return InsightsService(db).budget_status(current_user.id, current_month())


@router.put("/{category}", response_model=BudgetOut)
def set_budget(
    category: str,
    payload: BudgetUpsert,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> BudgetOut:
    return BudgetService(db).set_budget(current_user.id, category, payload.monthly_limit)


@router.delete("/{category}", status_code=status.HTTP_204_NO_CONTENT)
def delete_budget(
    category: str,
    current_user: Annotated[User, Depends(get_current_user)],
    db: Annotated[Session, Depends(get_db)],
) -> None:
    BudgetService(db).delete_budget(current_user.id, category)