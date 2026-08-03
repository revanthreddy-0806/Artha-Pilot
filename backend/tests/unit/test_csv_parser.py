"""Unit tests for the CSV statement parser."""
from app.domain.entities import TransactionType
from app.parsers.csv_parser import CsvStatementParser


def _parse(content: bytes, filename: str = "statement.csv"):
    return CsvStatementParser().parse(content, filename)


def test_parse_hdfc_style_csv(sample_csv_bytes):
    result = _parse(sample_csv_bytes)
    assert len(result.transactions) == 7
    assert result.bank_name is None  # CSV has no bank marker
    assert result.period_start is not None
    assert result.period_end is not None
    assert result.period_start.isoformat() == "2025-04-01"
    assert result.period_end.isoformat() == "2025-04-10"


def test_parse_credit_and_debit_types(sample_csv_bytes):
    result = _parse(sample_csv_bytes)
    by_desc = {t.description: t for t in result.transactions}
    assert by_desc["SALARY CREDIT"].transaction_type == TransactionType.CREDIT
    assert by_desc["SALARY CREDIT"].amount == 75000.0
    assert by_desc["ZOMATO ORDER"].transaction_type == TransactionType.DEBIT
    assert by_desc["ZOMATO ORDER"].amount == 450.0
    assert by_desc["UPI TRANSFER TO RAMESH"].amount == 1200.5
    assert by_desc["RELIANCE FRESH"].balance == 91782.25


def test_semicolon_delimited_csv():
    content = "Date;Description;Debit;Credit;Balance\n01/01/2025;TEST SHOP;100.00;;900.00\n".encode()
    result = _parse(content)
    assert len(result.transactions) == 1
    assert result.transactions[0].transaction_type == TransactionType.DEBIT


def test_single_amount_column_with_negative_sign():
    content = "Date,Narration,Amount\n01/01/2025,SALARY,+50000\n02/01/2025,FOOD,-250\n".encode()
    result = _parse(content)
    assert result.transactions[0].transaction_type == TransactionType.CREDIT
    assert result.transactions[1].transaction_type == TransactionType.DEBIT


def test_empty_csv_raises():
    import pytest

    from app.core.errors import AppError

    with pytest.raises(ValueError):
        _parse(b"")


def test_csv_without_header_raises():
    import pytest

    with pytest.raises(ValueError, match="header"):
        _parse(b"1,2,3,4\n5,6,7,8\n")


def test_date_format_dd_mon_yyyy():
    content = "Date,Description,Debit,Credit,Balance\n01-Apr-2025,GROCERY,300.00,,700.00\n".encode()
    result = _parse(content)
    assert result.transactions[0].date.isoformat() == "2025-04-01"
