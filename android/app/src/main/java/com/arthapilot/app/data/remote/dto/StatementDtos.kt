package com.arthapilot.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class StatementUploadResponseDto(
    val id: Long,
    val filename: String,
    @SerializedName("file_type") val fileType: String,
    @SerializedName("bank_name") val bankName: String?,
    @SerializedName("account_number") val accountNumber: String?,
    @SerializedName("period_start") val periodStart: String?,
    @SerializedName("period_end") val periodEnd: String?,
    @SerializedName("parse_status") val parseStatus: String,
    @SerializedName("transaction_count") val transactionCount: Int,
    @SerializedName("error_message") val errorMessage: String?,
    val transactions: List<TransactionDto> = emptyList(),
)
