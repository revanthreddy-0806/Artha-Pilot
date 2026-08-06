package com.arthapilot.app.data.repository

import com.arthapilot.app.data.remote.ApiErrorMapper
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.toDomain
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskProfile
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

interface InsightsRepository {
    suspend fun fetchCategories(): List<String>
    suspend fun fetchSummary(month: String?): InsightsSummary
    suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend>
    suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem>
    suspend fun fetchRecurring(): List<RecurringItem>
    suspend fun fetchRiskProfile(): RiskProfile
}

@Singleton
class InsightsRepositoryImpl @Inject constructor(
    private val api: ArthaApi,
) : InsightsRepository {

    override suspend fun fetchCategories(): List<String> = mapNetwork {
        api.getCategories().categories
    }

    override suspend fun fetchSummary(month: String?): InsightsSummary = mapNetwork {
        api.getInsightsSummary(month).toDomain()
    }

    override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> = mapNetwork {
        api.getMonthlyTrends(months).months.map { it.toDomain() }
    }

    override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> = mapNetwork {
        api.getCategoryBreakdown(month).categories.map { it.toDomain() }
    }

    override suspend fun fetchRecurring(): List<RecurringItem> = mapNetwork {
        api.getRecurring().items.map { it.toDomain() }
    }

    override suspend fun fetchRiskProfile(): RiskProfile = mapNetwork {
        api.getRiskProfile().toDomain()
    }

    private suspend fun <T> mapNetwork(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        throw ApiErrorMapper.map(t)
    }
}
