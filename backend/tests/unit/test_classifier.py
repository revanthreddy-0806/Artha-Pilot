"""Unit tests for the 3-tier categorization engine."""
from app.ml.classifier import categorize, categorize_many, normalize
from app.ml.categories import DEFAULT_CATEGORY


def test_rule_based_categories():
    assert categorize("ZOMATO ORDER #1234")[0] == "Food & Dining"
    assert categorize("salary credit march")[0] == "Salary"
    assert categorize("ATM WITHDRAWAL AT HDFC")[0] == "Cash"
    assert categorize("UPI transfer to friend")[0] == "Transfer"
    assert categorize("RELIANCE FRESH GROCERY")[0] == "Groceries"
    assert categorize("mutual fund sip payment")[0] == "Investments"


def test_longest_keyword_rule_wins():
    assert categorize("AMAZON PRIME RENEWAL")[0] == "Bills & Recurring"
    assert categorize("AMAZON PRIME VIDEO")[0] == "Entertainment"
    assert categorize("AMAZON SHOPPING")[0] == "Shopping"
    assert categorize("PRIME VIDEO RENEWAL")[0] == "Entertainment"


def test_ml_prediction_on_seed_data():
    category, confidence = categorize("biryani house order")
    assert category == "Food & Dining"
    assert confidence is not None and confidence > 0.0


def test_ml_prediction_second_seed():
    category, confidence = categorize("lab tests at diagnostic center")
    assert category == "Health & Medical"
    assert confidence is not None


def test_unknown_description_falls_back():
    category, confidence = categorize("XYZZY QUARKNODDLE FRABJOUS")
    assert category == DEFAULT_CATEGORY
    assert confidence is not None


def test_empty_description():
    assert categorize("")[0] == DEFAULT_CATEGORY
    assert categorize("   ")[0] == DEFAULT_CATEGORY
    assert categorize(None)[0] == DEFAULT_CATEGORY


def test_normalize():
    assert normalize("UPI-Txn 123.45") == "upi txn 123 45"
    assert normalize("₹1,000 SBI") == "1 000 sbi"


def test_categorize_many_matches_individual_results():
    descriptions = [
        "ZOMATO ORDER",
        "biryani house",
        "ATM WITHDRAWAL",
        "",
        "XYZZY QUARKNODDLE",
    ]
    batch = categorize_many(descriptions)
    individual = [categorize(d) for d in descriptions]
    assert batch == individual


def test_categorize_many_empty():
    assert categorize_many([]) == []
