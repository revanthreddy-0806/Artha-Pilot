"""Generate a sample PDF bank statement for tests (HDFC-style)."""
from reportlab.pdfgen import canvas

OUTPUT = "tests/fixtures/sample_statement.pdf"

TRANSACTIONS = [
    ("01/04/2025", "ZOMATO ORDER", "450.00", "0.00", "24550.00"),
    ("02/04/2025", "SALARY CREDIT", "0.00", "75000.00", "99550.00"),
    ("03/04/2025", "ATM WITHDRAWAL", "5000.00", "0.00", "94550.00"),
    ("05/04/2025", "UPI TRANSFER TO RAMESH", "1200.50", "0.00", "93349.50"),
    ("06/04/2025", "RELIANCE FRESH", "1567.25", "0.00", "91782.25"),
]


def build(path: str = OUTPUT) -> str:
    c = canvas.Canvas(path)
    c.setFont("Helvetica-Bold", 14)
    c.drawString(50, 780, "HDFC BANK STATEMENT")
    c.setFont("Helvetica", 10)
    c.drawString(50, 760, "Account Number: 50100234567890")
    c.drawString(50, 748, "Period: 01/04/2025 to 30/04/2025")
    c.setFont("Helvetica-Bold", 9)
    c.drawString(50, 730, "Date  Description  Debit  Credit  Balance")
    c.setFont("Helvetica", 9)
    y = 714
    for date_, desc, debit, credit, balance in TRANSACTIONS:
        line = f"{date_}  {desc}  {debit}  {credit}  {balance}"
        c.drawString(50, y, line)
        y -= 16
    c.save()
    return path


if __name__ == "__main__":
    print(build())
