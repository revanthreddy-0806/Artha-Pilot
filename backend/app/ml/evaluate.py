"""Evaluation harness for the 3-tier transaction categorizer.

Estimates honest performance of the ML tier on the curated seed data:
stratified train/test split with a fixed random seed, per-class and
macro precision/recall/F1, confusion matrix, and a leakage check that
no description appears in both splits.

The ML tier is only ever reached when Tier 1 keyword rules do not match,
so the ML metrics here are computed ONLY on the rule-free seed samples
(that is what production actually sends to the model). A secondary
end-to-end "system" accuracy that includes the keyword rules is also
reported.
"""
import json
import logging
from collections import Counter

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
)
from sklearn.model_selection import train_test_split

from app.ml.categories import CATEGORY_LABELS, LABEL_CATEGORIES, DEFAULT_CATEGORY
from app.ml.classifier import CONFIDENCE_THRESHOLD, categorize, normalize, _match_rules
from app.ml.seed_data import SEED_DATA

logger = logging.getLogger(__name__)

RANDOM_STATE = 42
TEST_SIZE = 0.33


def _build_model(texts: list[str], labels: list[int]):
    vectorizer = TfidfVectorizer(
        lowercase=False,
        token_pattern=r"[a-z0-9]+",
        ngram_range=(1, 2),
        sublinear_tf=True,
    )
    features = vectorizer.fit_transform(texts)
    model = LogisticRegression(C=10.0, max_iter=2000, solver="lbfgs")
    model.fit(features, labels)
    return vectorizer, model


def ml_relevant_samples() -> list[tuple[str, str]]:
    """Seed entries that bypass the keyword rules and reach the ML tier."""
    return [(desc, cat) for desc, cat in SEED_DATA if _match_rules(desc) is None]


def _leakage_report(train_descs: list[str], test_descs: list[str]) -> dict:
    train_tokens: Counter[str] = Counter()
    for desc in train_descs:
        train_tokens.update(normalize(desc).split())
    test_shared = 0
    test_total = 0
    for desc in test_descs:
        tokens = normalize(desc).split()
        test_total += len(tokens)
        test_shared += sum(1 for tok in tokens if train_tokens[tok])
    return {
        "duplicate_descriptions_across_splits": len(set(test_descs) & set(train_descs)),
        "test_token_overlap_fraction": round(test_shared / test_total, 4) if test_total else 0.0,
    }


def evaluate_ml_tier(seed_data: list[tuple[str, str]] | None = None) -> dict:
    """Train on a stratified split of the rule-free seed subset and score the held-out test set."""
    samples = ml_relevant_samples() if seed_data is None else seed_data
    if len(samples) < 4:
        raise ValueError("Not enough samples to split for evaluation")

    descriptions = [desc for desc, _ in samples]
    labels = [CATEGORY_LABELS[cat] for _, cat in samples]

    try:
        train_descs, test_descs, train_y, test_y = train_test_split(
            descriptions, labels, test_size=TEST_SIZE, random_state=RANDOM_STATE, stratify=labels
        )
    except ValueError:
        train_descs, test_descs, train_y, test_y = train_test_split(
            descriptions, labels, test_size=TEST_SIZE, random_state=RANDOM_STATE
        )

    vectorizer, model = _build_model(
        [normalize(d) for d in train_descs], train_y
    )
    features = vectorizer.transform([normalize(d) for d in test_descs])
    probabilities = model.predict_proba(features)
    predictions = model.predict(features)
    class_values = list(model.classes_)

    predicted_labels: list[str] = []
    confidences: list[float] = []
    for idx, proba in enumerate(probabilities):
        best_col = int(proba.argmax())
        best_idx = int(class_values[best_col])
        confidence = float(proba[best_col])
        if confidence >= CONFIDENCE_THRESHOLD:
            predicted_labels.append(LABEL_CATEGORIES[best_idx])
        else:
            predicted_labels.append(DEFAULT_CATEGORY)
        confidences.append(confidence)

    true_labels = [LABEL_CATEGORIES[y] for y in test_y]
    report = classification_report(
        true_labels, predicted_labels, zero_division=0, output_dict=True
    )
    labels_used = sorted({y for y in true_labels} | {p for p in predicted_labels})
    return {
        "n_train": len(train_descs),
        "n_test": len(test_descs),
        "accuracy": round(accuracy_score(true_labels, predicted_labels), 4),
        "macro_f1": round(f1_score(true_labels, predicted_labels, average="macro", zero_division=0), 4),
        "weighted_f1": round(f1_score(true_labels, predicted_labels, average="weighted", zero_division=0), 4),
        "mean_confidence": round(sum(confidences) / len(confidences), 4),
        "per_class": {k: v for k, v in report.items() if k not in ("accuracy", "macro avg", "weighted avg")},
        "confusion_matrix": confusion_matrix(
            true_labels, predicted_labels, labels=labels_used
        ).tolist(),
        "confusion_labels": labels_used,
        "threshold": CONFIDENCE_THRESHOLD,
        "leakage": _leakage_report(train_descs, test_descs),
    }


def overall_categorization_accuracy(seed_data: list[tuple[str, str]] | None = None) -> dict:
    """End-to-end accuracy of categorize() (rules + ML + threshold) on the curated seeds."""
    samples = SEED_DATA if seed_data is None else seed_data
    correct = 0
    for desc, expected in samples:
        predicted, _ = categorize(desc)
        if predicted == expected:
            correct += 1
    return {
        "samples": len(samples),
        "correct": correct,
        "accuracy": round(correct / len(samples), 4),
    }


def full_report() -> dict:
    ml = evaluate_ml_tier()
    overall = overall_categorization_accuracy()
    return {
        "seed_samples_total": len(SEED_DATA),
        "ml_relevant_samples": len(ml_relevant_samples()),
        "overall_accuracy": overall,
        "ml_tier": ml,
    }


if __name__ == "__main__":
    logging.basicConfig(level=logging.WARNING)
    print(json.dumps(full_report(), indent=2))