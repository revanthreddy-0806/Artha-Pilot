"""Explainable, rule-based recommendation engine (Phase 4 spec).

Maps the user's risk score to a fixed equity/debt/gold mix and explains
every number from the user's own data. Nothing here is random and no
reason is invented: the engine only reports values it actually derived
from the user's transactions, risk assessment and (optional) market
reference data.

When the data needed for a defensible suggestion is missing, the engine
returns an explicit "insufficient" status with a plain reason instead of
guessing.
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass, field

from app.models.transaction import Transaction, TransactionType
from app.schemas.risk import RiskAssessment
from app.services.market_data_service import MarketQuote

EDUCATIONAL_DISCLAIMER = (
    "Educational/simulated guidance only - not SEBI-registered investment advice."
)

# Fixed rule-based allocation mixes per risk-appetite band.
ALLOCATION_MATRIX: dict[str, dict[str, int]] = {
    "Conservative": {"Equity": 30, "Debt": 60, "Gold": 10},
    "Moderate": {"Equity": 45, "Debt": 40, "Gold": 15},
    "Aggressive": {"Equity": 60, "Debt": 25, "Gold": 15},
}

RATIONALES: dict[str, dict[str, str]] = {
    "Conservative": {
        "Equity": (
            "Your conservative appetite favours capital protection, so equity is capped at 30% "
            "as the growth engine for long-term goals."
        ),
        "Debt": "Debt funds and fixed income anchor the portfolio and fit your preference for stability.",
        "Gold": "10% gold hedges against inflation and market shocks.",
    },
    "Moderate": {
        "Equity": "45% equity balances growth potential with your moderate comfort with short-term swings.",
        "Debt": "40% in debt keeps near-term goals safe while the portfolio grows.",
        "Gold": "15% gold hedges against inflation and market shocks.",
    },
    "Aggressive": {
        "Equity": "Your high appetite supports a 60% equity focus aimed at long-term growth; expect volatility.",
        "Debt": "25% debt provides a stability floor for the aggressive equity sleeve.",
        "Gold": "15% gold hedges against inflation and market shocks.",
    },
}

ALLOCATION_ORDER = ["Equity", "Debt", "Gold"]


@dataclass(frozen=True)
class MonthlyContext:
    """Aggregated monthly figures derived purely from the user's transactions."""

    months_used: int
    income_recorded: bool
    monthly_income: float
    monthly_expenses: float
    monthly_savings: float
    savings_rate: float | None
    category_monthly: dict[str, float] = field(default_factory=dict)


def _round2(value: float) -> float:
    return round(value, 2)


def _used_months(transactions: list[Transaction], window_months: int) -> list[str]:
    months = sorted({t.date.strftime("%Y-%m") for t in transactions})
    return months[-window_months:] if len(months) > window_months else months


def build_context(transactions: list[Transaction], window_months: int = 3) -> MonthlyContext:
    """Aggregate monthly income/expenses/savings and per-category spend.

    Average of per-month totals across the data window (default: the most
    recent 3 calendar months). Debits/category amounts are magnitudes;
    ``transaction_type`` decides the direction.
    """
    used = _used_months(transactions, window_months)

    month_credits: dict[str, float] = defaultdict(float)
    month_debits: dict[str, float] = defaultdict(float)
    category_by_month: dict[str, dict[str, float]] = defaultdict(lambda: defaultdict(float))
    for txn in transactions:
        key = txn.date.strftime("%Y-%m")
        value = abs(txn.amount)
        if txn.transaction_type == TransactionType.DEBIT:
            month_debits[key] += value
            category_by_month[key][txn.category] += value
        else:
            month_credits[key] += value

    if not used:
        return MonthlyContext(
            months_used=0,
            income_recorded=False,
            monthly_income=0.0,
            monthly_expenses=0.0,
            monthly_savings=0.0,
            savings_rate=None,
            category_monthly={},
        )

    monthly_income = sum(month_credits.get(m, 0.0) for m in used) / len(used)
    monthly_expenses = sum(month_debits.get(m, 0.0) for m in used) / len(used)
    monthly_savings = monthly_income - monthly_expenses
    savings_rate = monthly_savings / monthly_income if monthly_income > 0 else None

    categories = {c for m in used for c in category_by_month[m]}
    category_monthly = {
        category: sum(category_by_month[m].get(category, 0.0) for m in used) / len(used)
        for category in categories
    }

    return MonthlyContext(
        months_used=len(used),
        income_recorded=monthly_income > 0,
        monthly_income=_round2(monthly_income),
        monthly_expenses=_round2(monthly_expenses),
        monthly_savings=_round2(monthly_savings),
        savings_rate=_round2(savings_rate) if savings_rate is not None else None,
        category_monthly={k: _round2(v) for k, v in sorted(category_monthly.items())},
    )


def allocation_for_band(band: str) -> list[dict]:
    """Deterministic equity/debt/gold mix for a risk band."""
    matrix = ALLOCATION_MATRIX.get(band, ALLOCATION_MATRIX["Moderate"])
    return [
        {
            "asset_class": asset.lower(),
            "label": asset,
            "percentage": matrix[asset],
            "rationale": RATIONALES.get(band, RATIONALES["Moderate"])[asset],
        }
        for asset in ALLOCATION_ORDER
    ]


def build_notes(assessment: RiskAssessment, context: MonthlyContext, quotes: list[MarketQuote]) -> list[dict]:
    """Explainable notes; every one derives from a measured value."""
    notes: list[dict] = []
    if context.savings_rate is not None and context.savings_rate < 0.15:
        notes.append(
            {
                "kind": "caution",
                "text": (
                    f"Your savings rate is {context.savings_rate * 100:.1f}% - set aside a "
                    "3-6 month emergency fund before investing."
                ),
            }
        )
    if assessment.emi_load_ratio is None or assessment.emi_load_ratio == 0:
        notes.append({"kind": "info", "text": "No EMI detected in your recent transactions."})
    if context.months_used < 2:
        notes.append(
            {
                "kind": "info",
                "text": (
                    f"Only {context.months_used} month(s) of history - treat these "
                    "percentages as educational until more data accrues."
                ),
            }
        )
    if not quotes or any(q.status == "unavailable" for q in quotes):
        notes.append(
            {
                "kind": "info",
                "text": (
                    "Live market reference prices are unavailable right now; the allocation "
                    "itself does not depend on today's prices."
                ),
            }
        )
    return notes


def _quote_dict(quote: MarketQuote) -> dict:
    return {
        "symbol": quote.symbol,
        "name": quote.name,
        "price": _round2(quote.price),
        "change_pct": _round2(quote.change_pct) if quote.change_pct is not None else None,
        "as_of": quote.as_of.isoformat(),
        "source": quote.source,
        "status": quote.status,
    }


def _insufficient(assessment: RiskAssessment, reason: str) -> dict:
    return {
        "status": "insufficient",
        "risk_score": assessment.score,
        "risk_band": assessment.band,
        "tolerance_score": assessment.tolerance_score,
        "behavior_score": assessment.behavior_score,
        "insufficient_reason": reason,
        "context": None,
        "allocation": [],
        "allocation_summary": None,
        "notes": [],
        "market": [],
        "educational_disclaimer": EDUCATIONAL_DISCLAIMER,
    }


def build_recommendation(
    assessment: RiskAssessment,
    transactions: list[Transaction],
    quotes: list[MarketQuote],
    *,
    window_months: int = 3,
) -> dict:
    """Compose the full recommendation payload (status ok or insufficient).

    Never fabricates numbers: without transactions or without recorded
    income the engine explains why no allocation can be suggested.
    """
    context = build_context(transactions, window_months)

    if not transactions:
        return _insufficient(
            assessment,
            "No transactions uploaded yet. Upload statements so the suggestion can be built "
            "from your actual income and expenses.",
        )
    if not context.income_recorded:
        return _insufficient(
            assessment,
            "Regular income has not been detected. An allocation cannot be suggested without "
            "knowing your income and savings.",
        )

    investable_monthly = max(0.0, context.monthly_savings)
    return {
        "status": "ok",
        "risk_score": assessment.score,
        "risk_band": assessment.band,
        "tolerance_score": assessment.tolerance_score,
        "behavior_score": assessment.behavior_score,
        "insufficient_reason": None,
        "context": {
            "months_used": context.months_used,
            "monthly_income": context.monthly_income,
            "monthly_expenses": context.monthly_expenses,
            "savings_rate": context.savings_rate,
            "investable_monthly": _round2(investable_monthly),
        },
        "allocation": allocation_for_band(assessment.band),
        "allocation_summary": (
            f"Based on your {assessment.band} risk appetite ({assessment.score}/100), an "
            f"investable surplus of {investable_monthly:,.0f}/month would split as follows."
        ),
        "notes": build_notes(assessment, context, quotes),
        "market": [_quote_dict(q) for q in quotes],
        "educational_disclaimer": EDUCATIONAL_DISCLAIMER,
    }


# ---------------------------------------------------------------------------
# What-if simulator (never mutates data; projection only).
# ---------------------------------------------------------------------------
def simulate_expense_reduction(
    transactions: list[Transaction],
    category: str,
    monthly_reduction: float,
    *,
    window_months: int = 3,
) -> dict:
    """Project the effect of cutting ``category`` spend by ``monthly_reduction``.

    Raises ValueError with a user-facing message for invalid scenarios.
    """
    context = build_context(transactions, window_months)
    current_category = context.category_monthly.get(category)

    if not transactions or not context.months_used:
        raise ValueError(
            "Not enough transaction history to simulate changes yet - upload statements first."
        )
    if current_category is None:
        raise ValueError(f"Category '{category}' was not found in your recent transactions.")
    if monthly_reduction <= 0:
        raise ValueError("Reduction must be greater than zero.")
    if monthly_reduction > current_category:
        raise ValueError(
            f"Reduction exceeds current monthly spend of {current_category:,.0f} in '{category}'."
        )

    original_savings = context.monthly_savings
    original_rate = context.savings_rate
    new_savings = original_savings + monthly_reduction
    new_expenses = context.monthly_expenses - monthly_reduction
    new_rate = new_savings / context.monthly_income if context.monthly_income > 0 else None

    def _block(savings: float, expenses: float, rate: float | None) -> dict:
        return {
            "monthly_expenses": _round2(expenses),
            "monthly_savings": _round2(savings),
            "savings_rate": _round2(rate) if rate is not None else None,
        }

    narrative: str
    if original_rate is not None and new_rate is not None:
        narrative = (
            f"Cutting {category} by {monthly_reduction:,.0f}/month raises your monthly "
            f"savings from {original_savings:,.0f} to {new_savings:,.0f} and your savings "
            f"rate from {original_rate * 100:.0f}% to {new_rate * 100:.0f}%."
        )
    else:
        narrative = (
            f"Cutting {category} by {monthly_reduction:,.0f}/month frees "
            f"{monthly_reduction:,.0f} extra each month (simulated)."
        )

    return {
        "category": category,
        "monthly_reduction": _round2(monthly_reduction),
        "months_used": context.months_used,
        "current_monthly_spend": _round2(current_category),
        "monthly_income": context.monthly_income,
        "original": _block(original_savings, context.monthly_expenses, original_rate),
        "simulated": _block(new_savings, new_expenses, new_rate),
        "impact": {
            "monthly_expense_reduction": _round2(monthly_reduction),
            "additional_monthly_investable": _round2(monthly_reduction),
            "annualised_extra_investable": _round2(monthly_reduction * 12),
        },
        "narrative": narrative,
        "is_simulation": True,
    }