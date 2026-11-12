package com.arthapilot.app.domain.model

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "bearer",
)

data class User(
    val id: Long,
    val email: String,
    val fullName: String,
    val isActive: Boolean,
    val createdAt: String,
)

data class Transaction(
    val id: Long,
    val statementId: Long?,
    val date: String,
    val description: String,
    val amount: Double,
    val transactionType: String,
    val balance: Double?,
    val category: String,
    val confidence: Double?,
    val isRecurring: Boolean,
)

data class TransactionPage(
    val items: List<Transaction>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

data class StatementPage(
    val items: List<StatementInfo>,
    val total: Int,
)

data class StatementUploadResult(
    val id: Long,
    val filename: String,
    val bankName: String?,
    val parseStatus: String,
    val transactionCount: Int,
    val transactions: List<Transaction>,
)

data class StatementInfo(
    val id: Long,
    val filename: String,
    val fileType: String,
    val bankName: String?,
    val periodStart: String?,
    val periodEnd: String?,
    val parseStatus: String,
    val transactionCount: Int,
    val errorMessage: String?,
    val createdAt: String,
)

data class Budget(
    val category: String,
    val monthlyLimit: Double,
    val spent: Double,
    val percentage: Double,
    val overrun: Boolean,
)

data class CategoryAmount(
    val category: String,
    val amount: Double,
    val percentage: Double,
)

data class InsightsSummary(
    val month: String,
    val income: Double,
    val expenses: Double,
    val savings: Double,
    val savingsRate: Double?,
    val transactionCount: Int,
    val topCategories: List<CategoryAmount>,
)

data class MonthTrend(
    val month: String,
    val income: Double,
    val expenses: Double,
    val savings: Double,
)

data class CategoryBreakdownItem(
    val category: String,
    val amount: Double,
    val percentage: Double,
    val transactionCount: Int,
)

data class RecurringItem(
    val transactionId: Long,
    val description: String,
    val amount: Double,
    val category: String,
    val kind: String,
    val intervalDays: Int,
    val occurrences: Int,
    val confidence: Double,
    val firstDate: String,
    val lastDate: String,
    val nextExpectedDate: String,
)

data class RiskFactor(
    val name: String,
    val value: String,
    val impact: String,
)

data class RiskProfile(
    val score: Int,
    val band: String,
    val summary: String,
    val factors: List<RiskFactor>,
    val income: Double?,
    val monthlyExpenses: Double,
    val savingsRate: Double?,
    val emiLoadRatio: Double?,
    val safeEmiAmount: Double,
    val maxLoanAmount: Double?,
    val maxLoanTenureMonths: Int,
    val toleranceScore: Int? = null,
    val toleranceBand: String? = null,
    val behaviorScore: Int = 0,
    val questionnaireCompleted: Boolean = false,
)

data class RiskQuestionOption(
    val id: String,
    val label: String,
)

data class RiskQuestion(
    val id: String,
    val text: String,
    val options: List<RiskQuestionOption>,
)

data class AllocationItem(
    val assetClass: String,
    val label: String,
    val percentage: Int,
    val rationale: String,
)

data class MarketQuote(
    val symbol: String,
    val name: String,
    val price: Double,
    val changePct: Double?,
    val asOf: String,
    val source: String,
    val status: String, // "live" | "cached" | "unavailable"
)

data class RecommendationContext(
    val monthsUsed: Int,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val savingsRate: Double?,
    val investableMonthly: Double,
)

data class RecommendationNote(
    val kind: String, // "caution" | "info"
    val text: String,
)

data class Recommendation(
    val status: String, // "ok" | "insufficient"
    val riskScore: Int,
    val riskBand: String,
    val toleranceScore: Int?,
    val behaviorScore: Int,
    val insufficientReason: String?,
    val context: RecommendationContext?,
    val allocation: List<AllocationItem>,
    val allocationSummary: String?,
    val notes: List<RecommendationNote>,
    val market: List<MarketQuote>,
    val educationalDisclaimer: String,
)

data class WhatIfBlock(
    val monthlyExpenses: Double,
    val monthlySavings: Double,
    val savingsRate: Double?,
)

data class WhatIfImpact(
    val monthlyExpenseReduction: Double,
    val additionalMonthlyInvestable: Double,
    val annualisedExtraInvestable: Double,
)

data class WhatIfResult(
    val category: String,
    val monthlyReduction: Double,
    val monthsUsed: Int,
    val currentMonthlySpend: Double,
    val monthlyIncome: Double,
    val original: WhatIfBlock,
    val simulated: WhatIfBlock,
    val impact: WhatIfImpact,
    val narrative: String,
    val isSimulation: Boolean,
    val educationalDisclaimer: String,
)

data class AssistantReply(
    val answer: String,
    val disclaimer: String,
)

data class ChatTurn(
    val role: String,
    val content: String,
)

data class Alert(
    val title: String,
    val message: String,
    val severity: String, // "warning" | "info"
)
