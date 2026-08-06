package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.StatementEntity
import com.arthapilot.app.data.local.TransactionEntity
import com.arthapilot.app.data.remote.dto.BreakdownItemDto
import com.arthapilot.app.data.remote.dto.BudgetDto
import com.arthapilot.app.data.remote.dto.CategoryAmountDto
import com.arthapilot.app.data.remote.dto.InsightsSummaryDto
import com.arthapilot.app.data.remote.dto.MonthTrendDto
import com.arthapilot.app.data.remote.dto.RecurringItemDto
import com.arthapilot.app.data.remote.dto.RiskFactorDto
import com.arthapilot.app.data.remote.dto.RiskProfileDto
import com.arthapilot.app.data.remote.dto.StatementItemDto
import com.arthapilot.app.data.remote.dto.StatementUploadResponseDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.arthapilot.app.data.remote.dto.TransactionDto
import com.arthapilot.app.data.remote.dto.TransactionPageDto
import com.arthapilot.app.data.remote.dto.UserDto
import com.arthapilot.app.domain.model.AuthTokens
import com.arthapilot.app.domain.model.Budget
import com.arthapilot.app.domain.model.CategoryAmount
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskFactor
import com.arthapilot.app.domain.model.RiskProfile
import com.arthapilot.app.domain.model.StatementInfo
import com.arthapilot.app.domain.model.StatementUploadResult
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.domain.model.TransactionPage
import com.arthapilot.app.domain.model.User

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
)
