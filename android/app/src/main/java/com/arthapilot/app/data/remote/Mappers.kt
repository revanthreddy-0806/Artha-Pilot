package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.StatementEntity
import com.arthapilot.app.data.local.TransactionEntity
import com.arthapilot.app.data.remote.dto.AllocationItemDto
import com.arthapilot.app.data.remote.dto.BreakdownItemDto
import com.arthapilot.app.data.remote.dto.BudgetDto
import com.arthapilot.app.data.remote.dto.CategoryAmountDto
import com.arthapilot.app.data.remote.dto.InsightsSummaryDto
import com.arthapilot.app.data.remote.dto.MarketQuoteDto
import com.arthapilot.app.data.remote.dto.MonthTrendDto
import com.arthapilot.app.data.remote.dto.RecommendationContextDto
import com.arthapilot.app.data.remote.dto.RecommendationDto
import com.arthapilot.app.data.remote.dto.RecommendationNoteDto
import com.arthapilot.app.data.remote.dto.RecurringItemDto
import com.arthapilot.app.data.remote.dto.RiskFactorDto
import com.arthapilot.app.data.remote.dto.RiskProfileDto
import com.arthapilot.app.data.remote.dto.RiskQuestionDto
import com.arthapilot.app.data.remote.dto.StatementItemDto
import com.arthapilot.app.data.remote.dto.StatementUploadResponseDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.arthapilot.app.data.remote.dto.TransactionDto
import com.arthapilot.app.data.remote.dto.TransactionPageDto
import com.arthapilot.app.data.remote.dto.UserDto
import com.arthapilot.app.data.remote.dto.WhatIfBlockDto
import com.arthapilot.app.data.remote.dto.WhatIfImpactDto
import com.arthapilot.app.data.remote.dto.WhatIfResultDto
import com.arthapilot.app.domain.model.AllocationItem
import com.arthapilot.app.domain.model.AuthTokens
import com.arthapilot.app.domain.model.Budget
import com.arthapilot.app.domain.model.CategoryAmount
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MarketQuote
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.Recommendation
import com.arthapilot.app.domain.model.RecommendationContext
import com.arthapilot.app.domain.model.RecommendationNote
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskFactor
import com.arthapilot.app.domain.model.RiskProfile
import com.arthapilot.app.domain.model.RiskQuestion
import com.arthapilot.app.domain.model.RiskQuestionOption
import com.arthapilot.app.domain.model.StatementInfo
import com.arthapilot.app.domain.model.StatementUploadResult
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.domain.model.TransactionPage
import com.arthapilot.app.domain.model.User
import com.arthapilot.app.domain.model.WhatIfBlock
import com.arthapilot.app.domain.model.WhatIfImpact
import com.arthapilot.app.domain.model.WhatIfResult

fun TokenResponseDto.toDomain() = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
    tokenType = tokenType,
)

fun UserDto.toDomain() = User(
    id = id,
    email = email,
    fullName = fullName,
    isActive = isActive,
    createdAt = createdAt,
)

fun TransactionDto.toDomain() = Transaction(
    id = id,
    statementId = statementId,
    date = date,
    description = description,
    amount = amount,
    transactionType = transactionType,
    balance = balance,
    category = category,
    confidence = confidence,
    isRecurring = isRecurring,
)

fun TransactionDto.toEntity() = TransactionEntity(
    id = id,
    statementId = statementId,
    date = date,
    description = description,
    amount = amount,
    transactionType = transactionType,
    balance = balance,
    category = category,
    confidence = confidence,
    isRecurring = isRecurring,
)

fun TransactionPageDto.toDomain() = TransactionPage(
    items = items.map { it.toDomain() },
    total = total,
    page = page,
    pageSize = pageSize,
)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    statementId = statementId,
    date = date,
    description = description,
    amount = amount,
    transactionType = transactionType,
    balance = balance,
    category = category,
    confidence = confidence,
    isRecurring = isRecurring,
)

fun StatementUploadResponseDto.toDomain() = StatementUploadResult(
    id = id,
    filename = filename,
    bankName = bankName,
    parseStatus = parseStatus,
    transactionCount = transactionCount,
    transactions = transactions.map { it.toDomain() },
)

fun StatementUploadResponseDto.toStatementEntity() = StatementEntity(
    id = id,
    filename = filename,
    fileType = fileType,
    bankName = bankName,
    periodStart = periodStart,
    periodEnd = periodEnd,
    parseStatus = parseStatus,
    transactionCount = transactionCount,
    createdAt = java.time.LocalDateTime.now().toString(),
)

fun StatementItemDto.toDomain() = StatementInfo(
    id = id,
    filename = filename,
    fileType = fileType,
    bankName = bankName,
    periodStart = periodStart,
    periodEnd = periodEnd,
    parseStatus = parseStatus,
    transactionCount = transactionCount,
    errorMessage = errorMessage,
    createdAt = createdAt,
)

fun StatementItemDto.toStatementEntity() = StatementEntity(
    id = id,
    filename = filename,
    fileType = fileType,
    bankName = bankName,
    periodStart = periodStart,
    periodEnd = periodEnd,
    parseStatus = parseStatus,
    transactionCount = transactionCount,
    createdAt = createdAt,
)

fun StatementEntity.toStatementInfo() = StatementInfo(
    id = id,
    filename = filename,
    fileType = fileType,
    bankName = bankName,
    periodStart = periodStart,
    periodEnd = periodEnd,
    parseStatus = parseStatus,
    transactionCount = transactionCount,
    errorMessage = null,
    createdAt = createdAt,
)

fun BudgetDto.toDomain() = Budget(
    category = category,
    monthlyLimit = monthlyLimit,
    spent = spent,
    percentage = percentage,
    overrun = overrun,
)

fun CategoryAmountDto.toDomain() = CategoryAmount(
    category = category,
    amount = amount,
    percentage = percentage,
)

fun InsightsSummaryDto.toDomain() = InsightsSummary(
    month = month,
    income = income,
    expenses = expenses,
    savings = savings,
    savingsRate = savingsRate,
    transactionCount = transactionCount,
    topCategories = topCategories.map { it.toDomain() },
)

fun MonthTrendDto.toDomain() = MonthTrend(
    month = month,
    income = income,
    expenses = expenses,
    savings = savings,
)

fun BreakdownItemDto.toDomain() = CategoryBreakdownItem(
    category = category,
    amount = amount,
    percentage = percentage,
    transactionCount = transactionCount,
)

fun RecurringItemDto.toDomain() = RecurringItem(
    transactionId = transactionId,
    description = description,
    amount = amount,
    category = category,
    kind = kind,
    intervalDays = intervalDays,
    occurrences = occurrences,
    confidence = confidence,
    firstDate = firstDate,
    lastDate = lastDate,
    nextExpectedDate = nextExpectedDate,
)

fun RiskFactorDto.toDomain() = RiskFactor(
    name = name,
    value = value,
    impact = impact,
)

fun RiskProfileDto.toDomain() = RiskProfile(
    score = score,
    band = band,
    summary = summary,
    factors = factors.map { it.toDomain() },
    income = income,
    monthlyExpenses = monthlyExpenses,
    savingsRate = savingsRate,
    emiLoadRatio = emiLoadRatio,
    safeEmiAmount = safeEmiAmount,
    maxLoanAmount = maxLoanAmount,
    maxLoanTenureMonths = maxLoanTenureMonths,
    toleranceScore = toleranceScore,
    toleranceBand = toleranceBand,
    behaviorScore = behaviorScore ?: 0,
    questionnaireCompleted = questionnaireCompleted == true,
)

fun RiskQuestionDto.toDomain() = RiskQuestion(
    id = id,
    text = text,
    options = options.map { RiskQuestionOption(id = it.id, label = it.label) },
)

fun AllocationItemDto.toDomain() = AllocationItem(
    assetClass = assetClass,
    label = label,
    percentage = percentage,
    rationale = rationale,
)

fun MarketQuoteDto.toDomain() = MarketQuote(
    symbol = symbol,
    name = name,
    price = price,
    changePct = changePct,
    asOf = asOf,
    source = source,
    status = status,
)

fun RecommendationContextDto.toDomain() = RecommendationContext(
    monthsUsed = monthsUsed,
    monthlyIncome = monthlyIncome,
    monthlyExpenses = monthlyExpenses,
    savingsRate = savingsRate,
    investableMonthly = investableMonthly,
)

fun RecommendationNoteDto.toDomain() = RecommendationNote(
    kind = kind,
    text = text,
)

fun RecommendationDto.toDomain() = Recommendation(
    status = status,
    riskScore = riskScore,
    riskBand = riskBand,
    toleranceScore = toleranceScore,
    behaviorScore = behaviorScore,
    insufficientReason = insufficientReason,
    context = context?.toDomain(),
    allocation = allocation.map { it.toDomain() },
    allocationSummary = allocationSummary,
    notes = notes.map { it.toDomain() },
    market = market.map { it.toDomain() },
    educationalDisclaimer = educationalDisclaimer,
)

fun WhatIfBlockDto.toDomain() = WhatIfBlock(
    monthlyExpenses = monthlyExpenses,
    monthlySavings = monthlySavings,
    savingsRate = savingsRate,
)

fun WhatIfImpactDto.toDomain() = WhatIfImpact(
    monthlyExpenseReduction = monthlyExpenseReduction,
    additionalMonthlyInvestable = additionalMonthlyInvestable,
    annualisedExtraInvestable = annualisedExtraInvestable,
)

fun WhatIfResultDto.toDomain() = WhatIfResult(
    category = category,
    monthlyReduction = monthlyReduction,
    monthsUsed = monthsUsed,
    currentMonthlySpend = currentMonthlySpend,
    monthlyIncome = monthlyIncome,
    original = original.toDomain(),
    simulated = simulated.toDomain(),
    impact = impact.toDomain(),
    narrative = narrative,
    isSimulation = isSimulation,
    educationalDisclaimer = educationalDisclaimer,
)
