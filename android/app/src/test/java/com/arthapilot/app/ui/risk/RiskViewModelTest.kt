package com.arthapilot.app.ui.risk

import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskFactor
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
class RiskViewModelTest {

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
    fun `init loads risk profile`() = runTest(dispatcher) {
        val repository = FakeInsightsRepository(
            profile = RiskProfile(
                score = 82,
                band = "Excellent",
                summary = "Healthy",
                factors = listOf(RiskFactor("Savings rate", "60.0%", "positive")),
                income = 100000.0,
                monthlyExpenses = 40000.0,
                savingsRate = 0.6,
                emiLoadRatio = 0.1,
                safeEmiAmount = 30000.0,
                maxLoanAmount = 1398000.0,
                maxLoanTenureMonths = 60,
            ),
        )
        val viewModel = RiskViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(82, state.profile!!.score)
        assertEquals("Excellent", state.profile.band)
        assertNull(state.error)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `empty user gets poor profile`() = runTest(dispatcher) {
        val repository = FakeInsightsRepository(
            profile = RiskProfile(
                score = 0,
                band = "Poor",
                summary = "No data",
                factors = emptyList(),
                income = null,
                monthlyExpenses = 0.0,
                savingsRate = null,
                emiLoadRatio = null,
                safeEmiAmount = 0.0,
                maxLoanAmount = null,
                maxLoanTenureMonths = 60,
            ),
        )
        val viewModel = RiskViewModel(repository)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.profile!!.score)
        assertTrue(viewModel.uiState.value.profile!!.maxLoanAmount == null)
    }

    @Test
    fun `network failure marks offline with message`() = runTest(dispatcher) {
        val viewModel = RiskViewModel(FakeInsightsRepository(fail = ApiException.Network()))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
    }

    private class FakeInsightsRepository(
        val profile: RiskProfile? = null,
        val fail: ApiException? = null,
    ) : InsightsRepository {
        override suspend fun fetchCategories(): List<String> = emptyList()

        override suspend fun fetchSummary(month: String?): InsightsSummary =
            throw UnsupportedOperationException()

        override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> =
            throw UnsupportedOperationException()

        override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> =
            throw UnsupportedOperationException()

        override suspend fun fetchRecurring(): List<RecurringItem> = emptyList()

        override suspend fun fetchRiskProfile(): RiskProfile {
            fail?.let { throw it }
            return profile ?: throw UnsupportedOperationException()
        }
    }
}
