package com.arthapilot.app.data.repository

import com.arthapilot.app.data.local.PendingChangeDao
import com.arthapilot.app.data.local.PendingChangeEntity
import com.arthapilot.app.data.local.TransactionDao
import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.dto.CategoryUpdateDto
import com.arthapilot.app.data.remote.dto.TransactionCreateDto
import com.arthapilot.app.data.remote.dto.TransactionUpdateDto
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.data.remote.toEntity
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.domain.model.TransactionPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface TransactionRepository {
    /** Locally cached transactions (offline view of the latest fetch). */
    val cachedTransactions: Flow<List<Transaction>>

    /**
     * Online-first fetch: loads a page from the API and mirrors it into the
     * Room cache. Failures surface as [ApiException] for the UI to decide
     * whether to fall back to the cache.
     */
    suspend fun fetchTransactions(
        page: Int,
        pageSize: Int,
        category: String?,
        transactionType: String?,
        startDate: String?,
        endDate: String?,
        search: String? = null,
        minAmount: Double? = null,
        maxAmount: Double? = null,
        statementId: Long? = null,
        sortBy: String? = null,
        sortDir: String? = null,
    ): TransactionPage

    /**
     * Updates a transaction's category. When the network is unavailable the
     * change is queued locally (optimistically applied) and synced on the
     * next successful fetch.
     */
    suspend fun updateCategory(id: Long, category: String): Transaction

    suspend fun createTransaction(
        transactionType: String,
        amount: Double,
        date: String,
        description: String,
        category: String,
        balance: Double?,
    ): Transaction

    suspend fun updateTransaction(
        id: Long,
        transactionType: String?,
        amount: Double?,
        date: String?,
        description: String?,
        category: String?,
        balance: Double?,
    ): Transaction

    suspend fun deleteTransaction(id: Long)
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
    private val transactionDao: TransactionDao,
    private val pendingChangeDao: PendingChangeDao,
) : TransactionRepository {

    override val cachedTransactions: Flow<List<Transaction>> =
        transactionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun fetchTransactions(
        page: Int,
        pageSize: Int,
        category: String?,
        transactionType: String?,
        startDate: String?,
        endDate: String?,
        search: String?,
        minAmount: Double?,
        maxAmount: Double?,
        statementId: Long?,
        sortBy: String?,
        sortDir: String?,
    ): TransactionPage {
        flushPendingChanges()
        val result = try {
            api.getTransactions(
                page = page,
                pageSize = pageSize,
                category = category,
                transactionType = transactionType,
                startDate = startDate,
                endDate = endDate,
                q = search,
                minAmount = minAmount,
                maxAmount = maxAmount,
                statementId = statementId,
                sortBy = sortBy,
                sortDir = sortDir,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
        transactionDao.upsertAll(result.items.map { it.toEntity() })
        return result.toDomain()
    }

    override suspend fun updateCategory(id: Long, category: String): Transaction {
        try {
            val updated = api.updateCategory(id, CategoryUpdateDto(category))
            pendingChangeDao.deleteForTransaction(id)
            transactionDao.updateCategory(id, updated.category)
            return updated.toDomain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException.Network) {
            pendingChangeDao.insert(
                PendingChangeEntity(
                    transactionId = id,
                    newCategory = category,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            transactionDao.updateCategory(id, category)
            return transactionDao.getById(id)?.toDomain()
                ?: Transaction(
                    id = id,
                    statementId = 0,
                    date = "",
                    description = "",
                    amount = 0.0,
                    transactionType = "debit",
                    balance = null,
                    category = category,
                    confidence = null,
                    isRecurring = false,
                )
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
    }

    override suspend fun createTransaction(
        transactionType: String,
        amount: Double,
        date: String,
        description: String,
        category: String,
        balance: Double?,
    ): Transaction {
        val result = try {
            api.createTransaction(
                TransactionCreateDto(
                    transactionType = transactionType,
                    amount = amount,
                    date = date,
                    description = description,
                    category = category,
                    balance = balance,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
        transactionDao.upsert(result.toEntity())
        return result.toDomain()
    }

    override suspend fun updateTransaction(
        id: Long,
        transactionType: String?,
        amount: Double?,
        date: String?,
        description: String?,
        category: String?,
        balance: Double?,
    ): Transaction {
        val result = try {
            api.updateTransaction(
                id,
                TransactionUpdateDto(
                    transactionType = transactionType,
                    amount = amount,
                    date = date,
                    description = description,
                    category = category,
                    balance = balance,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
        transactionDao.upsert(result.toEntity())
        return result.toDomain()
    }

    override suspend fun deleteTransaction(id: Long) {
        try {
            api.deleteTransaction(id)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
        transactionDao.deleteById(id)
    }

    /** Best-effort replay of queued category changes; failures stay queued. */
    private suspend fun flushPendingChanges() {
        for (change in pendingChangeDao.getAll()) {
            try {
                api.updateCategory(change.transactionId, CategoryUpdateDto(change.newCategory))
                pendingChangeDao.delete(change.id)
                transactionDao.updateCategory(change.transactionId, change.newCategory)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the pending change for the next sync attempt.
            }
        }
    }
}
