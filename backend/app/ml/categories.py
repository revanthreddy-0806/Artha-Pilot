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

# Categories that are conceptually "income" for dashboard purposes.
INCOME_CATEGORIES: set[str] = {"Salary"}

DEFAULT_CATEGORY = "Uncategorized"
