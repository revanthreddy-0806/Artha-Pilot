"""Credit-health risk scoring and loan-eligibility math (pure, testable)."""
from __future__ import annotations

from dataclasses import dataclass

ANNUAL_INTEREST_RATE = 0.105
MAX_LOAN_TENURE_MONTHS = 60
SAFE_EMI_INCOME_RATIO = 0.30

ScoreWeights = {
    "base": 30,
    "stability": 20,
    "savings": 20,
    "utilization": 20,
    "emi_load": 10,
}


@dataclass(frozen=True)
class RiskMetrics:
    """Aggregated monthly metrics used as risk-scoring input."""

    has_activity: bool
    months_of_data: int
    monthly_income: float | None
    months_with_income: int
    income_std_ratio: float | None  # std/mean of monthly credits
    monthly_expenses: float
    savings_rate: float | None  # (income - expenses) / income
    utilization: float | None  # expenses / income
    emi_load_ratio: float | None  # emi amount / income
    emi_monthly_amount: float


def _stability_points(metrics: RiskMetrics) -> int:
    if not metrics.monthly_income:
        return 0
    if metrics.months_with_income <= 1:
        return 10
    ratio = metrics.income_std_ratio
    if ratio is None:
        return 10
    if ratio <= 0.15:
        return 20
    if ratio <= 0.30:
        return 15
    if ratio <= 0.50:
        return 10
    return 5


def _savings_points(metrics: RiskMetrics) -> int:
    rate = metrics.savings_rate
    if rate is None:
        return 0
    if rate >= 0.30:
        return 20
    if rate >= 0.15:
        return 15
    if rate >= 0:
        return 10
    return 0


def _utilization_points(metrics: RiskMetrics) -> int:
    util = metrics.utilization
    if util is None:
        return 0
    if util <= 0.60:
        return 20
    if util <= 0.80:
        return 10
    if util <= 1.00:
        return 5
    return 0


def _emi_load_points(metrics: RiskMetrics) -> int:
    ratio = metrics.emi_load_ratio
    if ratio is None:
        return 0
    if ratio <= 0.20:
        return 10
    if ratio <= 0.40:
        return 5
    return 0


def score_risk(metrics: RiskMetrics) -> int:
    if not metrics.has_activity:
        return 0
    points = (
        ScoreWeights["base"]
        + _stability_points(metrics)
        + _savings_points(metrics)
        + _utilization_points(metrics)
        + _emi_load_points(metrics)
    )
    return max(0, min(100, points))


def risk_band(score: int) -> str:
    if score >= 80:
        return "Excellent"
    if score >= 60:
        return "Good"
    if score >= 40:
        return "Fair"
    return "Poor"


def build_factors(metrics: RiskMetrics, score: int) -> list[dict[str, str]]:
    """Human-readable scoring factors for the API response."""
    factors: list[dict[str, str]] = []

    if not metrics.has_activity:
        factors.append(
            {
                "name": "Activity history",
                "value": "No transactions uploaded yet",
                "impact": "negative",
            }
        )
        return factors

    if metrics.monthly_income:
        if metrics.months_with_income <= 1:
            stability_value = "Low (limited history)"
            stability_impact = "negative"
        elif metrics.income_std_ratio is not None and metrics.income_std_ratio <= 0.30:
            stability_value = "High"
            stability_impact = "positive"
        elif metrics.income_std_ratio is not None and metrics.income_std_ratio <= 0.50:
            stability_value = "Moderate"
            stability_impact = "neutral"
        else:
            stability_value = "Low"
            stability_impact = "negative"
        factors.append(
            {
                "name": "Income stability",
                "value": stability_value,
                "impact": stability_impact,
            }
        )

    if metrics.savings_rate is not None:
        savings_impact = "positive" if metrics.savings_rate >= 0.15 else ("neutral" if metrics.savings_rate >= 0 else "negative")
        factors.append(
            {
                "name": "Savings rate",
                "value": f"{metrics.savings_rate * 100:.1f}%",
                "impact": savings_impact,
            }
        )

    if metrics.utilization is not None:
        util_impact = "positive" if metrics.utilization <= 0.60 else ("neutral" if metrics.utilization <= 0.80 else "negative")
        factors.append(
            {
                "name": "Expense utilization",
                "value": f"{metrics.utilization * 100:.1f}% of income",
                "impact": util_impact,
            }
        )

    if metrics.emi_load_ratio is not None:
        emi_impact = "positive" if metrics.emi_load_ratio <= 0.20 else ("neutral" if metrics.emi_load_ratio <= 0.40 else "negative")
        factors.append(
            {
                "name": "EMI / loan load",
                "value": f"{metrics.emi_load_ratio * 100:.1f}% of income",
                "impact": emi_impact,
            }
        )

    factors.append(
        {
            "name": "Data history",
            "value": f"{metrics.months_of_data} month(s) of transactions",
            "impact": "neutral",
        }
    )
    return factors


def build_summary(metrics: RiskMetrics, band: str) -> str:
    if not metrics.has_activity:
        return "Upload bank statements to build a credit-health assessment."
    if band in {"Excellent", "Good"}:
        return (
            f"Your finances look {band.lower()} with a healthy income-to-expense balance. "
            "Lenders would likely view you as a low-risk borrower."
        )
    if band == "Fair":
        return "Your finances are workable but have room for improvement - focus on savings and keeping expenses in check."
    return "Your current spending pattern signals risk. Reduce expenses and EMIs before taking on new credit."


def safe_emi_amount(metrics: RiskMetrics) -> float:
    if not metrics.monthly_income:
        return 0.0
    return max(0.0, SAFE_EMI_INCOME_RATIO * metrics.monthly_income - metrics.emi_monthly_amount)


def max_loan_amount(
    safe_emi: float,
    annual_rate: float = ANNUAL_INTEREST_RATE,
    tenure_months: int = MAX_LOAN_TENURE_MONTHS,
) -> float | None:
    """Principal affordable for a fixed-rate loan at the given EMI and tenure."""
    if safe_emi <= 0:
        return None
    monthly_rate = annual_rate / 12
    if monthly_rate <= 0:
        return None
    principal = safe_emi * (1 - (1 + monthly_rate) ** -tenure_months) / monthly_rate
    return round(principal, 2)
