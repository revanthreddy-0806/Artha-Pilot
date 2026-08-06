package com.arthapilot.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class CategoriesDto(
    val categories: List<String>,
)

data class StatementItemDto(
    val id: Long,
    val filename: String,
    @SerializedName("file_type") val fileType: String,
    @SerializedName("bank_name") val bankName: String?,
    @SerializedName("period_start") val periodStart: String?,
    @SerializedName("period_end") val periodEnd: String?,
    @SerializedName("parse_status") val parseStatus: String,
    @SerializedName("transaction_count") val transactionCount: Int,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("created_at") val createdAt: String,
)

data class StatementListDto(
    val items: List<StatementItemDto>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size") val pageSize: Int,
)

data class BudgetDto(
    val category: String,
    @SerializedName("monthly_limit") val monthlyLimit: Double,
    val spent: Double,
    val percentage: Double,
    val overrun: Boolean,
)

data class BudgetListDto(
    val items: List<BudgetDto>,
    val total: Int,
)

data class BudgetUpsertDto(
    @SerializedName("monthly_limit") val monthlyLimit: Double,
)

data class CategoryAmountDto(
    val category: String,
    val amount: Double,
    val percentage: Double,
)

data class InsightsSummaryDto(
    val month: String,
    val income: Double,
    val expenses: Double,
    val savings: Double,
    @SerializedName("savings_rate") val savingsRate: Double?,
    @SerializedName("transaction_count") val transactionCount: Int,
    @SerializedName("top_categories") val topCategories: List<CategoryAmountDto>,
)

data class MonthTrendDto(
    val month: String,
    val income: Double,
    val expenses: Double,
    val savings: Double,
)

data class MonthlyTrendsDto(
    val months: List<MonthTrendDto>,
)

data class BreakdownItemDto(
    val category: String,
    val amount: Double,
    val percentage: Double,
    @SerializedName("transaction_count") val transactionCount: Int,
)

data class CategoryBreakdownDto(
    val month: String,
    val categories: List<BreakdownItemDto>,
)

data class RecurringItemDto(
    @SerializedName("transaction_id") val transactionId: Long,
    val description: String,
    val amount: Double,
    val category: String,
    val kind: String,
    @SerializedName("interval_days") val intervalDays: Int,
    val occurrences: Int,
    val confidence: Double,
    @SerializedName("first_date") val firstDate: String,
    @SerializedName("last_date") val lastDate: String,
    @SerializedName("next_expected_date") val nextExpectedDate: String,
)

data class RecurringListDto(
    val items: List<RecurringItemDto>,
)

data class RiskFactorDto(
    val name: String,
    val value: String,
    val impact: String,
)

data class RiskProfileDto(
    val score: Int,
    val band: String,
    val summary: String,
    val factors: List<RiskFactorDto>,
    val income: Double?,
    @SerializedName("monthly_expenses") val monthlyExpenses: Double,
    @SerializedName("savings_rate") val savingsRate: Double?,
    @SerializedName("emi_load_ratio") val emiLoadRatio: Double?,
    @SerializedName("safe_emi_amount") val safeEmiAmount: Double,
    @SerializedName("max_loan_amount") val maxLoanAmount: Double?,
    @SerializedName("max_loan_tenure_months") val maxLoanTenureMonths: Int,
)
