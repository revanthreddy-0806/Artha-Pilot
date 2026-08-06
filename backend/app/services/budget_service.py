"""Budget management business logic."""
import logging

from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.ml.categories import CATEGORIES
from app.models.budget import Budget
from app.repositories.budget_repository import BudgetRepository
from app.repositories.transaction_repository import TransactionRepository
from app.schemas.budget import BudgetOut
from app.services.insights_service import _expense_by_category, _month_bounds, current_month

logger = logging.getLogger(__name__)


class BudgetService:
    def __init__(self, db: Session) -> None:
        self._budgets = BudgetRepository(db)
        self._transactions = TransactionRepository(db)

    def _spent_by_category(self, user_id: int, month: str) -> dict[str, float]:
        start, end = _month_bounds(month)
        txns = self._transactions.all_for_user(user_id, start_date=start, end_date=end)
        return _expense_by_category(txns)

    @staticmethod
    def _to_out(budget: Budget, spent: float) -> BudgetOut:
        return BudgetOut(
            category=budget.category,
            monthly_limit=budget.monthly_limit,
            spent=round(spent, 2),
            percentage=round(spent / budget.monthly_limit * 100, 1),
            overrun=spent > budget.monthly_limit,
        )

    def set_budget(self, user_id: int, category: str, monthly_limit: float) -> BudgetOut:
        if category not in CATEGORIES:
            raise AppError(
                f"Invalid category. Allowed: {', '.join(CATEGORIES)}",
                status_code=422,
                error_code="invalid_category",
            )
        budget = self._budgets.upsert(user_id, category, monthly_limit)
        spent = self._spent_by_category(user_id, current_month()).get(category, 0.0)
        logger.info("User %s set budget %s=%.2f", user_id, category, monthly_limit)
        return self._to_out(budget, spent)

    def delete_budget(self, user_id: int, category: str) -> None:
        if not self._budgets.delete(user_id, category):
            raise AppError(
                f"No budget set for category '{category}'",
                status_code=404,
                error_code="budget_not_found",
            )
        logger.info("User %s removed budget for %s", user_id, category)
