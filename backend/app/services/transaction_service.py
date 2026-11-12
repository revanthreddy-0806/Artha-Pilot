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
        search: str | None = None,
        min_amount: float | None = None,
        max_amount: float | None = None,
        statement_id: int | None = None,
        sort_by: str = "date",
        sort_dir: str = "desc",
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
        if start_date and end_date and start_date > end_date:
            raise AppError(
                "start_date must be on or before end_date",
                status_code=422,
                error_code="invalid_date_range",
            )
        if min_amount is not None and max_amount is not None and min_amount > max_amount:
            raise AppError(
                "min_amount must be less than or equal to max_amount",
                status_code=422,
                error_code="invalid_amount_range",
            )
        if sort_by not in {"date", "amount", "description", "category", "created_at"}:
            raise AppError(
                "invalid sort_by",
                status_code=422,
                error_code="invalid_sort",
            )
        if sort_dir not in {"asc", "desc"}:
            raise AppError(
                "invalid sort_dir",
                status_code=422,
                error_code="invalid_sort",
            )
        return self._transactions.list_for_user(
            user_id,
            page=page,
            page_size=min(page_size, 100),
            category=category,
            transaction_type=txn_type,
            start_date=start_date,
            end_date=end_date,
            search=search.strip() if search else None,
            min_amount=min_amount,
            max_amount=max_amount,
            statement_id=statement_id,
            sort_by=sort_by,
            sort_dir=sort_dir,
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

    def create_transaction(self, user_id: int, payload) -> Transaction:
        txn_type = self._parse_type(payload.transaction_type)
        if payload.category not in CATEGORIES:
            raise AppError(
                f"Invalid category. Allowed: {', '.join(CATEGORIES)}",
                status_code=422,
                error_code="invalid_category",
            )
        transaction = Transaction(
            statement_id=None,
            user_id=user_id,
            date=payload.date,
            description=payload.description,
            amount=payload.amount,
            transaction_type=txn_type,
            balance=payload.balance,
            category=payload.category,
            confidence=None,
            is_recurring=False,
            raw_line=None,
        )
        created = self._transactions.save(transaction)
        logger.info("User %s created manual transaction %s", user_id, created.id)
        return created

    def update_transaction(self, user_id: int, transaction_id: int, payload) -> Transaction:
        transaction = self._transactions.get_for_user(transaction_id, user_id)
        if transaction is None:
            raise AppError("Transaction not found", status_code=404, error_code="transaction_not_found")
        if payload.transaction_type is not None:
            transaction.transaction_type = self._parse_type(payload.transaction_type)
        if payload.amount is not None:
            transaction.amount = payload.amount
        if payload.date is not None:
            transaction.date = payload.date
        if payload.description is not None:
            transaction.description = payload.description
        if payload.category is not None:
            if payload.category not in CATEGORIES:
                raise AppError(
                    f"Invalid category. Allowed: {', '.join(CATEGORIES)}",
                    status_code=422,
                    error_code="invalid_category",
                )
            transaction.category = payload.category
        if "balance" in getattr(payload, "model_fields_set", set()):
            transaction.balance = payload.balance
        updated = self._transactions.update(transaction)
        logger.info("User %s updated transaction %s", user_id, transaction_id)
        return updated

    def delete_transaction(self, user_id: int, transaction_id: int) -> None:
        transaction = self._transactions.get_for_user(transaction_id, user_id)
        if transaction is None:
            raise AppError("Transaction not found", status_code=404, error_code="transaction_not_found")
        self._transactions.delete(transaction)
        logger.info("User %s deleted transaction %s", user_id, transaction_id)

    @staticmethod
    def _parse_type(value: str) -> TransactionType:
        try:
            return TransactionType(value.lower())
        except ValueError:
            raise AppError(
                "transaction_type must be 'credit' or 'debit'",
                status_code=422,
                error_code="invalid_transaction_type",
            ) from None
