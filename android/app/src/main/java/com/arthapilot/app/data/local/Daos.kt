package com.arthapilot.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface StatementDao {

    @Upsert
    suspend fun upsert(statement: StatementEntity)

    @Upsert
    suspend fun upsertAll(statements: List<StatementEntity>)

    @Query("SELECT * FROM statements ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StatementEntity>>
}

@Dao
interface PendingChangeDao {

    @Insert
    suspend fun insert(change: PendingChangeEntity)

    @Query("SELECT * FROM pending_changes ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingChangeEntity>

    @Query("DELETE FROM pending_changes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_changes WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)
}
