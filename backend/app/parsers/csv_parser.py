"""CSV bank statement parser with column auto-detection."""
import csv
import io
import logging
from datetime import date

from app.domain.entities import ParsedStatement, RawTransaction, TransactionType
from app.parsers.base import BaseStatementParser

logger = logging.getLogger(__name__)

_DATE_KEYS = ("date", "txn date", "transaction date", "value date", "posting date", "posted date")
_DESC_KEYS = (
    "description",
    "narration",
    "particulars",
    "details",
    "transaction details",
    "remarks",
    "memo",
    "transaction",
    "reference no",
    "reference",
)
_DEBIT_KEYS = ("debit", "withdrawal", "debit amount", "withdrawals", "dr", "amount paid")
_CREDIT_KEYS = ("credit", "deposit", "credit amount", "deposits", "cr", "amount received")
_AMOUNT_KEYS = ("amount", "transaction amount", "txn amount", "value")
_BALANCE_KEYS = ("balance", "closing balance", "running balance", "available balance")
_TYPE_KEYS = ("type", "transaction type", "txn type")


class CsvStatementParser(BaseStatementParser):
    def parse(self, content: bytes, filename: str) -> ParsedStatement:
        text = content.decode("utf-8-sig", errors="replace")
        delimiter = self._detect_delimiter(text)
        reader = csv.reader(io.StringIO(text), delimiter=delimiter)

        rows = [row for row in reader if row and any(cell.strip() for cell in row)]
        if not rows:
            raise ValueError("CSV file is empty")

        header_idx, header = self._find_header(rows)
        if header_idx is None:
            raise ValueError("Could not locate a CSV header row with recognizable columns")

        cols = self._map_columns(header)
        logger.info("CSV column mapping: %s", cols)

        statement = ParsedStatement(bank_name=None, account_number=None, period_start=None, period_end=None)
        statement.bank_name = self.detect_bank(text)

        dates: list[date] = []
        for row in rows[header_idx + 1 :]:
            txn = self._row_to_transaction(row, cols)
            if txn is None:
                continue
            statement.transactions.append(txn)
            dates.append(txn.date)

        if dates:
            statement.period_start = min(dates)
            statement.period_end = max(dates)

        if not statement.transactions:
            raise ValueError("No transactions could be extracted from the CSV")

        return statement

    @staticmethod
    def _detect_delimiter(text: str) -> str:
        sample = "\n".join(text.splitlines()[:10])
        candidates = {",": sample.count(","), ";": sample.count(";"), "\t": sample.count("\t")}
        return max(candidates, key=candidates.get)

    def _find_header(self, rows: list[list[str]]) -> tuple[int | None, list[str]]:
        best_score = 0
        best_idx: int | None = None
        best_header: list[str] = []
        for idx, row in enumerate(rows[:20]):
            lowered = [cell.strip().lower() for cell in row]
            score = sum(
                1
                for cell in lowered
                if any(k in cell for k in (*_DATE_KEYS, *_DESC_KEYS, *_DEBIT_KEYS, *_CREDIT_KEYS, *_BALANCE_KEYS))
            )
            if score > best_score:
                best_score = score
                best_idx = idx
                best_header = row
        if best_idx is not None and best_score >= 2:
            return best_idx, best_header
        return None, []

    def _map_columns(self, header: list[str]) -> dict[str, int]:
        cols: dict[str, int] = {}
        for idx, cell in enumerate(header):
            key = cell.strip().lower()
            if key in cols:
                continue
            if any(k in key for k in _DATE_KEYS) and "date" not in cols:
                cols["date"] = idx
            elif any(k in key for k in _DESC_KEYS) and "description" not in cols:
                cols["description"] = idx
            elif any(k in key for k in _DEBIT_KEYS) and "debit" not in cols:
                cols["debit"] = idx
            elif any(k in key for k in _CREDIT_KEYS) and "credit" not in cols:
                cols["credit"] = idx
            elif any(k in key for k in _BALANCE_KEYS) and "balance" not in cols:
                cols["balance"] = idx
            elif any(k in key for k in _AMOUNT_KEYS) and "amount" not in cols:
                cols["amount"] = idx
            elif any(k in key for k in _TYPE_KEYS) and "type" not in cols:
                cols["type"] = idx

        if "date" not in cols or "description" not in cols:
            raise ValueError("CSV must contain recognizable date and description columns")
        if "amount" not in cols and "debit" not in cols and "credit" not in cols:
            raise ValueError("CSV must contain an amount, debit or credit column")
        return cols

    def _row_to_transaction(self, row: list[str], cols: dict[str, int]) -> RawTransaction | None:
        def cell(key: str) -> str:
            idx = cols.get(key)
            if idx is None or idx >= len(row):
                return ""
            return row[idx].strip()

        txn_date = self.parse_date(cell("date"))
        description = cell("description")
        if txn_date is None or not description:
            return None

        debit = self.clean_amount(cell("debit"))
        credit = self.clean_amount(cell("credit"))
        amount = self.clean_amount(cell("amount"))
        type_hint = cell("type").lower()

        if debit is not None:
            txn_type = TransactionType.DEBIT
            amount = debit
        elif credit is not None:
            txn_type = TransactionType.CREDIT
            amount = credit
        elif amount is not None:
            if "cr" in type_hint or "credit" in type_hint or "deposit" in type_hint:
                txn_type = TransactionType.CREDIT
            elif "dr" in type_hint or "debit" in type_hint or "withdrawal" in type_hint:
                txn_type = TransactionType.DEBIT
            else:
                raw_amount = row[cols["amount"]].strip()
                txn_type = (
                    TransactionType.DEBIT
                    if raw_amount.startswith("-") or raw_amount.startswith("(")
                    else TransactionType.CREDIT
                )
        else:
            return None

        balance = self.clean_amount(cell("balance"))
        return RawTransaction(
            date=txn_date,
            description=description,
            amount=amount,
            transaction_type=txn_type,
            balance=balance,
            raw_line=",".join(cell for cell in row if cell),
        )
