package com.arthapilot.app.ui.recurring

import com.arthapilot.app.data.repository.InsightsRepository
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
