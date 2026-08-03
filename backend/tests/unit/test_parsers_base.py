"""Unit tests for shared parser helpers."""
from app.parsers.base import BaseStatementParser


def test_clean_amount_plain():
    assert BaseStatementParser.clean_amount("1,234.56") == 1234.56
    assert BaseStatementParser.clean_amount("450.00") == 450.0


def test_clean_amount_currency():
    assert BaseStatementParser.clean_amount("₹1,000") == 1000.0
    assert BaseStatementParser.clean_amount("Rs. 500.50") == 500.5
    assert BaseStatementParser.clean_amount("INR 25,000") == 25000.0


def test_clean_amount_negative_and_parentheses():
    assert BaseStatementParser.clean_amount("-200.00") == 200.0
    assert BaseStatementParser.clean_amount("(300.00)") == 300.0


def test_clean_amount_invalid():
    assert BaseStatementParser.clean_amount("") is None
    assert BaseStatementParser.clean_amount("N/A") is None
    assert BaseStatementParser.clean_amount(None) is None


def test_parse_date_formats():
    assert BaseStatementParser.parse_date("01/04/2025").isoformat() == "2025-04-01"
    assert BaseStatementParser.parse_date("01-04-2025").isoformat() == "2025-04-01"
    assert BaseStatementParser.parse_date("2025-04-01").isoformat() == "2025-04-01"
    assert BaseStatementParser.parse_date("1 Apr 2025").isoformat() == "2025-04-01"
    assert BaseStatementParser.parse_date("01-Apr-2025").isoformat() == "2025-04-01"
    assert BaseStatementParser.parse_date("Apr 1, 2025").isoformat() == "2025-04-01"


def test_parse_date_invalid():
    assert BaseStatementParser.parse_date("not-a-date") is None
    assert BaseStatementParser.parse_date("") is None
    assert BaseStatementParser.parse_date(None) is None


def test_detect_bank():
    assert BaseStatementParser.detect_bank("HDFC BANK LIMITED statement") == "HDFC BANK"
    assert BaseStatementParser.detect_bank("State Bank of India passbook") == "STATE BANK OF INDIA"
    assert BaseStatementParser.detect_bank("random text") is None
