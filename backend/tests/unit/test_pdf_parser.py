"""Unit tests for the PDF statement parser."""
from app.domain.entities import TransactionType
from app.parsers.pdf_parser import PdfStatementParser


def test_parse_hdfc_style_pdf(sample_pdf_bytes):
    result = PdfStatementParser().parse(sample_pdf_bytes, "statement.pdf")
    assert result.bank_name == "HDFC BANK"
    assert len(result.transactions) == 5
    assert result.period_start.isoformat() == "2025-04-01"
    assert result.period_end.isoformat() == "2025-04-06"


def test_parse_multiline_layout_pdf(sample_multiline_pdf_bytes):
    """Each transaction is spread across separate date/description/type/
    amount/balance lines (previously dropped -> 422)."""
    result = PdfStatementParser().parse(sample_multiline_pdf_bytes, "statement.pdf")
    assert len(result.transactions) == 10
    assert result.period_start.isoformat() == "2026-07-01"
    assert result.period_end.isoformat() == "2026-07-29"
    by_desc = {t.description: t for t in result.transactions}
    assert by_desc["UPI/COFFEE SHOP Debit"].transaction_type == TransactionType.DEBIT
    assert by_desc["UPI/COFFEE SHOP Debit"].amount == 320.0
    assert by_desc["SALARY CREDIT Credit"].transaction_type == TransactionType.CREDIT
    assert by_desc["SALARY CREDIT Credit"].amount == 65000.0
    assert by_desc["ATM WITHDRAWAL Debit"].balance == 105680.5
    assert by_desc["CASH DEPOSIT Credit"].transaction_type == TransactionType.CREDIT
    assert by_desc["CASH DEPOSIT Credit"].amount == 10000.0
    assert by_desc["CASH DEPOSIT Credit"].balance == 87300.0


def test_inline_type_word_single_line():
    """One transaction per line with type word and amounts on the same line."""
    result = PdfStatementParser()._extract_transactions(
        [
            "01-07-2026 UPI/COFFEE SHOP DEBIT 320.00 45,680.50",
            "03-07-2026 SALARY CREDIT 65,000.00 110,680.50",
        ]
    )
    assert len(result) == 2
    assert result[0].description == "UPI/COFFEE SHOP DEBIT"
    assert result[0].amount == 320.0
    assert result[0].transaction_type == TransactionType.DEBIT
    assert result[0].balance == 45680.5
    assert result[1].transaction_type == TransactionType.CREDIT
    assert result[1].amount == 65000.0
    assert result[1].balance == 110680.5


def test_multiline_accumulation_with_inline_description():
    """Date shared with the description on the first line, type/amount/balance
    following on the next lines."""
    result = PdfStatementParser()._extract_transactions(
        [
            "01-07-2026 UPI/COFFEE SHOP",
            "Debit",
            "320.00",
            "45,680.50",
            "02-07-2026 SALARY CREDIT",
            "Credit",
            "5,000.00",
            "50,680.50",
        ]
    )
    assert len(result) == 2
    assert result[0].date.isoformat() == "2026-07-01"
    assert result[0].description == "UPI/COFFEE SHOP Debit"
    assert result[0].amount == 320.0
    assert result[0].transaction_type == TransactionType.DEBIT
    assert result[0].balance == 45680.5
    assert result[1].transaction_type == TransactionType.CREDIT
    assert result[1].amount == 5000.0
    assert result[1].balance == 50680.5


def test_pdf_types_and_amounts(sample_pdf_bytes):
    result = PdfStatementParser().parse(sample_pdf_bytes, "statement.pdf")
    by_desc = {t.description: t for t in result.transactions}
    assert by_desc["ZOMATO ORDER"].transaction_type == TransactionType.DEBIT
    assert by_desc["ZOMATO ORDER"].amount == 450.0
    assert by_desc["SALARY CREDIT"].transaction_type == TransactionType.CREDIT
    assert by_desc["SALARY CREDIT"].amount == 75000.0
    assert by_desc["ATM WITHDRAWAL"].balance == 94550.0


def test_garbage_pdf_raises():
    import pytest

    with pytest.raises(ValueError, match="Could not read PDF"):
        PdfStatementParser().parse(b"this is not a pdf at all", "x.pdf")


def test_empty_text_pdf_raises(sample_pdf_bytes):
    import pytest

    from reportlab.pdfgen import canvas
    import io

    buf = io.BytesIO()
    c = canvas.Canvas(buf)
    c.setFont("Helvetica", 10)
    c.drawString(50, 50, "   ")
    c.save()
    with pytest.raises(ValueError, match="No extractable text"):
        PdfStatementParser().parse(buf.getvalue(), "blank.pdf")
