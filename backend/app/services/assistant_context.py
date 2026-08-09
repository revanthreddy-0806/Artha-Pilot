"""Grounded context for the financial assistant.

This module is the single source of truth for everything the assistant is
allowed to say: it aggregates VERIFIED values from the existing services
(insights, recurring/EMI detection, risk assessment, recommendation engine,
market reference data) and only those values are passed to the model.

The model never computes financial figures itself; every number it can
mention appears in the structured context produced here.
"""
from __future__ import annotations

import re
from datetime import date

from sqlalchemy.orm import Session

from app.ml.categories import CATEGORIES
from app.services.insights_service import InsightsService
from app.services.market_data_service import MarketDataService
from app.services.recommendation_service import (
    build_recommendation,
    simulate_expense_reduction,
)
from app.repositories.transaction_repository import TransactionRepository

# ---------------------------------------------------------------------------
# What-if intent parsing (backend-determined, never left to the model).
# ---------------------------------------------------------------------------
_CATEGORY_INDEX: list[tuple[str, str]] = [
    (label, label.lower()) for label in CATEGORIES
]

CATEGORY_ALIAS_TARGETS: dict[str, str] = {
    "bills": "Bills & Recurring",
    "subscriptions": "Bills & Recurring",
    "emi": "Bills & Recurring",
    "fee": "Fees & Charges",
    "fees": "Fees & Charges",
    "groceries": "Groceries",
    "grocery": "Groceries",
    "food": "Food & Dining",
    "restaurants": "Food & Dining",
    "dining": "Food & Dining",
    "eating out": "Food & Dining",
    "transport": "Transport",
    "petrol": "Transport",
    "fuel": "Transport",
    "commute": "Transport",
    "utilities": "Utilities",
    "electricity": "Utilities",
    "rent": "Rent",
    "entertainment": "Entertainment",
    "shopping": "Shopping",
    "health": "Health & Medical",
    "medical": "Health & Medical",
    "education": "Education",
    "travel": "Travel",
    "salary": "Salary",
    "income": "Salary",
    "loan": "Loan Repayment",
}


def _resolve_what_if_category(
    user_message: str,
    user_categories: list[str],
) -> str | None:
    """Map the user's message onto ONE of the user's real categories.

    Prefers the user's own category strings (e.g. "Dining" vs "Food & Dining")
    so the what-if engine can find the spend. Static taxonomy is the fallback
    only when no user category matches.
    """
    lowered = user_message.lower()
    user_known = sorted(set(category.lower() for category in user_categories))
    by_category = {category.lower(): category for category in user_categories}

    candidate_targets: set[str] = set()
    for label, key in _CATEGORY_INDEX:
        if label.lower() in lowered or key in lowered:
            candidate_targets.add(label)
    for alias, target in CATEGORY_ALIAS_TARGETS.items():
        if alias in lowered:
            candidate_targets.add(target)

    if not candidate_targets:
        return None

    # 1) exact canonical identity against the user's categories
    for target in candidate_targets:
        if target.lower() in user_known:
            return by_category[target.lower()]

    # 2) closeness: canonical label contained in (or containing) a user label
    for target in candidate_targets:
        target_lower = target.lower()
        for known in user_known:
            if target_lower in known or known in target_lower:
                return by_category[known]

    # 3) static-taxonomy-only fallback (the what-if engine will explain it is
    #    unknown unless the category exists in the user's data).
    return next(iter(candidate_targets))


_AMOUNT_PATTERNS = [
    re.compile(r"(?P<amount>\d[\d,]{2,8})\s*(?:rs\.?|inr|₹)?", re.IGNORECASE),
    re.compile(r"[₹]\s*(?P<amount>\d[\d,]{2,8})"),
]


def _find_amount(text: str) -> float | None:
    """Extract a plausible monthly rupee amount (1,000 .. 2,000,000)."""
    for pattern in _AMOUNT_PATTERNS:
        match = pattern.search(text)
        if not match:
            continue
        raw = match.group("amount").replace(",", "")
        try:
            value = float(raw)
        except ValueError:
            continue
        if 1000 <= value <= 2000000:
            return value
    return None


# ---------------------------------------------------------------------------
# Grounded context construction.
# ---------------------------------------------------------------------------
def _quote_plain(quote) -> dict:
    return {
        "symbol": quote.symbol,
        "name": quote.name,
        "price": round(quote.price, 2),
        "change_pct": round(quote.change_pct, 2) if quote.change_pct is not None else None,
        "status": quote.status,
        "as_of": str(quote.as_of),
    }


def build_grounded_context(
    db,
    user_id: int,
    user_message: str,
    *,
    window_months: int = 3,
) -> dict:
    """Assemble the verified facts the assistant may reference.

    Never raises for missing data; "insufficient" flags make the model say
    honest things instead of guessing. The returned dict is also echoed in
    the API response so clients can audit the grounding.
    """
    insights = InsightsService(db)
    transactions = TransactionRepository(db).all_for_user(user_id)

    current_month = f"{date.today().year:04d}-{date.today().month:02d}"
    summary = insights.summary(user_id, current_month)
    trends = insights.monthly_trends(user_id, months=3)
    recurring = insights.recurring(user_id)
    risk = insights.risk_assessment(user_id)
    quotes = MarketDataService(db).quotes()
    recommendation = build_recommendation(risk, transactions, quotes, window_months=window_months)

    recurring_plain = [
        {
            "description": item.description,
            "amount": round(item.amount, 2),
            "category": item.category,
            "kind": item.kind,
            "interval_days": item.interval_days,
            "occurrences": item.occurrences,
            "confidence": item.confidence,
            "next_expected_date": str(item.next_expected_date),
        }
        for item in recurring.items
    ]

    emi_items = [item for item in recurring.items if item.kind == "emi"]

    months_income = [t.income for t in trends.months]
    average_income = (
        round(sum(months_income) / len(months_income), 2) if months_income else 0.0
    )
    trend_plain = [
        {
            "month": t.month,
            "income": t.income,
            "expenses": t.expenses,
            "savings": round(t.savings, 2),
        }
        for t in trends.months
    ]

    # What-if: determined by the backend when the user's message names one of
    # their real categories and a plausible amount. Never left to the model.
    what_if = None
    user_categories = sorted({t.category for t in transactions if t.category})
    category = _resolve_what_if_category(user_message, user_categories)
    amount = _find_amount(user_message)
    if category is not None and amount is not None:
        try:
            what_if = simulate_expense_reduction(transactions, category, amount)
        except ValueError:
            what_if = None

    return {
        "profile": {
            "current_month": current_month,
            "transaction_count": summary.transaction_count,
            "months_of_data": len(set(item["month"] for item in trend_plain)),
            "monthly_income_average": average_income,
            "current_month_income": summary.income,
            "current_month_expenses": summary.expenses,
            "current_month_savings": round(summary.savings, 2),
            "current_month_savings_rate": summary.savings_rate,
            "recent_months": trend_plain,
            "top_categories": [
                {
                    "category": item.category,
                    "amount": item.amount,
                    "percentage": item.percentage,
                }
                for item in summary.top_categories
            ],
        },
        "recurring": {
            "items": recurring_plain,
            "emi": [
                {
                    "description": item.description,
                    "amount": round(item.amount, 2),
                    "occurrences": item.occurrences,
                }
                for item in emi_items
            ],
            "emi_load_pct": round(risk.emi_load_ratio * 100, 1)
            if risk.emi_load_ratio is not None
            else None,
        },
        "risk": {
            "score": risk.score,
            "band": risk.band,
            "summary": risk.summary,
            "tolerance_score": risk.tolerance_score,
            "tolerance_band": risk.tolerance_band,
            "behavior_score": risk.behavior_score,
            "questionnaire_completed": risk.questionnaire_completed,
            "income": risk.income,
            "monthly_expenses": risk.monthly_expenses,
            "savings_rate": risk.savings_rate,
            "emi_load_ratio": risk.emi_load_ratio,
        },
        "recommendation": {
            "status": recommendation["status"],
            "risk_band": recommendation["risk_band"],
            "risk_score": recommendation["risk_score"],
            "insufficient_reason": recommendation["insufficient_reason"],
            "allocation": recommendation["allocation"],
            "allocation_summary": recommendation["allocation_summary"],
            "context": recommendation["context"],
            "notes": recommendation["notes"],
        },
        "market_reference": [_quote_plain(q) for q in quotes],
        "what_if": what_if,
        "insufficient_data": (
            "No transactions have been uploaded yet" if summary.transaction_count == 0 else None
        ),
    }