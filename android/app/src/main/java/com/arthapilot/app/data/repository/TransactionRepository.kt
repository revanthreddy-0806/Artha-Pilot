package com.arthapilot.app.data.repository

import com.arthapilot.app.data.local.TransactionDao
import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.dto.CategoryUpdateDto
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.data.remote.toEntity
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.domain.model.TransactionPage
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
    ): TransactionPage

    suspend fun updateCategory(id: Long, category: String): Transaction
}

@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
    private val transactionDao: TransactionDao,
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
    ): TransactionPage {
        val result = try {
            api.getTransactions(page, pageSize, category, transactionType, startDate, endDate)
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
        transactionDao.upsertAll(result.items.map { it.toEntity() })
        return result.toDomain()
    }

    override suspend fun updateCategory(id: Long, category: String): Transaction {
        val updated = try {
            api.updateCategory(id, CategoryUpdateDto(category))
        } catch (t: Throwable) {
            throw ApiErrorMapper.map(t)
        }
        transactionDao.updateCategory(id, updated.category)
        return updated.toDomain()
    }
}
