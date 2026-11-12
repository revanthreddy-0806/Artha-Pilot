"""Unit tests for the 3-tier categorization engine."""
import joblib

from app.ml import classifier
from app.ml.classifier import categorize, categorize_many, normalize
from app.ml.categories import DEFAULT_CATEGORY


def _reset_model():
    with classifier._LOCK:
        classifier._model = None
        classifier._vectorizer = None


def test_model_cache_is_created_and_reused(tmp_path, monkeypatch):
    cache_dir = tmp_path / "cache"
    monkeypatch.setattr(classifier, "_CACHE_DIR", str(cache_dir))
    monkeypatch.setattr(classifier, "_CACHE_PATH", str(cache_dir / "categorizer.joblib"))
    _reset_model()

    first = categorize("biryani house order")
    assert (cache_dir / "categorizer.joblib").exists()
    _reset_model()

    second = categorize("biryani house order")
    assert second == first


def test_stale_cache_is_retrained(tmp_path, monkeypatch):
    cache_dir = tmp_path / "cache"
    monkeypatch.setattr(classifier, "_CACHE_DIR", str(cache_dir))
    monkeypatch.setattr(classifier, "_CACHE_PATH", str(cache_dir / "categorizer.joblib"))
    cache_dir.mkdir()
    joblib.dump(("stale", "payload", "wrong-signature"), classifier._CACHE_PATH)
    _reset_model()

    category, confidence = categorize("biryani house booking")
    assert category != DEFAULT_CATEGORY
    from app.ml import classifier as clf

    assert clf._model is not None
    _, _, signature = joblib.load(clf._CACHE_PATH)
    assert signature == clf._seed_signature()


def test_corrupt_model_cache_recovers(tmp_path, monkeypatch):
    cache_dir = tmp_path / "cache"
    monkeypatch.setattr(classifier, "_CACHE_DIR", str(cache_dir))
    monkeypatch.setattr(classifier, "_CACHE_PATH", str(cache_dir / "categorizer.joblib"))
    cache_dir.mkdir()
    (cache_dir / "categorizer.joblib").write_bytes(b"not a valid joblib payload")
    _reset_model()

    category, confidence = categorize("biryani house booking")
    assert category == "Food & Dining"
    assert confidence is not None
    from app.ml.classifier import _seed_signature

    _, _, signature = joblib.load(classifier._CACHE_PATH)
    assert signature == _seed_signature()


def test_rule_based_categories():
    assert categorize("ZOMATO ORDER #1234")[0] == "Food & Dining"
    assert categorize("salary credit march")[0] == "Salary"
    assert categorize("ATM WITHDRAWAL AT HDFC")[0] == "Cash"
    assert categorize("UPI transfer to friend")[0] == "Transfer"
    assert categorize("RELIANCE FRESH GROCERY")[0] == "Groceries"
    assert categorize("BLINKIT ORDER")[0] == "Groceries"
    assert categorize("mutual fund sip payment")[0] == "Investments"


def test_longest_keyword_rule_wins():
    assert categorize("AMAZON PRIME RENEWAL")[0] == "Bills & Recurring"
    assert categorize("AMAZON PRIME VIDEO")[0] == "Entertainment"
    assert categorize("AMAZON SHOPPING")[0] == "Shopping"
    assert categorize("PRIME VIDEO RENEWAL")[0] == "Entertainment"


def test_rule_conflict_resolutions():
    """Longer specific rules must override generic ones for known conflicts."""
    assert categorize("UBER EATS")[0] == "Food & Dining"
    assert categorize("UBER TRIP")[0] == "Transport"
    assert categorize("METRO CARD RECHARGE")[0] == "Transport"
    assert categorize("FASTAG RECHARGE")[0] == "Transport"
    assert categorize("YOUTUBE PREMIUM")[0] == "Entertainment"
    assert categorize("HEALTH INSURANCE PREMIUM")[0] == "Health & Medical"
    assert categorize("EDUCATION LOAN EMI")[0] == "Education"


def test_observed_interest_credit_pattern():
    assert categorize("INTEREST CREDIT")[0] == "Investments"
    assert categorize("INTEREST CREDIT Credit")[0] == "Investments"


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
