"""Repository pattern: persistence access for the Transaction aggregate."""
from datetime import date

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.transaction import Transaction, TransactionType


class TransactionRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def bulk_create(
        self,
        rows: list[Transaction],
    ) -> int:
        if not rows:
            return 0
        self._db.add_all(rows)
        self._db.commit()
        return len(rows)

    def list_for_user(
        self,
        user_id: int,
        *,
        page: int = 1,
        page_size: int = 50,
        category: str | None = None,
        transaction_type: TransactionType | None = None,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> tuple[list[Transaction], int]:
        query = select(Transaction).where(Transaction.user_id == user_id)
        count_query = select(func.count()).select_from(Transaction).where(Transaction.user_id == user_id)

        if category:
            query = query.where(Transaction.category == category)
            count_query = count_query.where(Transaction.category == category)
        if transaction_type:
            query = query.where(Transaction.transaction_type == transaction_type)
            count_query = count_query.where(Transaction.transaction_type == transaction_type)
        if start_date:
            query = query.where(Transaction.date >= start_date)
            count_query = count_query.where(Transaction.date >= start_date)
        if end_date:
            query = query.where(Transaction.date <= end_date)
            count_query = count_query.where(Transaction.date <= end_date)

        total = self._db.scalar(count_query) or 0
        rows = list(
            self._db.scalars(
                query.order_by(Transaction.date.desc(), Transaction.id.desc())
                .offset((page - 1) * page_size)
                .limit(page_size)
            )
        )
        return rows, total

    def get_for_user(self, transaction_id: int, user_id: int) -> Transaction | None:
        return self._db.scalar(
            select(Transaction).where(
                Transaction.id == transaction_id, Transaction.user_id == user_id
            )
        )

    def update_category(self, transaction: Transaction, category: str) -> Transaction:
        transaction.category = category
        self._db.commit()
        self._db.refresh(transaction)
        return transaction
