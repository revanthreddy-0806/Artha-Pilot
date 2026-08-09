"""Three-tier transaction categorization engine.

Tier 1: deterministic keyword rules (fast, exact).
Tier 2: TF-IDF + Logistic Regression model trained on curated seed data.
Tier 3: confidence threshold check; falls back to "Uncategorized"
        rather than making a low-confidence guess.

The fitted model is persisted to disk via joblib and reused across
process restarts; it is retrained automatically whenever the seed data
or model configuration changes (checked via a content signature).
"""
import hashlib
import logging
import os
import re
import threading

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

from app.ml.categories import DEFAULT_CATEGORY
from app.ml.seed_data import RULE_KEYWORDS, SEED_DATA

logger = logging.getLogger(__name__)

CONFIDENCE_THRESHOLD = 0.55

_CACHE_DIR = os.environ.get("CATEGORIZER_CACHE_DIR", os.path.join(os.path.dirname(__file__), ".model_cache"))
_CACHE_PATH = os.path.join(_CACHE_DIR, "categorizer.joblib")

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


def _seed_signature() -> str:
    """Hash of everything that influences the fitted model (data + config)."""
    from app.ml.categories import CATEGORY_LABELS

    payload = (SEED_DATA, RULE_KEYWORDS, tuple(CATEGORY_LABELS), _WORD_RE.pattern)
    return hashlib.sha256(repr(payload).encode("utf-8")).hexdigest()


def _load_cached_model() -> tuple[TfidfVectorizer, LogisticRegression] | None:
    """Load a persisted model if present and matching the current seed data."""
    if not os.path.exists(_CACHE_PATH):
        return None
    try:
        vectorizer, model, signature = joblib.load(_CACHE_PATH)
    except Exception:
        logger.warning("Ignoring unreadable model cache at %s", _CACHE_PATH, exc_info=True)
        return None
    if signature != _seed_signature():
        logger.info("Model cache signature mismatch; retraining")
        return None
    return vectorizer, model


def _save_cached_model(vectorizer: TfidfVectorizer, model: LogisticRegression) -> None:
    try:
        os.makedirs(os.path.dirname(_CACHE_PATH), exist_ok=True)
        joblib.dump((vectorizer, model, _seed_signature()), _CACHE_PATH)
        logger.info("Persisted categorizer model to %s", _CACHE_PATH)
    except Exception:
        logger.warning("Failed to persist categorizer model cache", exc_info=True)


def _train_model() -> tuple[TfidfVectorizer, LogisticRegression]:
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
    return vectorizer, model


def _ensure_model() -> None:
    """Lazily acquire the ML model on first use (thread-safe)."""
    global _vectorizer, _model
    with _LOCK:
        if _model is not None:
            return
        cached = _load_cached_model()
        if cached is not None:
            _vectorizer, _model = cached
            logger.info("Loaded categorizer model from cache (%d samples)", len(SEED_DATA))
            return
        _vectorizer, _model = _train_model()
        _save_cached_model(_vectorizer, _model)
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
        best_col = int(probabilities.argmax())
        best_idx = int(_model.classes_[best_col])
        confidence = float(probabilities[best_col])
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
        class_values = list(_model.classes_)
        for position, idx in enumerate(ml_indices):
            proba = probabilities[position]
            best_col = int(proba.argmax())
            best_idx = int(class_values[best_col])
            confidence = float(proba[best_col])
            if confidence >= CONFIDENCE_THRESHOLD:
                results[idx] = (LABEL_CATEGORIES[best_idx], round(confidence, 4))
            else:
                results[idx] = (DEFAULT_CATEGORY, round(confidence, 4))
    except Exception:
        logger.exception("Batched prediction failed; categorizing individually")
        for idx in ml_indices:
            results[idx] = categorize(descriptions[idx])

    return results
