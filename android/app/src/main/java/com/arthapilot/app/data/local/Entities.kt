package com.arthapilot.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index("statementId")])
data class TransactionEntity(
    @PrimaryKey val id: Long,
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

@Entity(tableName = "statements")
data class StatementEntity(
    @PrimaryKey val id: Long,
    val filename: String,
    val fileType: String,
    val bankName: String?,
    val periodStart: String?,
    val periodEnd: String?,
    val parseStatus: String,
    val transactionCount: Int,
    val createdAt: String,
)

@Entity(tableName = "pending_changes")
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val newCategory: String,
    val createdAt: Long,
)
