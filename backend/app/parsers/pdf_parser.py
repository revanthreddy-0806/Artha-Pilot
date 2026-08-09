"""PDF bank statement parser.

Extracts text via pypdf, then parses transaction lines with a
marker/balance-inference heuristic that covers common Indian bank
formats (HDFC, ICICI, SBI, generic).
"""
import logging
import re
from datetime import date

from pypdf import PdfReader

from app.domain.entities import ParsedStatement, RawTransaction, TransactionType
from app.parsers.base import BaseStatementParser

logger = logging.getLogger(__name__)

_AMOUNT_RE = re.compile(r"(?<![\d,.])\d+(?:,\d{3})*\.\d{2}")
_MARKER_RE = re.compile(r"(?<![\d,.])(\d+(?:,\d{3})*\.\d{2})\s*(DR|CR|DEBIT|CREDIT|DB|CD)", re.IGNORECASE)
_MARKED_AMOUNT_RE = re.compile(
    r"(?<![\d,.])(\d+(?:,\d{3})*\.\d{2})\s*(DR|CR|DEBIT|CREDIT|DB|CD)\b", re.IGNORECASE
)
_DATE_PREFIX_RE = re.compile(
    r"^(\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}|\d{4}-\d{2}-\d{2}|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4})(?:\s+(.*))?$"
)
_NOISE_RE = re.compile(
    r"^(page|statement|account|branch|ifsc|micr|address|swift|customer|tel\b|email|"
    r"generated|period|from|date of|closing|opening|no\.?\s+of|summary|total|balance as|"
    r"narration|continued|contd|currency|initial|minimum|average|details of|cheque|"
    r"please|any time|this is a system|confidential|do not|disclaimer|synthetic|"
    r"fictional|for software testing)", re.IGNORECASE,
)

_TYPE_WORD_RE = re.compile(r"^(DEBIT|DR|DB|CREDIT|CR|CD)$", re.IGNORECASE)
_DEBIT_WORD_RE = re.compile(r"\b(?:DEBIT|DR|DB)\b", re.IGNORECASE)
_CREDIT_WORD_RE = re.compile(r"\b(?:CREDIT|CR|CD)\b", re.IGNORECASE)


class PdfStatementParser(BaseStatementParser):
    def parse(self, content: bytes, filename: str) -> ParsedStatement:
        try:
            reader = PdfReader(io_bytes(content))
        except Exception as exc:
            raise ValueError(f"Could not read PDF file: {exc}") from exc

        lines: list[str] = []
        for page in reader.pages:
            try:
                text = page.extract_text() or ""
            except Exception as exc:
                logger.warning("Page text extraction failed: %s", exc)
                continue
            for raw_line in text.splitlines():
                line = " ".join(raw_line.split())
                if line:
                    lines.append(line)

        full_text = "\n".join(lines)
        statement = ParsedStatement(bank_name=None, account_number=None, period_start=None, period_end=None)
        statement.bank_name = self.detect_bank(full_text)

        if not lines:
            raise ValueError("No extractable text found in PDF (scanned/image PDFs are not supported)")

        statement.transactions = self._extract_transactions(lines)
        if statement.transactions:
            statement.period_start = min(t.date for t in statement.transactions)
            statement.period_end = max(t.date for t in statement.transactions)
        else:
            raise ValueError("No transactions could be extracted from the PDF")

        return statement

    def _extract_transactions(self, lines: list[str]) -> list[RawTransaction]:
        transactions: list[RawTransaction] = []
        prev_balance: float | None = None
        pending_date: date | None = None
        pending_description: str | None = None

        def flush() -> None:
            nonlocal pending_date, pending_description, prev_balance
            if pending_date is None:
                return
            txn = self._build_transaction(pending_date, pending_description or "", prev_balance)
            if txn is not None:
                transactions.append(txn)
                if txn.balance is not None:
                    prev_balance = txn.balance
            pending_date = None
            pending_description = None

        for line in lines:
            match = _DATE_PREFIX_RE.match(line)
            if match:
                txn_date = self.parse_date(match.group(1))
                if txn_date is None:
                    continue
                rest = match.group(2) or ""
                txn = self._build_transaction(txn_date, rest, prev_balance)
                if txn is None:
                    flush()
                    pending_date = txn_date
                    pending_description = rest
                    continue
                flush()
                transactions.append(txn)
                if txn.balance is not None:
                    prev_balance = txn.balance
                continue

            if pending_date is not None and (not _NOISE_RE.match(line) or _TYPE_WORD_RE.match(line)):
                if pending_description:
                    pending_description = f"{pending_description} {line}"
                else:
                    pending_description = line

        flush()
        return transactions

    def _build_transaction(
        self,
        txn_date: date,
        rest: str,
        prev_balance: float | None,
    ) -> RawTransaction | None:
        amounts = [self._to_float(a) for a in _AMOUNT_RE.findall(rest)]
        if not amounts:
            return None

        marker = _MARKER_RE.search(rest)
        balance: float | None = None
        txn_type: TransactionType | None = None
        amount: float | None = None

        if marker:
            amount = self._to_float(marker.group(1))
            txn_type = TransactionType.CREDIT if marker.group(2).upper().startswith(("CR", "CD")) else TransactionType.DEBIT
            if len(amounts) >= 2:
                balance = amounts[-1]

        elif len(amounts) >= 3:
            debit, credit, balance = amounts[0], amounts[1], amounts[2]
            if debit and debit > 0:
                txn_type = TransactionType.DEBIT
                amount = debit
            elif credit and credit > 0:
                txn_type = TransactionType.CREDIT
                amount = credit

        elif len(amounts) >= 2:
            debit_match = _DEBIT_WORD_RE.search(rest)
            credit_match = _CREDIT_WORD_RE.search(rest)
            if debit_match is not None or credit_match is not None:
                if debit_match is not None and (credit_match is None or debit_match.start() < credit_match.start()):
                    txn_type = TransactionType.DEBIT
                else:
                    txn_type = TransactionType.CREDIT
                amount = amounts[0]
                balance = amounts[-1]

        elif len(amounts) == 2 and prev_balance is not None:
            amount, balance = amounts[0], amounts[1]
            delta = prev_balance - balance
            if abs(delta) < 0.005:
                return None
            txn_type = TransactionType.DEBIT if delta > 0 else TransactionType.CREDIT
            amount = abs(delta)

        elif len(amounts) == 1 and prev_balance is not None:
            balance = amounts[0]
            delta = prev_balance - balance
            if abs(delta) < 0.005:
                return None
            txn_type = TransactionType.DEBIT if delta > 0 else TransactionType.CREDIT
            amount = abs(delta)

        if txn_type is None or amount is None:
            return None

        description = self._clean_description(rest, amounts)
        if not description:
            return None

        return RawTransaction(
            date=txn_date,
            description=description,
            amount=round(amount, 2),
            transaction_type=txn_type,
            balance=round(balance, 2) if balance is not None else None,
            raw_line=rest,
        )

    @staticmethod
    def _clean_description(rest: str, amounts: list[float | None]) -> str:
        cleaned = _MARKED_AMOUNT_RE.sub("", rest)
        cleaned = _AMOUNT_RE.sub("", cleaned)
        cleaned = " ".join(cleaned.split())
        cleaned = cleaned.strip(" -|·•")
        return cleaned[:500]

    @staticmethod
    def _to_float(value: str) -> float | None:
        try:
            number = float(value.replace(",", ""))
        except ValueError:
            return None
        return round(number, 2) if number != 0 else 0.0


def io_bytes(content: bytes):
    import io

    return io.BytesIO(content)
