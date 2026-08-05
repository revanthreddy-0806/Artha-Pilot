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
    val statementId: Long,
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

data class StatementUploadResult(
    val id: Long,
    val filename: String,
    val bankName: String?,
    val parseStatus: String,
    val transactionCount: Int,
    val transactions: List<Transaction>,
)
