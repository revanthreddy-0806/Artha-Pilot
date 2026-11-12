package com.arthapilot.app.ui.insights

import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.InsightsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    @Test
    fun `invalidation triggers a fresh insights request with updated values`() = runTest(dispatcher) {
        val repository = CountingInsightsRepository(
            summary = InsightsSummary(
                month = "2026-07",
                income = 100.0,
                expenses = 40.0,
                savings = 60.0,
                savingsRate = 0.6,
                transactionCount = 5,
                topCategories = emptyList(),
            ),
            trends = listOf(MonthTrend("2026-07", 100.0, 40.0, 60.0)),
            breakdown = listOf(CategoryBreakdownItem("Rent", 25.0, 62.5, 1)),
        )
        val bus = FakeDataInvalidationBus()
        val viewModel = InsightsViewModel(repository, bus)
        advanceUntilIdle()

        assertEquals(1, repository.fetchCount)
        assertEquals(100.0, viewModel.uiState.value.summary!!.income, 0.0)

        repository.nextSummary = repository.summary.copy(income = 200.0, transactionCount = 9)
        repository.nextTrends = listOf(MonthTrend("2026-07", 200.0, 50.0, 150.0))
        repository.nextBreakdown = listOf(CategoryBreakdownItem("Groceries", 50.0, 25.0, 2))

        bus.notifyDataChanged()
        advanceUntilIdle()

        assertEquals(2, repository.fetchCount)
        assertEquals(200.0, viewModel.uiState.value.summary!!.income, 0.0)
        assertEquals(9, viewModel.uiState.value.summary!!.transactionCount)
        assertEquals(1, viewModel.uiState.value.trends.size)
        assertEquals(200.0, viewModel.uiState.value.trends[0].income, 0.0)
        assertEquals(1, viewModel.uiState.value.breakdown.size)
        assertEquals("Groceries", viewModel.uiState.value.breakdown[0].category)
    }

    @Test
    fun `failed refresh shows error state`() = runTest(dispatcher) {
        val repository = CountingInsightsRepository(fail = ApiException.Network())
        val bus = FakeDataInvalidationBus()
        val viewModel = InsightsViewModel(repository, bus)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
    }

    @Test
    fun `repeated collection does not create an uncontrolled refresh loop`() = runTest(dispatcher) {
        val repository = CountingInsightsRepository()
        val viewModel = InsightsViewModel(repository, NoOpDataInvalidationBus)
        advanceUntilIdle()

        val countAfterInit = repository.fetchCount
        advanceUntilIdle()
        advanceUntilIdle()

        assertEquals(countAfterInit, repository.fetchCount)
    }

    private class CountingInsightsRepository(
        summary: InsightsSummary? = null,
        trends: List<MonthTrend> = emptyList(),
        breakdown: List<CategoryBreakdownItem> = emptyList(),
        val fail: ApiException? = null,
    ) : InsightsRepository {
        var summary: InsightsSummary = summary ?: InsightsSummary(
            month = "2026-07",
            income = 100.0,
            expenses = 40.0,
            savings = 60.0,
            savingsRate = 0.6,
            transactionCount = 5,
            topCategories = emptyList(),
        )
        var nextSummary: InsightsSummary = this.summary
        var trends: List<MonthTrend> = trends.ifEmpty { listOf(MonthTrend("2026-07", 100.0, 40.0, 60.0)) }
        var nextTrends: List<MonthTrend> = this.trends
        var breakdown: List<CategoryBreakdownItem> = breakdown.ifEmpty { listOf(CategoryBreakdownItem("Rent", 25.0, 62.5, 1)) }
        var nextBreakdown: List<CategoryBreakdownItem> = this.breakdown
        var fetchCount: Int = 0

        override suspend fun fetchCategories(): List<String> = emptyList()

        override suspend fun fetchSummary(month: String?): InsightsSummary {
            fail?.let { throw it }
            fetchCount += 1
            summary = nextSummary
            return summary
        }

        override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> {
            fail?.let { throw it }
            trends = nextTrends
            return trends
        }

        override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> {
            fail?.let { throw it }
            breakdown = nextBreakdown
            return breakdown
        }

        override suspend fun fetchRecurring(): List<RecurringItem> = emptyList()

        override suspend fun fetchRiskProfile(): RiskProfile =
            throw UnsupportedOperationException()

        override suspend fun fetchRiskQuestions(): List<com.arthapilot.app.domain.model.RiskQuestion> =
            throw UnsupportedOperationException()

        override suspend fun submitRiskQuestionnaire(answers: Map<String, String>): RiskProfile =
            throw UnsupportedOperationException()
    }

    private class FakeDataInvalidationBus : DataInvalidationBus {
        private val _revision = MutableStateFlow(0L)
        override val revision = _revision.asStateFlow()
        override fun notifyDataChanged() {
            _revision.value = _revision.value + 1
        }

        override fun reset() {
            _revision.value = 0L
        }
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

        override suspend fun fetchRiskQuestions(): List<com.arthapilot.app.domain.model.RiskQuestion> =
            throw UnsupportedOperationException()

        override suspend fun submitRiskQuestionnaire(answers: Map<String, String>): RiskProfile =
            throw UnsupportedOperationException()
    }
}
