"""Repository pattern: persistence access for Statement aggregate."""
from datetime import date

from sqlalchemy import func, select
from sqlalchemy.orm import Session, selectinload

from app.domain.entities import ParseStatus
from app.models.statement import Statement


class StatementRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

    def list_for_user(
        self,
        user_id: int,
        *,
        page: int = 1,
        page_size: int = 50,
    ) -> tuple[list[Statement], int]:
        base = select(Statement).where(Statement.user_id == user_id)
        total = self._db.scalar(select(func.count()).select_from(Statement).where(Statement.user_id == user_id)) or 0
        rows = list(
            self._db.scalars(
                base.order_by(Statement.created_at.desc(), Statement.id.desc())
                .offset((page - 1) * page_size)
                .limit(page_size)
            )
        )
        return rows, total

    def processed_with_transactions(self, user_id: int) -> list[Statement]:
        """Processed statements and their rows, used for duplicate detection."""
        return list(
            self._db.scalars(
                select(Statement)
                .where(
                    Statement.user_id == user_id,
                    Statement.parse_status == ParseStatus.PROCESSED,
                )
                .options(selectinload(Statement.transactions))
                .order_by(Statement.id.desc())
            )
        )

    def create(
        self,
        user_id: int,
        filename: str,
        file_type: str,
    ) -> Statement:
        statement = Statement(user_id=user_id, filename=filename, file_type=file_type)
        self._db.add(statement)
        self._db.flush()
        return statement

    def mark_processed(
        self,
        statement: Statement,
        *,
        bank_name: str | None,
        account_number: str | None,
        period_start: date | None,
        period_end: date | None,
        transaction_count: int,
    ) -> None:
        statement.bank_name = bank_name
        statement.account_number = account_number
        statement.period_start = period_start
        statement.period_end = period_end
        statement.transaction_count = transaction_count
        statement.parse_status = ParseStatus.PROCESSED
        statement.error_message = None

    def mark_failed(self, statement: Statement, error_message: str) -> None:
        statement.parse_status = ParseStatus.FAILED
        statement.error_message = error_message[:2000]

    def commit(self) -> None:
        self._db.commit()
