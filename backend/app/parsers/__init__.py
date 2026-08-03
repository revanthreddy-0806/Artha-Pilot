"""Statement parser dispatch by file type."""
import logging

from app.domain.entities import ParsedStatement
from app.parsers.base import StatementParser
from app.parsers.csv_parser import CsvStatementParser
from app.parsers.pdf_parser import PdfStatementParser

logger = logging.getLogger(__name__)

_PARSERS: dict[str, StatementParser] = {
    "csv": CsvStatementParser(),
    "pdf": PdfStatementParser(),
}


def get_parser(file_type: str) -> StatementParser:
    parser = _PARSERS.get(file_type.lower())
    if parser is None:
        raise ValueError(f"Unsupported file type: {file_type}. Supported: csv, pdf")
    return parser


def parse_statement(content: bytes, filename: str, file_type: str) -> ParsedStatement:
    parser = get_parser(file_type)
    try:
        result = parser.parse(content, filename)
    except ValueError:
        raise
    except Exception as exc:
        logger.exception("Unexpected parsing error for %s", filename)
        raise ValueError(f"Failed to parse statement: {exc}") from exc
    return result
