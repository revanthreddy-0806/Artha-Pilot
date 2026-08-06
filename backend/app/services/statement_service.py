"""Statement ingestion business logic: upload, parse, categorize, persist."""
import logging
import os

from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.errors import AppError
from app.domain.entities import ParsedStatement
from app.ml.classifier import categorize_many
from app.models.statement import Statement
from app.models.transaction import Transaction
from app.parsers import parse_statement
from app.repositories.statement_repository import StatementRepository
from app.repositories.transaction_repository import TransactionRepository
from app.services.recurring_detector import TransactionRef, detect_recurring, mark_recurring_transaction_ids

logger = logging.getLogger(__name__)

ALLOWED_EXTENSIONS = {".csv": "csv", ".pdf": "pdf"}
MAX_FILENAME_LENGTH = 255


class StatementService:
    def __init__(self, db: Session) -> None:
        self._statements = StatementRepository(db)
        self._transactions = TransactionRepository(db)

    def ingest(self, user_id: int, filename: str, content: bytes) -> Statement:
        file_type = self._validate_file(filename, content)
        filename = _sanitize_filename(filename)

        statement = self._statements.create(user_id, filename, file_type)
        try:
            parsed: ParsedStatement = parse_statement(content, filename, file_type)
            statement = self._persist_parsed(statement, parsed)
            logger.info(
                "Statement %s processed: %d transactions for user %s",
                statement.id, statement.transaction_count, user_id,
            )
        except ValueError as exc:
            self._statements.mark_failed(statement, str(exc))
            self._statements.commit()
            logger.warning("Statement %s failed parsing: %s", statement.id, exc)
            raise AppError(
                f"Statement could not be parsed: {exc}",
                status_code=422,
                error_code="statement_parse_failed",
            ) from exc

        return statement

    @staticmethod
    def _validate_file(filename: str, content: bytes) -> str:
        max_bytes = get_settings().max_upload_bytes
        if len(content) > max_bytes:
            raise AppError(
                f"File exceeds {max_bytes // (1024 * 1024)} MB limit",
                status_code=413,
                error_code="file_too_large",
            )
        lower = filename.lower()
        for ext, file_type in ALLOWED_EXTENSIONS.items():
            if lower.endswith(ext):
                return file_type
        raise AppError(
            "Unsupported file type. Upload a .csv or .pdf bank statement",
            status_code=400,
            error_code="unsupported_file_type",
        )

    def _persist_parsed(self, statement: Statement, parsed: ParsedStatement) -> Statement:
        descriptions = [raw.description for raw in parsed.transactions]
        categorized = categorize_many(descriptions)

        rows: list[Transaction] = []
        for raw, (category, confidence) in zip(parsed.transactions, categorized, strict=True):
            rows.append(
                Transaction(
                    statement_id=statement.id,
                    user_id=statement.user_id,
                    date=raw.date,
                    description=raw.description,
                    amount=raw.amount,
                    transaction_type=raw.transaction_type,
                    balance=raw.balance,
                    category=category,
                    confidence=confidence,
                    raw_line=raw.raw_line,
                )
            )

        self._statements.mark_processed(
            statement,
            bank_name=parsed.bank_name,
            account_number=parsed.account_number,
            period_start=parsed.period_start,
            period_end=parsed.period_end,
            transaction_count=len(rows),
        )
        self._transactions.bulk_create(rows)
        self._refresh_recurring_flags(statement.user_id)
        return statement

    def _refresh_recurring_flags(self, user_id: int) -> None:
        """Recompute is_recurring across the user's history after an ingest."""
        txns = self._transactions.all_for_user(user_id)
        refs = [
            TransactionRef(id=t.id, date=t.date, amount=t.amount, description=t.description, category=t.category)
            for t in txns
        ]
        groups = detect_recurring(refs)
        recurring_ids = mark_recurring_transaction_ids(groups)
        changed = self._transactions.set_recurring_flags(user_id, recurring_ids)
        if changed:
            logger.info("Updated is_recurring on %d transactions for user %s", changed, user_id)


def _sanitize_filename(filename: str) -> str:
    """Strip any path components and truncate to the column limit, keeping the extension."""
    clean = os.path.basename(filename.replace("\\", "/")).strip()
    if not clean:
        return "statement"
    if len(clean) <= MAX_FILENAME_LENGTH:
        return clean
    stem, ext = os.path.splitext(clean)
    return stem[: MAX_FILENAME_LENGTH - len(ext)] + ext
