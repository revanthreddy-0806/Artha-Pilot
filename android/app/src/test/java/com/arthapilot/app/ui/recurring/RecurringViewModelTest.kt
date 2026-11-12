package com.arthapilot.app.ui.recurring

import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.InsightsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.arthapilot.app.domain.model.ApiException
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
class RecurringViewModelTest {

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
    fun `init loads recurring items`() = runTest(dispatcher) {
        val repository = FakeInsightsRepository(
            recurring = listOf(recurringItem("RENT PAYMENT", "recurring"), recurringItem("SBI CAR LOAN EMI", "emi")),
        )
        val viewModel = RecurringViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.items.size)
        assertEquals("emi", state.items[1].kind)
        assertNull(state.error)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `network failure marks offline`() = runTest(dispatcher) {
        val viewModel = RecurringViewModel(FakeInsightsRepository(fail = ApiException.Network()))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
    }

    @Test
    fun `invalidation triggers a fresh recurring request with updated items`() = runTest(dispatcher) {
        val repository = CountingRecurringRepository(
            initial = listOf(recurringItem("RENT PAYMENT", "recurring")),
        )
        val bus = FakeDataInvalidationBus()
        val viewModel = RecurringViewModel(repository, bus)
        advanceUntilIdle()

        assertEquals(1, repository.fetchCount)
        assertEquals(1, viewModel.uiState.value.items.size)

        repository.nextItems = listOf(
            recurringItem("RENT PAYMENT", "recurring"),
            recurringItem("SBI CAR LOAN EMI", "emi"),
        )

        bus.notifyDataChanged()
        advanceUntilIdle()

        assertEquals(2, repository.fetchCount)
        assertEquals(2, viewModel.uiState.value.items.size)
        assertEquals("emi", viewModel.uiState.value.items[1].kind)
    }

    @Test
    fun `failed recurring refresh shows error state`() = runTest(dispatcher) {
        val viewModel = RecurringViewModel(FakeInsightsRepository(fail = ApiException.Network()), FakeDataInvalidationBus())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
    }

    @Test
    fun `repeated collection does not create an uncontrolled refresh loop`() = runTest(dispatcher) {
        val repository = CountingRecurringRepository()
        val viewModel = RecurringViewModel(repository, NoOpDataInvalidationBus)
        advanceUntilIdle()

        val countAfterInit = repository.fetchCount
        advanceUntilIdle()
        advanceUntilIdle()

        assertEquals(countAfterInit, repository.fetchCount)
    }

    private fun recurringItem(description: String, kind: String) = RecurringItem(
        transactionId = 1,
        description = description,
        amount = 12000.0,
        category = "Bills & Recurring",
        kind = kind,
        intervalDays = 30,
        occurrences = 3,
        confidence = 0.75,
        firstDate = "2026-05-05",
        lastDate = "2026-07-05",
        nextExpectedDate = "2026-08-04",
    )

    private class CountingRecurringRepository(
        initial: List<RecurringItem> = emptyList(),
        val fail: ApiException? = null,
    ) : InsightsRepository {
        var nextItems: List<RecurringItem> = initial
        var fetchCount: Int = 0

        override suspend fun fetchCategories(): List<String> = emptyList()

        override suspend fun fetchSummary(month: String?): InsightsSummary =
            throw UnsupportedOperationException()

        override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> =
            throw UnsupportedOperationException()

        override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> =
            throw UnsupportedOperationException()

        override suspend fun fetchRecurring(): List<RecurringItem> {
            fail?.let { throw it }
            fetchCount += 1
            return nextItems
        }

        override suspend fun fetchRiskProfile(): RiskProfile =
            throw UnsupportedOperationException()

        override suspend fun fetchRiskQuestions(): List<com.arthapilot.app.domain.model.RiskQuestion> =
            throw UnsupportedOperationException()

        override suspend fun submitRiskQuestionnaire(answers: Map<String, String>): RiskProfile =
            throw UnsupportedOperationException()
    }

    private class FakeDataInvalidationBus : DataInvalidationBus {
        private val _revision = MutableStateFlow(0L)
        override val revision: StateFlow<Long> = _revision.asStateFlow()
        override fun notifyDataChanged() {
            _revision.value = _revision.value + 1
        }

        override fun reset() {
            _revision.value = 0L
        }
    }

    private class FakeInsightsRepository(
        val recurring: List<RecurringItem> = emptyList(),
        val fail: ApiException? = null,
    ) : InsightsRepository {
        override suspend fun fetchCategories(): List<String> = emptyList()

        override suspend fun fetchSummary(month: String?): InsightsSummary =
            throw UnsupportedOperationException()

        override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> =
            throw UnsupportedOperationException()

        override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> =
            throw UnsupportedOperationException()

        override suspend fun fetchRecurring(): List<RecurringItem> {
            fail?.let { throw it }
            return recurring
        }

        override suspend fun fetchRiskProfile(): RiskProfile =
            throw UnsupportedOperationException()

        override suspend fun fetchRiskQuestions(): List<com.arthapilot.app.domain.model.RiskQuestion> =
            throw UnsupportedOperationException()

        override suspend fun submitRiskQuestionnaire(answers: Map<String, String>): RiskProfile =
            throw UnsupportedOperationException()
    }
}
