"""Generate a sample PDF bank statement for tests (HDFC-style)."""
from reportlab.pdfgen import canvas

OUTPUT = "tests/fixtures/sample_statement.pdf"
MULTILINE_OUTPUT = "tests/fixtures/sample_statement_multiline.pdf"

TRANSACTIONS = [
    ("01/04/2025", "ZOMATO ORDER", "450.00", "0.00", "24550.00"),
    ("02/04/2025", "SALARY CREDIT", "0.00", "75000.00", "99550.00"),
    ("03/04/2025", "ATM WITHDRAWAL", "5000.00", "0.00", "94550.00"),
    ("05/04/2025", "UPI TRANSFER TO RAMESH", "1200.50", "0.00", "93349.50"),
    ("06/04/2025", "RELIANCE FRESH", "1567.25", "0.00", "91782.25"),
]

MULTILINE_TRANSACTIONS = [
    ("01-07-2026", "UPI/COFFEE SHOP", "Debit", "320.00", "45,680.50"),
    ("03-07-2026", "SALARY CREDIT", "Credit", "65,000.00", "110,680.50"),
    ("05-07-2026", "ATM WITHDRAWAL", "Debit", "5,000.00", "105,680.50"),
    ("08-07-2026", "UPI/GROCERY MART", "Debit", "2,840.75", "102,839.75"),
    ("11-07-2026", "NEFT/RENT", "Debit", "18,000.00", "84,839.75"),
    ("15-07-2026", "UPI/UTILITY BILL", "Debit", "2,450.00", "82,389.75"),
    ("18-07-2026", "INTEREST CREDIT", "Credit", "185.25", "82,574.00"),
    ("22-07-2026", "UPI/RESTAURANT", "Debit", "1,275.00", "81,299.00"),
    ("25-07-2026", "UPI/ONLINE SHOPPING", "Debit", "3,999.00", "77,300.00"),
    ("29-07-2026", "CASH DEPOSIT", "Credit", "10,000.00", "87,300.00"),
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


def build_multiline(path: str = MULTILINE_OUTPUT) -> str:
    """Column-per-line layout: date, description, type, amount, balance on
    separate lines per transaction (the format some export tools produce)."""
    c = canvas.Canvas(path)
    c.setFont("Helvetica-Bold", 14)
    c.drawString(50, 800, "SYNTHETIC BANK STATEMENT - TEST DATA")
    c.setFont("Helvetica", 10)
    c.drawString(50, 782, "Fictional data generated for software testing. Not a real bank statement.")
    c.drawString(50, 770, "Account Holder: TEST USER")
    c.drawString(50, 758, "Account Number: XXXX-TEST-0001")
    c.drawString(50, 746, "Statement Period: July 2026")
    c.setFont("Helvetica-Bold", 9)
    c.drawString(50, 728, "Date")
    c.drawString(50, 716, "Description")
    c.drawString(50, 704, "Type")
    c.drawString(50, 692, "Amount (INR)")
    c.drawString(50, 680, "Balance (INR)")
    c.setFont("Helvetica", 9)
    y = 665
    for date_, desc, txn_type, amount, balance in MULTILINE_TRANSACTIONS:
        c.drawString(50, y, date_)
        c.drawString(50, y - 12, desc)
        c.drawString(50, y - 24, txn_type)
        c.drawString(50, y - 36, amount)
        c.drawString(50, y - 48, balance)
        y -= 62
    c.setFont("Helvetica", 8)
    c.drawString(50, y - 12, "DISCLAIMER: Entirely synthetic test data.")
    c.save()
    return path


if __name__ == "__main__":
    print(build())
    print(build_multiline())
