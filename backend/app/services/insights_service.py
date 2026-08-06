"""Insights aggregation: monthly summaries, trends, budgets, recurring spend, risk."""
import logging
import statistics
from datetime import date, datetime

from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.models.budget import Budget
from app.models.transaction import Transaction
from app.repositories.budget_repository import BudgetRepository
from app.repositories.transaction_repository import TransactionRepository
from app.schemas.budget import BudgetListResponse, BudgetOut
from app.schemas.insights import (
    CategoryAmount,
    CategoryBreakdownItem,
    CategoryBreakdownResponse,
    InsightsSummary,
    MonthTrend,
    MonthlyTrendsResponse,
    RecurringItem,
    RecurringListResponse,
    RiskFactor,
    RiskProfile,
)
from app.services.recurring_detector import TransactionRef, detect_recurring
from app.services.risk_service import (
    RiskMetrics,
    build_factors,
    build_summary,
    max_loan_amount,
    risk_band,
    safe_emi_amount,
    score_risk,
)

logger = logging.getLogger(__name__)

TOP_CATEGORY_LIMIT = 5


def current_month() -> str:
    today = date.today()
    return f"{today.year:04d}-{today.month:02d}"


def _month_bounds(month: str) -> tuple[date, date]:
    try:
        parsed = datetime.strptime(month, "%Y-%m").date()
    except ValueError:
        raise AppError(
            "month must be in YYYY-MM format",
            status_code=422,
            error_code="invalid_month",
        ) from None
    start = parsed.replace(day=1)
    if parsed.month == 12:
        end = date(parsed.year + 1, 1, 1)
    else:
        end = date(parsed.year, parsed.month + 1, 1)
    return start, end


def _shift_first_of_month(reference: date, months_back: int) -> date:
    month_index = reference.year * 12 + (reference.month - 1) - months_back
    return date(month_index // 12, month_index % 12 + 1, 1)


def _aggregate(txns: list[Transaction]) -> tuple[float, float, int]:
    income = sum(t.amount for t in txns if t.transaction_type == "credit")
    expenses = sum(-t.amount for t in txns if t.transaction_type == "debit")
    return income, expenses, len(txns)


def _bucket_by_month(txns: list[Transaction]) -> dict[tuple[int, int], list[Transaction]]:
    buckets: dict[tuple[int, int], list[Transaction]] = {}
    for txn in txns:
        buckets.setdefault((txn.date.year, txn.date.month), []).append(txn)
    return buckets


def _expense_by_category(txns: list[Transaction]) -> dict[str, float]:
    by_category: dict[str, float] = {}
    for txn in txns:
        if txn.transaction_type != "debit":
            continue
        by_category[txn.category] = by_category.get(txn.category, 0.0) + -txn.amount
    return by_category


class InsightsService:
    def __init__(self, db: Session) -> None:
        self._transactions = TransactionRepository(db)
        self._budgets = BudgetRepository(db)

    # ---------- Summary ----------
    def summary(self, user_id: int, month: str) -> InsightsSummary:
        start, end = _month_bounds(month)
        txns = self._transactions.all_for_user(user_id, start_date=start, end_date=end)
        income, expenses, count = _aggregate(txns)
        savings = income - expenses
        savings_rate = (savings / income) if income > 0 else None

        by_category = _expense_by_category(txns)
        total_expenses = sum(by_category.values())
        top = [
            CategoryAmount(
                category=category,
                amount=amount,
                percentage=round(amount / total_expenses * 100, 1) if total_expenses else 0.0,
            )
            for category, amount in sorted(by_category.items(), key=lambda item: item[1], reverse=True)[
                :TOP_CATEGORY_LIMIT
            ]
        ]
        return InsightsSummary(
            month=month,
            income=round(income, 2),
            expenses=round(expenses, 2),
            savings=round(savings, 2),
            savings_rate=round(savings_rate, 4) if savings_rate is not None else None,
            transaction_count=count,
            top_categories=top,
        )

    # ---------- Trends ----------
    def monthly_trends(self, user_id: int, months: int) -> MonthlyTrendsResponse:
        months = max(1, min(months, 24))
        today = date.today()
        current = today.replace(day=1)
        start = _shift_first_of_month(today, months - 1)
        txns = self._transactions.all_for_user(user_id, start_date=start)
        buckets = _bucket_by_month(txns)

        trends: list[MonthTrend] = []
        for offset in range(months - 1, -1, -1):
            month_first = _shift_first_of_month(today, offset)
            label = f"{month_first.year:04d}-{month_first.month:02d}"
            income, expenses, _ = _aggregate(buckets.get((month_first.year, month_first.month), []))
            trends.append(
                MonthTrend(
                    month=label,
                    income=round(income, 2),
                    expenses=round(expenses, 2),
                    savings=round(income - expenses, 2),
                )
            )
        return MonthlyTrendsResponse(months=trends)

    # ---------- Category breakdown ----------
    def category_breakdown(self, user_id: int, month: str) -> CategoryBreakdownResponse:
        start, end = _month_bounds(month)
        txns = self._transactions.all_for_user(user_id, start_date=start, end_date=end)
        by_category = _expense_by_category(txns)
        total_expenses = sum(by_category.values())

        counts: dict[str, int] = {}
        for txn in txns:
            if txn.transaction_type == "debit":
                counts[txn.category] = counts.get(txn.category, 0) + 1

        items = [
            CategoryBreakdownItem(
                category=category,
                amount=round(amount, 2),
                percentage=round(amount / total_expenses * 100, 1) if total_expenses else 0.0,
                transaction_count=counts.get(category, 0),
            )
            for category, amount in sorted(by_category.items(), key=lambda item: item[1], reverse=True)
        ]
        return CategoryBreakdownResponse(month=month, categories=items)

    # ---------- Budgets ----------
    def budget_status(self, user_id: int, month: str) -> BudgetListResponse:
        start, end = _month_bounds(month)
        txns = self._transactions.all_for_user(user_id, start_date=start, end_date=end)
        spent_by_category = _expense_by_category(txns)

        budgets = self._budgets.list_for_user(user_id)
        items = [
            BudgetOut(
                category=budget.category,
                monthly_limit=budget.monthly_limit,
                spent=round(spent_by_category.get(budget.category, 0.0), 2),
                percentage=round(spent_by_category.get(budget.category, 0.0) / budget.monthly_limit * 100, 1),
                overrun=spent_by_category.get(budget.category, 0.0) > budget.monthly_limit,
            )
            for budget in budgets
        ]
        return BudgetListResponse(items=items, total=len(items))

    # ---------- Recurring / EMI ----------
    def recurring(self, user_id: int) -> RecurringListResponse:
        txns = self._transactions.all_for_user(user_id)
        refs = [
            TransactionRef(id=t.id, date=t.date, amount=t.amount, description=t.description, category=t.category)
            for t in txns
        ]
        groups = detect_recurring(refs)
        items = [
            RecurringItem(
                transaction_id=group.transaction_ids[-1],
                description=group.description,
                amount=round(group.amount, 2),
                category=group.category,
                kind=group.kind,
                interval_days=group.interval_days,
                occurrences=group.occurrences,
                confidence=group.confidence,
                first_date=group.first_date,
                last_date=group.last_date,
                next_expected_date=group.next_expected_date,
            )
            for group in groups
        ]
        return RecurringListResponse(items=items)

    # ---------- Risk profile ----------
    def risk_profile(self, user_id: int) -> RiskProfile:
        today = date.today()
        txns = self._transactions.all_for_user(
            user_id, start_date=_shift_first_of_month(today, 2)
        )
        months_of_data = len(_bucket_by_month(txns))
        has_activity = months_of_data > 0

        monthly_income: float | None = None
        months_with_income = 0
        income_values: list[float] = []
        expense_values: list[float] = []
        for bucket in _bucket_by_month(txns).values():
            income, expenses, _ = _aggregate(bucket)
            expense_values.append(expenses)
            if income > 0:
                monthly_income = income
                months_with_income += 1
                income_values.append(income)

        monthly_expenses = statistics.mean(expense_values) if expense_values else 0.0
        income_std_ratio = None
        if len(income_values) >= 2:
            mean = statistics.mean(income_values)
            if mean > 0:
                income_std_ratio = statistics.stdev(income_values) / mean

        savings_rate = ((monthly_income - monthly_expenses) / monthly_income) if monthly_income else None
        utilization = (monthly_expenses / monthly_income) if monthly_income else None

        recurring = self.recurring(user_id)
        emi_monthly_amount = sum(item.amount for item in recurring.items if item.kind == "emi")
        emi_load_ratio = (emi_monthly_amount / monthly_income) if monthly_income else None

        metrics = RiskMetrics(
            has_activity=has_activity,
            months_of_data=months_of_data,
            monthly_income=monthly_income,
            months_with_income=months_with_income,
            income_std_ratio=income_std_ratio,
            monthly_expenses=monthly_expenses,
            savings_rate=savings_rate,
            utilization=utilization,
            emi_load_ratio=emi_load_ratio,
            emi_monthly_amount=emi_monthly_amount,
        )
        score = score_risk(metrics)
        band = risk_band(score)
        safe_emi = safe_emi_amount(metrics)
        loan = max_loan_amount(safe_emi)
        return RiskProfile(
            score=score,
            band=band,
            summary=build_summary(metrics, band),
            factors=[RiskFactor(**f) for f in build_factors(metrics, score)],
            income=round(monthly_income, 2) if monthly_income is not None else None,
            monthly_expenses=round(monthly_expenses, 2),
            savings_rate=round(savings_rate, 4) if savings_rate is not None else None,
            emi_load_ratio=round(emi_load_ratio, 4) if emi_load_ratio is not None else None,
            safe_emi_amount=round(safe_emi, 2),
            max_loan_amount=loan,
            max_loan_tenure_months=60,
        )
