package com.arthapilot.app.ui.insights

import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.CategoryAmount
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads summary trends and breakdown`() = runTest(dispatcher) {
        val repository = FakeInsightsRepository(
            summary = InsightsSummary(
                month = "2026-07",
                income = 100.0,
                expenses = 40.0,
                savings = 60.0,
                savingsRate = 0.6,
                transactionCount = 5,
                topCategories = listOf(CategoryAmount("Rent", 25.0, 62.5)),
            ),
            trends = listOf(MonthTrend("2026-07", 100.0, 40.0, 60.0)),
            breakdown = listOf(CategoryBreakdownItem("Rent", 25.0, 62.5, 1)),
        )
        val viewModel = InsightsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(100.0, state.summary!!.income, 0.0)
        assertEquals(1, state.trends.size)
        assertEquals(1, state.breakdown.size)
        assertNull(state.error)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `network failure marks offline with message`() = runTest(dispatcher) {
        val viewModel = InsightsViewModel(FakeInsightsRepository(fail = ApiException.Network()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.offline)
        assertEquals("Cannot reach the server. Check your connection.", state.error)
        assertNull(state.summary)
    }

    @Test
    fun `error dismissal clears message`() = runTest(dispatcher) {
        val viewModel = InsightsViewModel(FakeInsightsRepository(fail = ApiException.Network()))
        advanceUntilIdle()

        viewModel.onErrorDismissed()
        assertNull(viewModel.uiState.value.error)
    }

    private class FakeInsightsRepository(
        val summary: InsightsSummary? = null,
        val trends: List<MonthTrend> = emptyList(),
        val breakdown: List<CategoryBreakdownItem> = emptyList(),
        val fail: ApiException? = null,
    ) : InsightsRepository {
        override suspend fun fetchCategories(): List<String> = emptyList()

        override suspend fun fetchSummary(month: String?): InsightsSummary {
            fail?.let { throw it }
            return summary ?: throw UnsupportedOperationException()
        }

        override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> {
            fail?.let { throw it }
            return trends
        }

        override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> {
            fail?.let { throw it }
            return breakdown
        }

        override suspend fun fetchRecurring(): List<RecurringItem> = emptyList()

        override suspend fun fetchRiskProfile(): RiskProfile =
            throw UnsupportedOperationException()
    }
}
