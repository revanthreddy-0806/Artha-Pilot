package com.arthapilot.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AllocationItemDto(
    @SerializedName("asset_class") val assetClass: String,
    val label: String,
    val percentage: Int,
    val rationale: String,
)

data class MarketQuoteDto(
    val symbol: String,
    val name: String,
    val price: Double,
    @SerializedName("change_pct") val changePct: Double?,
    @SerializedName("as_of") val asOf: String,
    val source: String,
    val status: String,
)

data class RecommendationContextDto(
    @SerializedName("months_used") val monthsUsed: Int,
    @SerializedName("monthly_income") val monthlyIncome: Double,
    @SerializedName("monthly_expenses") val monthlyExpenses: Double,
    @SerializedName("savings_rate") val savingsRate: Double?,
    @SerializedName("investable_monthly") val investableMonthly: Double,
)

data class RecommendationNoteDto(
    val kind: String,
    val text: String,
)

data class RecommendationDto(
    val status: String,
    @SerializedName("risk_score") val riskScore: Int,
    @SerializedName("risk_band") val riskBand: String,
    @SerializedName("tolerance_score") val toleranceScore: Int?,
    @SerializedName("behavior_score") val behaviorScore: Int,
    @SerializedName("insufficient_reason") val insufficientReason: String?,
    val context: RecommendationContextDto?,
    val allocation: List<AllocationItemDto>,
    @SerializedName("allocation_summary") val allocationSummary: String?,
    val notes: List<RecommendationNoteDto>,
    val market: List<MarketQuoteDto>,
    @SerializedName("educational_disclaimer") val educationalDisclaimer: String,
)

data class WhatIfRequestDto(
    val category: String,
    @SerializedName("monthly_reduction") val monthlyReduction: Double,
)

data class WhatIfBlockDto(
    @SerializedName("monthly_expenses") val monthlyExpenses: Double,
    @SerializedName("monthly_savings") val monthlySavings: Double,
    @SerializedName("savings_rate") val savingsRate: Double?,
)

data class WhatIfImpactDto(
    @SerializedName("monthly_expense_reduction") val monthlyExpenseReduction: Double,
    @SerializedName("additional_monthly_investable") val additionalMonthlyInvestable: Double,
    @SerializedName("annualised_extra_investable") val annualisedExtraInvestable: Double,
)

data class WhatIfResultDto(
    val category: String,
    @SerializedName("monthly_reduction") val monthlyReduction: Double,
    @SerializedName("months_used") val monthsUsed: Int,
    @SerializedName("current_monthly_spend") val currentMonthlySpend: Double,
    @SerializedName("monthly_income") val monthlyIncome: Double,
    val original: WhatIfBlockDto,
    val simulated: WhatIfBlockDto,
    val impact: WhatIfImpactDto,
    val narrative: String,
    @SerializedName("is_simulation") val isSimulation: Boolean,
    @SerializedName("educational_disclaimer") val educationalDisclaimer: String,
)