"""Risk-appetite assessment: questionnaire + spending-behaviour scoring (pure, testable).

Phase 3 spec: build a risk questionnaire (standard financial risk-tolerance
question set), combine the answers with spending behaviour (EMI-to-income
ratio, savings rate, expense volatility) into an explainable, rule-based
risk score that is defensible in a viva.

The final risk score is a weighted blend:
    risk_score = 50% questionnaire tolerance + 50% spending-behaviour score
(behaviour-only when the questionnaire has not been completed yet).
"""
from __future__ import annotations

from dataclasses import dataclass

ANNUAL_INTEREST_RATE = 0.105
MAX_LOAN_TENURE_MONTHS = 60
SAFE_EMI_INCOME_RATIO = 0.30

# Rule-based scoring weights (max behaviour score = 100).
ScoreWeights = {
    "base": 30,  # has any spend/income activity
    "income_stability": 10,
    "expense_stability": 10,  # expense volatility, inverse
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
    expense_std_ratio: float | None  # std/mean of monthly expenses (volatility)
    monthly_expenses: float
    savings_rate: float | None  # (income - expenses) / income
    utilization: float | None  # expenses / income
    emi_load_ratio: float | None  # emi amount / income
    emi_monthly_amount: float


# ---------------------------------------------------------------------------
# Standard financial risk-tolerance question set.
# Internal option "points" (1 = most conservative .. 4 = most aggressive)
# drive the score; the API only exposes ids/labels to the user.
# ---------------------------------------------------------------------------
RISK_QUESTIONNAIRE: list[dict] = [
    {
        "id": "horizon",
        "text": "How soon do you expect to need the money you may invest?",
        "options": [
            {"id": "under-1y", "label": "Within 1 year", "points": 1},
            {"id": "1-3y", "label": "In 1 to 3 years", "points": 2},
            {"id": "3-7y", "label": "In 3 to 7 years", "points": 3},
            {"id": "7y-plus", "label": "Beyond 7 years", "points": 4},
        ],
    },
    {
        "id": "goal",
        "text": "What is your primary reason for investing?",
        "options": [
            {"id": "emergency", "label": "Emergency / safety of capital", "points": 1},
            {"id": "short-term", "label": "A short-term purchase", "points": 2},
            {"id": "wealth", "label": "Long-term wealth building", "points": 3},
            {"id": "growth", "label": "Maximising growth above all else", "points": 4},
        ],
    },
    {
        "id": "drop",
        "text": "If your investments dropped 20% in a single month, what would you do?",
        "options": [
            {"id": "sell-all", "label": "Sell everything to stop losses", "points": 1},
            {"id": "sell-some", "label": "Sell part of it", "points": 2},
            {"id": "hold", "label": "Hold and wait for recovery", "points": 3},
            {"id": "buy-more", "label": "Buy more while prices are low", "points": 4},
        ],
    },
    {
        "id": "loss-tolerance",
        "text": "In a single year, how large a loss could you accept without giving up?",
        "options": [
            {"id": "zero", "label": "I cannot accept any loss", "points": 1},
            {"id": "up-to-5", "label": "Up to 5% of value", "points": 2},
            {"id": "up-to-15", "label": "Up to 15% of value", "points": 3},
            {"id": "over-20", "label": "20% or more", "points": 4},
        ],
    },
    {
        "id": "income-stability",
        "text": "How stable is your monthly income?",
        "options": [
            {"id": "unstable", "label": "Not stable / varies a lot", "points": 1},
            {"id": "variable", "label": "Variable with my work", "points": 2},
            {"id": "mostly-steady", "label": "Mostly steady", "points": 3},
            {"id": "very-stable", "label": "Very stable (e.g. regular salary)", "points": 4},
        ],
    },
    {
        "id": "emergency-fund",
        "text": "Apart from everyday savings, do you have an emergency fund?",
        "options": [
            {"id": "none", "label": "No emergency fund", "points": 1},
            {"id": "under-3m", "label": "Less than 3 months of expenses", "points": 2},
            {"id": "3-6m", "label": "3 to 6 months of expenses", "points": 3},
            {"id": "over-6m", "label": "More than 6 months of expenses", "points": 4},
        ],
    },
    {
        "id": "knowledge",
        "text": "How would you rate your investing knowledge?",
        "options": [
            {"id": "beginner", "label": "Beginner", "points": 1},
            {"id": "some", "label": "Somewhat knowledgeable", "points": 2},
            {"id": "knowledgeable", "label": "Knowledgeable", "points": 3},
            {"id": "expert", "label": "Very knowledgeable", "points": 4},
        ],
    },
    {
        "id": "market-fall",
        "text": "If markets kept falling for several weeks, you would likely...",
        "options": [
            {"id": "to-cash", "label": "Move everything into cash", "points": 1},
            {"id": "part-cash", "label": "Move part into cash", "points": 2},
            {"id": "stay-plan", "label": "Stay invested as planned", "points": 3},
            {"id": "add-more", "label": "Increase investments", "points": 4},
        ],
    },
    {
        "id": "risk-reward",
        "text": "Which statement best matches your preference?",
        "options": [
            {"id": "guaranteed", "label": "Guaranteed small returns over growth", "points": 1},
            {"id": "low-risk", "label": "Small risk for moderate returns", "points": 2},
            {"id": "balanced", "label": "Balanced risk and return", "points": 3},
            {"id": "high-growth", "label": "Highest possible returns, accepts big swings", "points": 4},
        ],
    },
    {
        "id": "savings-discipline",
        "text": "How regularly do you save from your income?",
        "options": [
            {"id": "rarely", "label": "Rarely or never", "points": 1},
            {"id": "occasionally", "label": "Occasionally", "points": 2},
            {"id": "most-months", "label": "Most months", "points": 3},
            {"id": "every-month", "label": "Every month", "points": 4},
        ],
    },
]


def tolerance_band(score: int) -> str:
    """Map a 0-100 risk score to an appetite band."""
    if score >= 70:
        return "Aggressive"
    if score >= 40:
        return "Moderate"
    return "Conservative"


def questionnaire_max_points() -> int:
    """Maximum possible questionnaire points (fully aggressive answers)."""
    return sum(max(o["points"] for o in q["options"]) for q in RISK_QUESTIONNAIRE)


def score_questionnaire(answers: dict[str, str]) -> tuple[int, str]:
    """Score questionnaire answers into a 0-100 tolerance score and appetite band.

    Raises:
        ValueError: if not every question is answered or an option id is unknown.
    """
    known = {q["id"]: q for q in RISK_QUESTIONNAIRE}
    if set(answers) != set(known):
        raise ValueError(f"Answer every question ({len(known)} required)")

    total = 0
    for qid, option_id in answers.items():
        question = known[qid]
        option = next((o for o in question["options"] if o["id"] == option_id), None)
        if option is None:
            raise ValueError(f"Unknown option '{option_id}' for question '{qid}'")
        total += option["points"]

    max_points = questionnaire_max_points()
    score = round(total / max_points * 100)
    return score, tolerance_band(score)


def combined_risk_score(tolerance_score: int | None, behaviour_score: int) -> int:
    """Blend the questionnaire tolerance with the spending-behaviour score.

    Explainable rule: 50% stated preference + 50% demonstrated capacity.
    Falls back to the behaviour score when the questionnaire is incomplete.
    """
    if tolerance_score is None:
        return max(0, min(100, behaviour_score))
    blended = 0.5 * tolerance_score + 0.5 * behaviour_score
    return max(0, min(100, int(blended + 0.5)))


# ---------------------------------------------------------------------------
# Spending-behaviour scoring (credit-health style).
# ---------------------------------------------------------------------------
def _income_stability_points(metrics: RiskMetrics) -> int:
    if not metrics.monthly_income:
        return 0
    if metrics.months_with_income <= 1:
        return 5
    ratio = metrics.income_std_ratio
    if ratio is None:
        return 5
    if ratio <= 0.15:
        return 10
    if ratio <= 0.30:
        return 8
    if ratio <= 0.50:
        return 5
    return 2


def _expense_stability_points(metrics: RiskMetrics) -> int:
    """Inverse expense volatility: steadier spending earns more points."""
    ratio = metrics.expense_std_ratio
    if ratio is None:
        return 5  # limited history - treat as neutral
    if ratio <= 0.15:
        return 10
    if ratio <= 0.30:
        return 8
    if ratio <= 0.50:
        return 5
    return 2


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
    """Behaviour score from spending data, 0-100 (pure rule-based)."""
    if not metrics.has_activity:
        return 0
    points = (
        ScoreWeights["base"]
        + _income_stability_points(metrics)
        + _expense_stability_points(metrics)
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
    """Human-readable spending factors for the API response."""
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

    if metrics.expense_std_ratio is not None:
        ratio = metrics.expense_std_ratio
        expense_stability = "Steady" if ratio <= 0.30 else ("Moderate" if ratio <= 0.50 else "Volatile")
        exp_impact = "positive" if ratio <= 0.30 else ("neutral" if ratio <= 0.50 else "negative")
        factors.append(
            {
                "name": "Expense stability",
                "value": expense_stability,
                "impact": exp_impact,
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


def build_assessment_factors(
    metrics: RiskMetrics,
    tolerance_score: int | None,
    tolerance_band: str | None,
    behaviour_score: int,
) -> list[dict[str, str]]:
    """Compose the explainable factors for the combined risk assessment."""
    factors: list[dict[str, str]] = []
    if tolerance_score is not None and tolerance_band is not None:
        factors.append(
            {
                "name": "Risk questionnaire",
                "value": f"{tolerance_score}/100 ({tolerance_band})",
                "impact": (
                    "positive" if tolerance_band == "Aggressive"
                    else ("neutral" if tolerance_band == "Moderate" else "negative")
                ),
            }
        )
    factors.append(
        {
            "name": "Spending behaviour",
            "value": f"{behaviour_score}/100",
            "impact": "positive" if behaviour_score >= 60 else ("neutral" if behaviour_score >= 40 else "negative"),
        }
    )
    factors.extend(build_factors(metrics, behaviour_score))
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


def build_appetite_summary(score: int, tolerance_score: int | None, band: str) -> str:
    if tolerance_score is None:
        return (
            f"Your estimated risk appetite is {band} based on your spending pattern. "
            "Answer the risk questionnaire to combine your stated preferences with your behaviour."
        )
    return (
        f"Your risk appetite is {band} ({score}/100) - your questionnaire answers "
        f"({tolerance_score}/100) combined with your spending behaviour."
    )


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