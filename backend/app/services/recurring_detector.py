"""Pure recurring/EMI spend detection logic (no database access).

The functions in this module operate on plain dataclasses so they can be unit
tested without a database or web framework.  Detection rules:

* Only debit transactions are considered (credits are income, not spend).
* Transactions are grouped by a normalized description (lowercased
  alphanumeric + spaces) and a stable amount (within a tolerance of the
  larger of Rs 1.00 or 0.5% of the amount).
* A group is recurring when the same normalized-description/amount combo
  appears in at least two distinct calendar months with the most common
  interval between 25 and 40 days.
* Confidence grows with the number of distinct months observed.
* A group whose most common interval is ~30 days and whose description
  suggests a loan (or whose amount is at least Rs 15,000, excluding Rent)
  is flagged as an EMI; everything else is a plain recurring charge.
"""
from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date, timedelta

MIN_MONTHLY_INTERVAL_DAYS = 25
MAX_MONTHLY_INTERVAL_DAYS = 40
MIN_DISTINCT_MONTHS = 2
EMI_AMOUNT_THRESHOLD = 15_000.0
EMI_EXCLUDED_CATEGORIES = {"Rent"}

EMI_DESCRIPTION_HINTS = (
    "emi",
    "loan",
    "finance",
    "installment",
    "mortgage",
    "creditcard",
    "housing",
)


@dataclass(frozen=True)
class TransactionRef:
    """Lightweight transaction projection used as detection input.

    ``amount`` is expected to use the stored convention: positive magnitudes
    with ``transaction_type`` carrying the direction.  When
    ``transaction_type`` is not supplied the sign of the amount is used as a
    fallback (positive = credit) to stay compatible with callers that pass
    signed amounts.
    """

    id: int
    date: date
    amount: float
    description: str
    category: str
    transaction_type: str | None = None


@dataclass
class RecurringGroup:
    """A detected recurring/EMI spend pattern."""

    description: str
    amount: float
    category: str
    kind: str  # "emi" | "recurring"
    interval_days: int
    occurrences: int
    confidence: float
    transaction_ids: list[int]
    first_date: date
    last_date: date
    next_expected_date: date


def normalize_description(description: str) -> str:
    """Lowercase and strip non-alphanumeric characters from a description."""
    cleaned = "".join(ch if ch.isalnum() else " " for ch in description.lower())
    return " ".join(cleaned.split())


def amounts_match(a: float, b: float) -> bool:
    """Return True when two amounts are stable enough to be the same charge.

    Tolerance is the larger of Rs 1.00 or 0.25% of the larger amount.
    """
    return abs(a - b) <= max(1.0, 0.0025 * max(abs(a), abs(b)))


def _detect_kind(description: str, amount: float, interval_days: int, category: str) -> str:
    normalized = normalize_description(description)
    if any(hint in normalized for hint in EMI_DESCRIPTION_HINTS):
        return "emi"
    if (
        interval_days == 30
        and abs(amount) >= EMI_AMOUNT_THRESHOLD
        and category not in EMI_EXCLUDED_CATEGORIES
    ):
        return "emi"
    return "recurring"


def _next_expected(last_date: date, interval_days: int, today: date) -> date:
    candidate = last_date + timedelta(days=interval_days)
    while candidate < today:
        candidate += timedelta(days=interval_days)
    return candidate


def _round_interval(interval_days: int) -> int:
    if 25 <= interval_days <= 35:
        return 30
    return interval_days


def detect_recurring(transactions: list[TransactionRef], today: date | None = None) -> list[RecurringGroup]:
    """Detect recurring/EMI patterns from a list of debit transactions."""
    today = today or date.today()

    buckets: list[list[TransactionRef]] = []
    for txn in transactions:
        if txn.transaction_type == "credit":
            continue  # credits are income, not spend
        if txn.transaction_type is None and txn.amount >= 0:
            continue  # legacy callers: positive amount means credit
        normalized = normalize_description(txn.description)
        if not normalized:
            continue
        placed = False
        for bucket in buckets:
            anchor = bucket[0]
            if normalize_description(anchor.description) == normalized and amounts_match(
                anchor.amount, txn.amount
            ):
                bucket.append(txn)
                placed = True
                break
        if not placed:
            buckets.append([txn])

    results: list[RecurringGroup] = []
    for bucket in buckets:
        bucket.sort(key=lambda t: t.date)
        distinct_months = {(t.date.year, t.date.month) for t in bucket}
        if len(distinct_months) < MIN_DISTINCT_MONTHS:
            continue

        intervals = [(b.date - a.date).days for a, b in zip(bucket, bucket[1:])]
        common_interval, _ = Counter(intervals).most_common(1)[0]
        if not (MIN_MONTHLY_INTERVAL_DAYS <= common_interval <= MAX_MONTHLY_INTERVAL_DAYS):
            continue

        interval_days = _round_interval(common_interval)
        occurrences = len(bucket)
        confidence = min(0.95, 0.55 + 0.1 * (len(distinct_months) - 1))

        anchor = bucket[0]
        last = bucket[-1]
        results.append(
            RecurringGroup(
                description=anchor.description,
                amount=abs(anchor.amount),
                category=anchor.category,
                kind=_detect_kind(anchor.description, anchor.amount, interval_days, anchor.category),
                interval_days=interval_days,
                occurrences=occurrences,
                confidence=round(confidence, 2),
                transaction_ids=[t.id for t in bucket],
                first_date=bucket[0].date,
                last_date=last.date,
                next_expected_date=_next_expected(last.date, interval_days, today),
            )
        )

    results.sort(key=lambda g: g.next_expected_date)
    return results


def mark_recurring_transaction_ids(results: list[RecurringGroup]) -> set[int]:
    """Return the flat set of transaction ids belonging to detected groups."""
    return {txn_id for group in results for txn_id in group.transaction_ids}