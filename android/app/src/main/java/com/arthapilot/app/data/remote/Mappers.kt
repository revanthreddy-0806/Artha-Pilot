package com.arthapilot.app.data.remote

import com.arthapilot.app.data.local.StatementEntity
import com.arthapilot.app.data.local.TransactionEntity
import com.arthapilot.app.data.remote.dto.StatementUploadResponseDto
import com.arthapilot.app.data.remote.dto.TokenResponseDto
import com.arthapilot.app.data.remote.dto.TransactionDto
import com.arthapilot.app.data.remote.dto.TransactionPageDto
import com.arthapilot.app.data.remote.dto.UserDto
import com.arthapilot.app.domain.model.AuthTokens
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
    createdAt = "",
)
