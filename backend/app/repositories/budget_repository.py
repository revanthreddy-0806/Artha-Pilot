"""Repository pattern: persistence access for the Budget aggregate."""
from sqlalchemy import delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.budget import Budget


class BudgetRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def get(self, user_id: int, category: str) -> Budget | None:
        return self._db.scalar(
            select(Budget).where(Budget.user_id == user_id, Budget.category == category)
        )

    def list_for_user(self, user_id: int) -> list[Budget]:
        return list(
            self._db.scalars(
                select(Budget).where(Budget.user_id == user_id).order_by(Budget.category.asc())
            )
        )

    def upsert(self, user_id: int, category: str, monthly_limit: float) -> Budget:
        budget = self.get(user_id, category)
        if budget is None:
            budget = Budget(user_id=user_id, category=category, monthly_limit=monthly_limit)
            self._db.add(budget)
            try:
                self._db.commit()
            except IntegrityError:
                # A concurrent upsert of the same key won the race; converge on it.
                self._db.rollback()
                budget = self.get(user_id, category)
                if budget is None:  # pragma: no cover - defensive
                    raise
            self._db.refresh(budget)
        else:
            budget.monthly_limit = monthly_limit
            self._db.commit()
            self._db.refresh(budget)
        return budget

    def delete(self, user_id: int, category: str) -> bool:
        result = self._db.execute(
            delete(Budget).where(Budget.user_id == user_id, Budget.category == category)
        )
        self._db.commit()
        return result.rowcount > 0
