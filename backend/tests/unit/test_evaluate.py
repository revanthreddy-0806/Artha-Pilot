"""Unit tests for the categorizer evaluation harness."""
import json

from app.ml.classifier import categorize, normalize
from app.ml.categories import DEFAULT_CATEGORY
from app.ml.evaluate import (
    evaluate_ml_tier,
    full_report,
    ml_relevant_samples,
    overall_categorization_accuracy,
)
from app.ml.seed_data import SEED_DATA


def test_ml_relevant_samples_are_rule_free():
    from app.ml.classifier import _match_rules

    for desc, _ in ml_relevant_samples():
        assert _match_rules(desc) is None


def test_evaluate_ml_tier_shape_and_sanity():
    report = evaluate_ml_tier()
    assert report["n_train"] > 0 and report["n_test"] > 0
    assert report["n_train"] + report["n_test"] == len(ml_relevant_samples())
    assert 0.0 <= report["accuracy"] <= 1.0
    assert 0.0 <= report["macro_f1"] <= 1.0
    assert 0.0 <= report["weighted_f1"] <= 1.0
    assert 0.0 <= report["mean_confidence"] <= 1.0
    assert report["threshold"] == 0.55
    cm = report["confusion_matrix"]
    assert len(cm) == len(report["confusion_labels"])
    assert all(len(row) == len(report["confusion_labels"]) for row in cm)
    assert report["leakage"]["duplicate_descriptions_across_splits"] == 0
    assert 0.0 <= report["leakage"]["test_token_overlap_fraction"] <= 1.0


def test_evaluation_is_deterministic():
    first = evaluate_ml_tier()
    second = evaluate_ml_tier()
    assert first == second


def test_overall_categorization_accuracy():
    report = overall_categorization_accuracy()
    assert report["samples"] == len(SEED_DATA)
    assert report["accuracy"] > 0.9


def test_full_report_shape():
    report = full_report()
    assert report["seed_samples_total"] == len(SEED_DATA)
    assert report["ml_relevant_samples"] == len(ml_relevant_samples())
    assert "ml_tier" in report and "overall_accuracy" in report


def test_report_json_serializable():
    json.dumps(full_report())