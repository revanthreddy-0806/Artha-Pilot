"""Unit tests for the pure risk-scoring and loan-eligibility logic."""
from app.services.risk_service import (
    RiskMetrics,
    build_factors,
    build_summary,
    max_loan_amount,
    risk_band,
    safe_emi_amount,
    score_risk,
)


def _metrics(**overrides) -> RiskMetrics:
    base = dict(
        has_activity=True,
        months_of_data=3,
        monthly_income=100_000.0,
        months_with_income=3,
        income_std_ratio=0.05,
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
    assert {"Income stability", "Savings rate", "Expense utilization", "EMI / loan load", "Data history"} <= names
    positive = {f["impact"] for f in factors}
    assert "positive" in positive
