"""Unit tests for the PDF statement parser."""
from app.domain.entities import TransactionType
from app.parsers.pdf_parser import PdfStatementParser


def test_parse_hdfc_style_pdf(sample_pdf_bytes):
    result = PdfStatementParser().parse(sample_pdf_bytes, "statement.pdf")
    assert result.bank_name == "HDFC BANK"
    assert len(result.transactions) == 5
    assert result.period_start.isoformat() == "2025-04-01"
    assert result.period_end.isoformat() == "2025-04-06"


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
