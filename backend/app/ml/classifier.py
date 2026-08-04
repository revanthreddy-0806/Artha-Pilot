"""Three-tier transaction categorization engine.

Tier 1: deterministic keyword rules (fast, exact).
Tier 2: TF-IDF + Logistic Regression model trained on curated seed data.
Tier 3: confidence threshold check; falls back to "Uncategorized"
        rather than making a low-confidence guess.
"""
import logging
import re
import threading

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

from app.ml.categories import DEFAULT_CATEGORY
from app.ml.seed_data import RULE_KEYWORDS, SEED_DATA

logger = logging.getLogger(__name__)

CONFIDENCE_THRESHOLD = 0.55

_WORD_RE = re.compile(r"[a-z0-9]+")

_LOCK = threading.Lock()
_vectorizer: TfidfVectorizer | None = None
_model: LogisticRegression | None = None


def normalize(text: str) -> str:
    """Lowercase, keep alphanumeric tokens, join with spaces."""
    return " ".join(_WORD_RE.findall(text.lower()))


def _match_rules(description: str) -> str | None:
    """Best-match keyword rules: the longest matching keyword wins."""
    lowered = description.lower()
    best: str | None = None
    best_len = 0
    for keyword, category in RULE_KEYWORDS.items():
        if keyword.lower() in lowered and len(keyword) > best_len:
            best = category
            best_len = len(keyword)
    return best


def _ensure_model() -> None:
    """Lazily build the ML model on first use (thread-safe)."""
    global _vectorizer, _model
    with _LOCK:
        if _model is not None:
            return
        texts = [normalize(desc) for desc, _ in SEED_DATA]
        labels = [_label_index(cat) for _, cat in SEED_DATA]

        vectorizer = TfidfVectorizer(
            lowercase=False,
            token_pattern=r"[a-z0-9]+",
            ngram_range=(1, 2),
            sublinear_tf=True,
        )
        features = vectorizer.fit_transform(texts)

        model = LogisticRegression(
            C=10.0,
            max_iter=2000,
            solver="lbfgs",
        )
        model.fit(features, labels)
        _vectorizer = vectorizer
        _model = model
        logger.info("Categorized model trained on %d samples", len(SEED_DATA))


def _label_index(category: str) -> int:
    from app.ml.categories import CATEGORY_LABELS

    return CATEGORY_LABELS[category]


def categorize(description: str) -> tuple[str, float | None]:
    """Categorize a transaction description.

    Returns (category, confidence). Confidence is None for rule-based hits.
    """
    if not description or not description.strip():
        return DEFAULT_CATEGORY, None

    rule_hit = _match_rules(description)
    if rule_hit:
        return rule_hit, None

    try:
        _ensure_model()
    except Exception:
        logger.exception("ML model initialization failed; falling back to Uncategorized")
        return DEFAULT_CATEGORY, None

    try:
        text = normalize(description)
        features = _vectorizer.transform([text])
        probabilities = _model.predict_proba(features)[0]
        best_idx = int(_model.predict(features)[0])
        confidence = float(probabilities[best_idx])
    except Exception:
        logger.exception("Prediction failed for description=%r", description)
        return DEFAULT_CATEGORY, None

    if confidence >= CONFIDENCE_THRESHOLD:
        from app.ml.categories import LABEL_CATEGORIES

        return LABEL_CATEGORIES[best_idx], round(confidence, 4)
    return DEFAULT_CATEGORY, round(confidence, 4)


def categorize_many(descriptions: list[str]) -> list[tuple[str, float | None]]:
    """Batched categorization: one vectorization + prediction pass.

    Falls back to single categorization if the model cannot be used.
    """
    if not descriptions:
        return []

    results: list[tuple[str, float | None]] = []
    ml_indices: list[int] = []

    for idx, desc in enumerate(descriptions):
        if not desc or not desc.strip():
            results.append((DEFAULT_CATEGORY, None))
            continue
        rule_hit = _match_rules(desc)
        if rule_hit:
            results.append((rule_hit, None))
        else:
            results.append((None, None))
            ml_indices.append(idx)

    if not ml_indices:
        return results

    try:
        _ensure_model()
    except Exception:
        logger.exception("ML model initialization failed; falling back to Uncategorized")
        for idx in ml_indices:
            results[idx] = (DEFAULT_CATEGORY, None)
        return results

    from app.ml.categories import LABEL_CATEGORIES

    try:
        texts = [normalize(descriptions[idx]) for idx in ml_indices]
        features = _vectorizer.transform(texts)
        probabilities = _model.predict_proba(features)
        predictions = _model.predict(features)
        for position, idx in enumerate(ml_indices):
            best_idx = int(predictions[position])
            confidence = float(probabilities[position][best_idx])
            if confidence >= CONFIDENCE_THRESHOLD:
                results[idx] = (LABEL_CATEGORIES[best_idx], round(confidence, 4))
            else:
                results[idx] = (DEFAULT_CATEGORY, round(confidence, 4))
    except Exception:
        logger.exception("Batched prediction failed; categorizing individually")
        for idx in ml_indices:
            results[idx] = categorize(descriptions[idx])

    return results
