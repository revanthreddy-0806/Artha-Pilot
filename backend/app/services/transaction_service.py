"""Transaction query and correction business logic."""
import logging
from datetime import date

from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.ml.categories import CATEGORIES
from app.models.transaction import Transaction, TransactionType
from app.repositories.transaction_repository import TransactionRepository

logger = logging.getLogger(__name__)


class TransactionService:
    def __init__(self, db: Session) -> None:
        self._transactions = TransactionRepository(db)

    def list_transactions(
        self,
        user_id: int,
        page: int,
        page_size: int,
        category: str | None,
        transaction_type: str | None,
        start_date: date | None,
        end_date: date | None,
    ) -> tuple[list[Transaction], int]:
        txn_type: TransactionType | None = None
        if transaction_type:
            try:
                txn_type = TransactionType(transaction_type.lower())
            except ValueError:
                raise AppError(
                    "transaction_type must be 'credit' or 'debit'",
                    status_code=422,
                    error_code="invalid_transaction_type",
                ) from None
        return self._transactions.list_for_user(
            user_id,
            page=page,
            page_size=min(page_size, 100),
            category=category,
            transaction_type=txn_type,
            start_date=start_date,
            end_date=end_date,
        )

    def update_category(self, user_id: int, transaction_id: int, category: str) -> Transaction:
        if category not in CATEGORIES:
            raise AppError(
                f"Invalid category. Allowed: {', '.join(CATEGORIES)}",
                status_code=422,
                error_code="invalid_category",
            )
        transaction = self._transactions.get_for_user(transaction_id, user_id)
        if transaction is None:
            raise AppError("Transaction not found", status_code=404, error_code="transaction_not_found")
        updated = self._transactions.update_category(transaction, category)
        logger.info("User %s re-categorized transaction %s to %s", user_id, transaction_id, category)
        return updated
