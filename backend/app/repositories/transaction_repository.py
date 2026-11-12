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
        search: str | None = None,
        min_amount: float | None = None,
        max_amount: float | None = None,
        statement_id: int | None = None,
        sort_by: str = "date",
        sort_dir: str = "desc",
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
        if search:
            term = f"%{search.strip()}%"
            query = query.where(Transaction.description.ilike(term))
            count_query = count_query.where(Transaction.description.ilike(term))
        if min_amount is not None:
            query = query.where(Transaction.amount >= min_amount)
            count_query = count_query.where(Transaction.amount >= min_amount)
        if max_amount is not None:
            query = query.where(Transaction.amount <= max_amount)
            count_query = count_query.where(Transaction.amount <= max_amount)
        if statement_id is not None:
            query = query.where(Transaction.statement_id == statement_id)
            count_query = count_query.where(Transaction.statement_id == statement_id)

        sort_column = {
            "date": Transaction.date,
            "amount": Transaction.amount,
            "description": Transaction.description,
            "category": Transaction.category,
            "created_at": Transaction.created_at,
        }.get(sort_by, Transaction.date)
        if sort_dir == "asc":
            ordering = (sort_column.asc(), Transaction.id.asc())
        else:
            ordering = (sort_column.desc(), Transaction.id.desc())

        total = self._db.scalar(count_query) or 0
        rows = list(
            self._db.scalars(
                query.order_by(*ordering)
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

    def save(self, transaction: Transaction) -> Transaction:
        self._db.add(transaction)
        self._db.commit()
        self._db.refresh(transaction)
        return transaction

    def update(self, transaction: Transaction) -> Transaction:
        self._db.commit()
        self._db.refresh(transaction)
        return transaction

    def delete(self, transaction: Transaction) -> None:
        self._db.delete(transaction)
        self._db.commit()

    def all_for_user(
        self,
        user_id: int,
        *,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> list[Transaction]:
        query = select(Transaction).where(Transaction.user_id == user_id)
        if start_date:
            query = query.where(Transaction.date >= start_date)
        if end_date:
            query = query.where(Transaction.date <= end_date)
        return list(self._db.scalars(query.order_by(Transaction.date.asc(), Transaction.id.asc())))

    def set_recurring_flags(self, user_id: int, recurring_ids: set[int]) -> int:
        """Toggle is_recurring on the user's transactions to match recurring_ids.

        Only rows whose flag actually changes are updated; returns the number
        of updated rows.
        """
        rows = self._db.scalars(select(Transaction).where(Transaction.user_id == user_id))
        updated = 0
        for row in rows:
            should_be = row.id in recurring_ids
            if row.is_recurring != should_be:
                row.is_recurring = should_be
                updated += 1
        if updated:
            self._db.commit()
        return updated
