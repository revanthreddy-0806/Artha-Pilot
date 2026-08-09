"""Unit tests for the recommendation engine (pure, no network)."""
from datetime import date, datetime

import pytest

from app.domain.entities import TransactionType
from app.models.transaction import Transaction
from app.schemas.risk import RiskAssessment
from app.services.market_data_service import MarketQuote
from app.services.recommendation_service import (
    ALLOCATION_MATRIX,
    allocation_for_band,
    build_context,
    build_recommendation,
    simulate_expense_reduction,
)


def _txn(day, month, amount, txn_type, category, description="TXN"):
    return Transaction(
        statement_id=1,
        user_id=1,
        date=date(2026, month, day),
        description=description,
        amount=abs(amount),
        transaction_type=TransactionType(txn_type),
        category=category,
    )


def _assessment(monthly_income=100_000.0, band="Moderate", score=70):
    return RiskAssessment(
        score=score,
        band=band,
        summary="summary",
        factors=[],
        tolerance_score=1000,
        tolerance_band=band,
        behavior_score=50,
        questionnaire_completed=True,
        income=monthly_income,
        monthly_expenses=40_000.0,
        savings_rate=0.6,
        emi_load_ratio=0.1,
        safe_emi_amount=20_000.0,
        max_loan_amount=None,
        max_loan_tenure_months=60,
    )


SAMPLE_TXNS = [
    _txn(1, 6, 100_000, "credit", "Salary", "SALARY"),
    _txn(1, 7, 100_000, "credit", "Salary", "SALARY"),
    _txn(2, 6, 30_000, "debit", "Rent", "RENT"),
    _txn(2, 7, 30_000, "debit", "Rent", "RENT"),
    _txn(5, 6, 12_000, "debit", "Dining", "ZOMATO"),
    _txn(5, 7, 12_000, "debit", "Dining", "ZOMATO"),
    _txn(6, 7, 25_000, "debit", "Groceries", "DMART"),
]

LIVE_QUOTE = MarketQuote(
    symbol="^NSEI",
    name="NIFTY 50",
    price=23_600.0,
    change_pct=0.42,
    as_of=datetime(2026, 8, 9, 12, 0),
    source="Yahoo Finance (daily close)",
    status="live",
)


def test_build_context_averages_used_months():
    context = build_context(SAMPLE_TXNS)
    assert context.months_used == 2
    assert context.monthly_income == 100_000
    assert context.monthly_expenses == 54_500
    assert context.monthly_savings == 45_500
    assert context.savings_rate == pytest.approx(0.455, abs=0.01)
    assert context.category_monthly["Dining"] == 12_000
    assert context.category_monthly["Groceries"] == 12_500  # one month of 25k, averaged over two


def test_build_context_respects_window():
    one_month = [_txn(1, 5, 90_000, "credit", "Salary"), _txn(2, 5, 20_000, "debit", "Rent")]
    context = build_context(one_month, window_months=3)
    assert context.months_used == 1
    assert context.monthly_income == 90_000


def test_allocation_is_deterministic_and_band_scaled():
    expected = {
        "Conservative": {"equity": 30, "debt": 60, "gold": 10},
        "Moderate": {"equity": 45, "debt": 40, "gold": 15},
        "Aggressive": {"equity": 60, "debt": 25, "gold": 15},
    }
    for band, mix in expected.items():
        result = allocation_for_band(band)
        assert [i["label"] for i in result] == ["Equity", "Debt", "Gold"]
        assert {i["asset_class"]: i["percentage"] for i in result} == mix
        for item in result:
            assert item["rationale"]
        assert allocation_for_band(band) == allocation_for_band(band)


def test_allocation_matrix_sums_to_100_for_every_band():
    for band, mix in ALLOCATION_MATRIX.items():
        assert sum(mix.values()) == 100


def test_unknown_band_falls_back_to_moderate():
    result = allocation_for_band("Whatever")
    assert result[0]["percentage"] == 45


def test_recommendation_insufficient_without_transactions():
    result = build_recommendation(_assessment(), [], [])
    assert result["status"] == "insufficient"
    assert result["allocation"] == []
    assert "No transactions uploaded" in result["insufficient_reason"]


def test_recommendation_insufficient_without_income():
    spend_only = [_txn(1, 7, 20_000, "debit", "Rent")]
    result = build_recommendation(_assessment(), spend_only, [])
    assert result["status"] == "insufficient"
    assert "income" in result["insufficient_reason"].lower()


def test_recommendation_ok_uses_band_and_market():
    result = build_recommendation(_assessment(band="Aggressive", score=75), SAMPLE_TXNS, [LIVE_QUOTE])
    assert result["status"] == "ok"
    assert result["risk_band"] == "Aggressive"
    equity = next(i for i in result["allocation"] if i["asset_class"] == "equity")
    assert equity["percentage"] == 60
    assert result["context"]["investable_monthly"] == pytest.approx(45_500, abs=0.01)
    assert result["market"][0]["status"] == "live"
    assert "not SEBI-registered" in result["educational_disclaimer"]


def test_recommendation_caution_when_savings_rate_low():
    low_savings = [
        _txn(1, 7, 100_000, "credit", "Salary"),
        _txn(2, 7, 88_000, "debit", "Rent"),
    ]
    result = build_recommendation(_assessment(band="Conservative", score=40), low_savings, [])
    assert any(n["kind"] == "caution" and "emergency fund" in n["text"] for n in result["notes"])


def test_recommendation_note_when_market_unavailable():
    result = build_recommendation(_assessment(), SAMPLE_TXNS, [])
    assert any("market reference prices are unavailable" in n["text"] for n in result["notes"])


def test_recommendation_note_when_insufficient_history():
    one_month = [
        _txn(1, 7, 100_000, "credit", "Salary"),
        _txn(2, 7, 30_000, "debit", "Rent"),
    ]
    result = build_recommendation(_assessment(), one_month, [])
    assert any("Only 1 month(s)" in n["text"] for n in result["notes"])


def test_simulate_reduction_recomputes_surplus():
    result = simulate_expense_reduction(SAMPLE_TXNS, "Dining", 10_000)
    assert result["category"] == "Dining"
    assert result["current_monthly_spend"] == 12_000
    assert result["original"]["monthly_savings"] == 45_500
    assert result["simulated"]["monthly_savings"] == 55_500
    assert result["simulated"]["monthly_expenses"] == 44_500
    assert result["simulated"]["savings_rate"] == pytest.approx(0.555, abs=0.01)
    assert result["impact"]["additional_monthly_investable"] == 10_000
    assert result["impact"]["annualised_extra_investable"] == 120_000
    assert "ZOMATO" in result["narrative"] or "Dining" in result["narrative"]
    assert result["is_simulation"] is True


def test_simulate_validation_errors():
    with pytest.raises(ValueError, match="not found"):
        simulate_expense_reduction(SAMPLE_TXNS, "Travel", 1_000)
    with pytest.raises(ValueError, match="greater than zero"):
        simulate_expense_reduction(SAMPLE_TXNS, "Dining", 0)
    with pytest.raises(ValueError, match="exceeds current"):
        simulate_expense_reduction(SAMPLE_TXNS, "Dining", 50_000)
    with pytest.raises(ValueError, match="Not enough"):
        simulate_expense_reduction([], "Dining", 1_000)


def test_simulate_is_stateless():
    before = SAMPLE_TXNS[5]
    simulate_expense_reduction(SAMPLE_TXNS, "Dining", 10_000)
    assert before.amount == SAMPLE_TXNS[5].amount