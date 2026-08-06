"""Unit tests for the pure risk-scoring, questionnaire and loan-eligibility logic."""
import pytest

from app.services.risk_service import (
    RISK_QUESTIONNAIRE,
    RiskMetrics,
    build_appetite_summary,
    build_assessment_factors,
    build_factors,
    build_summary,
    combined_risk_score,
    max_loan_amount,
    questionnaire_max_points,
    risk_band,
    safe_emi_amount,
    score_questionnaire,
    score_risk,
    tolerance_band,
)


def _metrics(**overrides) -> RiskMetrics:
    base = dict(
        has_activity=True,
        months_of_data=3,
        monthly_income=100_000.0,
        months_with_income=3,
        income_std_ratio=0.05,
        expense_std_ratio=0.08,
        monthly_expenses=40_000.0,
        savings_rate=0.60,
        utilization=0.40,
        emi_load_ratio=0.10,
        emi_monthly_amount=10_000.0,
    )
    base.update(overrides)
    return RiskMetrics(**base)


def test_empty_profile_scores_zero_and_poor():
    metrics = RiskMetrics(
        has_activity=False,
        months_of_data=0,
        monthly_income=None,
        months_with_income=0,
        income_std_ratio=None,
        expense_std_ratio=None,
        monthly_expenses=0.0,
        savings_rate=None,
        utilization=None,
        emi_load_ratio=None,
        emi_monthly_amount=0.0,
    )
    assert score_risk(metrics) == 0
    assert risk_band(0) == "Poor"
    assert build_summary(metrics, "Poor").startswith("Upload bank statements")
    assert build_factors(metrics, 0)[0]["impact"] == "negative"


def test_healthy_profile_scores_high():
    score = score_risk(_metrics())
    assert score >= 80
    assert risk_band(score) == "Excellent"


def test_poor_profile_scores_low():
    metrics = RiskMetrics(
        has_activity=True,
        months_of_data=1,
        monthly_income=None,
        months_with_income=0,
        income_std_ratio=None,
        expense_std_ratio=None,
        monthly_expenses=80_000.0,
        savings_rate=None,
        utilization=None,
        emi_load_ratio=None,
        emi_monthly_amount=0.0,
    )
    score = score_risk(metrics)
    assert score < 40
    assert risk_band(score) == "Poor"


def test_moderate_profile_is_fair_or_good():
    score = score_risk(
        _metrics(
            monthly_income=80_000.0,
            monthly_expenses=70_000.0,
            savings_rate=0.125,
            utilization=0.875,
            emi_load_ratio=0.35,
        )
    )
    assert 40 <= score < 80


def test_expense_volatility_lowers_score():
    steady = score_risk(_metrics(expense_std_ratio=0.05))
    volatile = score_risk(_metrics(expense_std_ratio=0.90))
    assert volatile < steady


def test_income_volatility_lowers_score():
    steady = score_risk(_metrics(income_std_ratio=0.05))
    volatile = score_risk(_metrics(income_std_ratio=0.90))
    assert volatile < steady


def test_questionnaire_max_score_is_100_aggressive():
    answers = {q["id"]: max(q["options"], key=lambda o: o["points"])["id"] for q in RISK_QUESTIONNAIRE}
    score, band = score_questionnaire(answers)
    assert questionnaire_max_points() == 10 * 4
    assert score == 100
    assert band == "Aggressive"


def test_questionnaire_min_score_is_conservative():
    answers = {q["id"]: min(q["options"], key=lambda o: o["points"])["id"] for q in RISK_QUESTIONNAIRE}
    score, band = score_questionnaire(answers)
    assert score == 25
    assert band == "Conservative"


def test_questionnaire_mixed_answers_are_moderate():
    answers = {q["id"]: q["options"][1]["id"] for q in RISK_QUESTIONNAIRE}
    score, band = score_questionnaire(answers)
    assert 40 <= score < 70
    assert band == "Moderate"


def test_questionnaire_requires_all_questions():
    answers = {q["id"]: q["options"][0]["id"] for q in RISK_QUESTIONNAIRE}
    del answers["horizon"]
    with pytest.raises(ValueError):
        score_questionnaire(answers)


def test_questionnaire_rejects_unknown_option():
    answers = {q["id"]: q["options"][0]["id"] for q in RISK_QUESTIONNAIRE}
    answers["horizon"] = "not-an-option"
    with pytest.raises(ValueError):
        score_questionnaire(answers)


def test_tolerance_band_boundaries():
    assert tolerance_band(0) == "Conservative"
    assert tolerance_band(39) == "Conservative"
    assert tolerance_band(40) == "Moderate"
    assert tolerance_band(69) == "Moderate"
    assert tolerance_band(70) == "Aggressive"
    assert tolerance_band(100) == "Aggressive"


def test_combined_risk_score_blends_50_50():
    assert combined_risk_score(100, 0) == 50
    assert combined_risk_score(0, 100) == 50
    assert combined_risk_score(60, 80) == 70
    assert combined_risk_score(25, 0) == 13  # half-up rounding of 12.5
    assert combined_risk_score(None, 75) == 75
    assert combined_risk_score(120, -10) == 55  # clamped


def test_appetite_summary_mentions_questionnaire_when_completed():
    with_q = build_appetite_summary(70, 80, "Aggressive")
    without_q = build_appetite_summary(55, None, "Moderate")
    assert "questionnaire" in with_q.lower()
    assert "questionnaire" in without_q.lower()


def test_assessment_factors_include_questionnaire_and_behavior():
    factors = build_assessment_factors(_metrics(), 80, "Aggressive", 85)
    names = {f["name"] for f in factors}
    assert "Risk questionnaire" in names
    assert "Spending behaviour" in names
    assert "Expense stability" in names


def test_safe_emi_uses_thirty_percent_rule_minus_existing():
    metrics = _metrics(monthly_income=100_000.0, emi_monthly_amount=10_000.0)
    assert safe_emi_amount(metrics) == 20_000.0

    no_income = _metrics(monthly_income=None, monthly_expenses=10_000.0, months_with_income=0)
    assert safe_emi_amount(no_income) == 0.0


def test_max_loan_amount_math():
    principal = max_loan_amount(30_000.0, annual_rate=0.105, tenure_months=60)
    assert principal is not None
    assert 1_000_000 < principal < 2_000_000


def test_max_loan_amount_none_when_no_capacity():
    assert max_loan_amount(0.0) is None


def test_factors_cover_core_metrics():
    factors = build_factors(_metrics(), 85)
    names = {f["name"] for f in factors}
    assert {
        "Income stability",
        "Expense stability",
        "Savings rate",
        "Expense utilization",
        "EMI / loan load",
        "Data history",
    } <= names
    positive = {f["impact"] for f in factors}
    assert "positive" in positive