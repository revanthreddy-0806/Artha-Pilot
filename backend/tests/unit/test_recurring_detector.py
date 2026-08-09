"""Unit tests for the pure recurring/EMI detection logic."""
from datetime import date

from app.services.recurring_detector import (
    TransactionRef,
    amounts_match,
    detect_recurring,
    mark_recurring_transaction_ids,
    normalize_description,
)


def _txn(txn_id: int, day: int, month: int, amount: float, description: str, category: str = "Rent"):
    return TransactionRef(
        id=txn_id,
        date=date(2026, month, day),
        amount=-amount,
        description=description,
        category=category,
    )


def test_normalize_description_strips_punctuation_and_case():
    assert normalize_description("HDFC CAR LOAN EMI 1234") == "hdfc car loan emi 1234"
    assert normalize_description("  Netflix  Subscription  ") == "netflix subscription"


def test_amounts_match_tolerance():
    assert amounts_match(12000.00, 12000.50)
    assert amounts_match(12000.00, 12050.00) is False
    assert amounts_match(99.00, 99.90)


def test_single_occurrence_is_not_recurring():
    txns = [_txn(1, 5, 1, 12000, "HDFC CAR LOAN EMI")]
    assert detect_recurring(txns) == []


def test_detects_monthly_recurring_charge():
    txns = [
        _txn(1, 5, 1, 1500, "Spotify Subscription", "Entertainment"),
        _txn(2, 5, 2, 1500, "Spotify Subscription", "Entertainment"),
        _txn(3, 4, 3, 1500, "Spotify Subscription", "Entertainment"),
    ]
    groups = detect_recurring(txns)
    assert len(groups) == 1
    group = groups[0]
    assert group.kind == "recurring"
    assert group.interval_days == 30
    assert group.occurrences == 3
    assert group.confidence == 0.75
    assert set(group.transaction_ids) == {1, 2, 3}
    assert mark_recurring_transaction_ids(groups) == {1, 2, 3}


def test_emi_hint_words_flagged_as_emi():
    txns = [
        _txn(1, 2, 1, 12500, "SBI CAR LOAN EMI"),
        _txn(2, 2, 2, 12500, "SBI CAR LOAN EMI"),
        _txn(3, 2, 3, 12500, "SBI CAR LOAN EMI"),
    ]
    groups = detect_recurring(txns)
    assert len(groups) == 1
    assert groups[0].kind == "emi"


def test_large_monthly_amount_flagged_as_emi_without_hint():
    txns = [
        _txn(1, 10, 1, 20_000, "CITY HOUSING PAYMENT", "Bills & Recurring"),
        _txn(2, 10, 2, 20_000, "CITY HOUSING PAYMENT", "Bills & Recurring"),
    ]
    groups = detect_recurring(txns)
    assert len(groups) == 1
    assert groups[0].kind == "emi"


def test_large_monthly_rent_is_recurring_not_emi():
    txns = [
        _txn(1, 10, 1, 20_000, "RENT PAYMENT", "Rent"),
        _txn(2, 10, 2, 20_000, "RENT PAYMENT", "Rent"),
    ]
    groups = detect_recurring(txns)
    assert len(groups) == 1
    assert groups[0].kind == "recurring"


def test_small_monthly_amount_is_recurring_not_emi():
    txns = [
        _txn(1, 10, 1, 800, "NETFLIX SUBSCRIPTION"),
        _txn(2, 10, 2, 800, "NETFLIX SUBSCRIPTION"),
    ]
    groups = detect_recurring(txns)
    assert len(groups) == 1
    assert groups[0].kind == "recurring"


def test_credits_are_ignored():
    txns = [
        _txn(1, 1, 1, -25000, "Salary"),  # credit (positive amount)
        _txn(2, 1, 2, -25000, "Salary"),
        _txn(3, 1, 3, -25000, "Salary"),
    ]
    assert detect_recurring(txns) == []


def test_non_monthly_interval_is_rejected():
    txns = [
        _txn(1, 1, 1, 500, "Daily Canteen"),
        _txn(2, 8, 1, 500, "Daily Canteen"),
        _txn(3, 15, 1, 500, "Daily Canteen"),
    ]
    assert detect_recurring(txns) == []


def test_next_expected_date_rolls_forward():
    txns = [
        _txn(1, 5, 6, 12000, "HDFC CAR LOAN EMI"),
        _txn(2, 5, 7, 12000, "HDFC CAR LOAN EMI"),
    ]
    groups = detect_recurring(txns, today=date(2026, 8, 6))
    assert len(groups) == 1
    # last occurrence 2026-07-05 + 30d = 2026-08-04 < today, so next is 2026-09-03
    assert groups[0].next_expected_date == date(2026, 9, 3)


def test_different_amounts_are_separate_groups():
    txns = [
        _txn(1, 5, 1, 12000, "HDFC CAR LOAN EMI"),
        _txn(2, 5, 2, 12000, "HDFC CAR LOAN EMI"),
        _txn(3, 6, 1, 5500, "GYM MEMBERSHIP"),
        _txn(4, 6, 2, 5500, "GYM MEMBERSHIP"),
    ]
    groups = detect_recurring(txns)
    assert len(groups) == 2


def test_positive_stored_debits_are_detected():
    """Stored debits use positive magnitudes + transaction_type (real DB convention)."""
    txns = [
        TransactionRef(
            id=1,
            date=date(2026, 6, 10),
            amount=25_000,
            description="HOME LOAN EMI",
            category="Bills & Recurring",
            transaction_type="debit",
        ),
        TransactionRef(
            id=2,
            date=date(2026, 7, 10),
            amount=25_000,
            description="HOME LOAN EMI",
            category="Bills & Recurring",
            transaction_type="debit",
        ),
    ]
    groups = detect_recurring(txns)
    assert len(groups) == 1
    assert groups[0].kind == "emi"
    assert groups[0].amount == 25_000
    assert set(groups[0].transaction_ids) == {1, 2}


def test_positive_stored_credits_are_ignored():
    txns = [
        TransactionRef(
            id=1,
            date=date(2026, 6, 1),
            amount=100_000,
            description="ACME SALARY",
            category="Salary",
            transaction_type="credit",
        ),
        TransactionRef(
            id=2,
            date=date(2026, 7, 1),
            amount=100_000,
            description="ACME SALARY",
            category="Salary",
            transaction_type="credit",
        ),
    ]
    assert detect_recurring(txns) == []
