"""Transaction category taxonomy."""
CATEGORIES: list[str] = [
    "Food & Dining",
    "Groceries",
    "Transport",
    "Utilities",
    "Rent",
    "Entertainment",
    "Shopping",
    "Health & Medical",
    "Education",
    "Salary",
    "Investments",
    "Travel",
    "Bills & Recurring",
    "Transfer",
    "Cash",
    "Taxes",
    "Fees & Charges",
    "Uncategorized",
]

CATEGORY_LABELS: dict[str, int] = {name: idx for idx, name in enumerate(CATEGORIES)}
LABEL_CATEGORIES: dict[int, str] = {idx: name for name, idx in CATEGORY_LABELS.items()}

DEFAULT_CATEGORY = "Uncategorized"
