"""Repository pattern: persistence access for Statement aggregate."""
from datetime import date

from sqlalchemy.orm import Session

from app.domain.entities import ParseStatus
from app.models.statement import Statement


class StatementRepository:
    def __init__(self, db: Session) -> None:
        self._db = db

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
