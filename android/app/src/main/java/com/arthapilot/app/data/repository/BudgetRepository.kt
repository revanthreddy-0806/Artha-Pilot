package com.arthapilot.app.data.repository

import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.dto.BudgetUpsertDto
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Budget
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface BudgetRepository {
    suspend fun fetchBudgets(): List<Budget>
    suspend fun setBudget(category: String, monthlyLimit: Double): Budget
    suspend fun deleteBudget(category: String)
}

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
) : BudgetRepository {

    override suspend fun fetchBudgets(): List<Budget> = mapNetwork {
        api.getBudgets().items.map { it.toDomain() }
    }

    override suspend fun setBudget(category: String, monthlyLimit: Double): Budget = mapNetwork {
        api.setBudget(category, BudgetUpsertDto(monthlyLimit)).toDomain()
    }

    override suspend fun deleteBudget(category: String) {
        mapNetwork { api.deleteBudget(category) }
    }

    private suspend fun <T> mapNetwork(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
