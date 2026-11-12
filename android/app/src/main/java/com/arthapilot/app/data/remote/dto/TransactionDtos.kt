package com.arthapilot.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TransactionDto(
    val id: Long,
    @SerializedName("statement_id") val statementId: Long?,
    val date: String,
    val description: String,
    val amount: Double,
    @SerializedName("transaction_type") val transactionType: String,
    val balance: Double?,
    val category: String,
    val confidence: Double?,
    @SerializedName("is_recurring") val isRecurring: Boolean,
)

data class TransactionPageDto(
    val items: List<TransactionDto>,
    val total: Int,
    val page: Int,
    @SerializedName("page_size") val pageSize: Int,
)

data class CategoryUpdateDto(
    val category: String,
)

data class TransactionCreateDto(
    @SerializedName("transaction_type") val transactionType: String,
    val amount: Double,
    val date: String,
    val description: String,
    val category: String,
    val balance: Double? = null,
)

data class TransactionUpdateDto(
    @SerializedName("transaction_type") val transactionType: String? = null,
    val amount: Double? = null,
    val date: String? = null,
    val description: String? = null,
    val category: String? = null,
    val balance: Double? = null,
)
