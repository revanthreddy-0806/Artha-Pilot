"""Parser contracts and shared helpers."""
from abc import ABC, abstractmethod
from datetime import date, datetime
import logging
import re
from typing import Protocol

from app.domain.entities import ParsedStatement

logger = logging.getLogger(__name__)

_DATE_FORMATS = [
    "%d-%m-%Y",
    "%d/%m/%Y",
    "%d-%m-%y",
    "%d/%m/%y",
    "%Y-%m-%d",
    "%d %b %Y",
    "%d %B %Y",
    "%b %d, %Y",
    "%d-%b-%Y",
    "%d-%b-%y",
    "%d %b %y",
    "%d.%m.%Y",
    "%d/%b/%Y",
]

class StatementParser(Protocol):
    def parse(self, content: bytes, filename: str) -> ParsedStatement: ...


class BaseStatementParser(ABC):
    @abstractmethod
    def parse(self, content: bytes, filename: str) -> ParsedStatement: ...

    @staticmethod
    def clean_amount(value: str) -> float | None:
        """Parse a numeric amount like '1,234.56', '₹500', '(200)' or '-200'."""
        if value is None:
            return None
        text = str(value).strip().replace(",", "")
        text = re.sub(r"[^0-9.+\-]", "", text)
        text = text.lstrip(".+-")
        if not text or text in {".", "-", "+"}:
            return None
        if text.count(".") > 1:
            decimal_idx = text.rfind(".")
            text = text[:decimal_idx].replace(".", "") + text[decimal_idx:]
        try:
            number = float(text)
        except ValueError:
            return None
        return abs(number)

    @staticmethod
    def parse_date(value: str) -> date | None:
        """Try to parse a date string across common Indian/ISO formats."""
        text = str(value).strip()
        if not text:
            return None
        for fmt in _DATE_FORMATS:
            try:
                return datetime.strptime(text, fmt).date()
            except ValueError:
                continue
        return None

    @staticmethod
    def detect_bank(text: str) -> str | None:
        upper = text.upper()
        for bank in [
            "HDFC BANK",
            "ICICI BANK",
            "STATE BANK OF INDIA",
            "AXIS BANK",
            "KOTAK MAHINDRA BANK",
            "YES BANK",
            "CANARA BANK",
            "PUNJAB NATIONAL BANK",
            "IDFC FIRST BANK",
            "INDIAN BANK",
            "BANK OF BARODA",
            "UNION BANK OF INDIA",
        ]:
            if bank in upper:
                return bank
        return None
