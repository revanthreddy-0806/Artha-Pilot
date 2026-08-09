package com.arthapilot.app.ui.recommendations

import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.data.repository.RecommendationRepository
import com.arthapilot.app.domain.model.AllocationItem
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.Recommendation
import com.arthapilot.app.domain.model.RecommendationContext
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskProfile
import com.arthapilot.app.domain.model.RiskQuestion
import com.arthapilot.app.domain.model.WhatIfBlock
import com.arthapilot.app.domain.model.WhatIfImpact
import com.arthapilot.app.domain.model.WhatIfResult
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
class RecommendationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun recommendation(status: String = "ok") = Recommendation(
        status = status,
        riskScore = 75,
        riskBand = "Aggressive",
        toleranceScore = 80,
        behaviorScore = 70,
        insufficientReason = if (status == "insufficient") "No transactions uploaded yet." else null,
        context = if (status == "ok") {
            RecommendationContext(
                monthsUsed = 2,
                monthlyIncome = 100000.0,
                monthlyExpenses = 42000.0,
                savingsRate = 0.58,
                investableMonthly = 58000.0,
            )
        } else {
            null
        },
        allocation = if (status == "ok") {
            listOf(
                AllocationItem("equity", "Equity", 60, "Growth"),
                AllocationItem("debt", "Debt", 25, "Stability"),
                AllocationItem("gold", "Gold", 15, "Hedge"),
            )
        } else {
            emptyList()
        },
        allocationSummary = "summary",
        notes = emptyList(),
        market = emptyList(),
        educationalDisclaimer = "Educational only",
    )

    private fun whatIfResult() = WhatIfResult(
        category = "Dining",
        monthlyReduction = 5000.0,
        monthsUsed = 2,
        currentMonthlySpend = 12000.0,
        monthlyIncome = 100000.0,
        original = WhatIfBlock(42000.0, 58000.0, 0.58),
        simulated = WhatIfBlock(37000.0, 63000.0, 0.63),
        impact = com.arthapilot.app.domain.model.WhatIfImpact(5000.0, 5000.0, 60000.0),
        narrative = "Savings rise to 63000.",
        isSimulation = true,
        educationalDisclaimer = "Educational only",
    )

    @Test
    fun `init loads recommendation and categories`() = runTest(dispatcher) {
        val viewModel = RecommendationViewModel(
            FakeRecommendationRepository(recommendation = recommendation()),
            FakeCategoriesRepository(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ok", state.recommendation!!.status)
        assertEquals(75, state.recommendation.riskScore)
        assertEquals(3, state.recommendation.allocation.size)
        assertEquals(listOf("Rent", "Dining"), state.categories)
        assertNull(state.error)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `insufficient recommendation surfaces reason`() = runTest(dispatcher) {
        val viewModel = RecommendationViewModel(
            FakeRecommendationRepository(recommendation = recommendation(status = "insufficient")),
            FakeCategoriesRepository(),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("insufficient", state.recommendation!!.status)
        assertTrue(state.recommendation.insufficientReason!!.contains("No transactions"))
        assertTrue(state.recommendation.allocation.isEmpty())
    }

    @Test
    fun `simulate posts scenario and stores result`() = runTest(dispatcher) {
        val viewModel = RecommendationViewModel(
            FakeRecommendationRepository(
                recommendation = recommendation(),
                whatIf = whatIfResult(),
            ),
            FakeCategoriesRepository(),
        )
        advanceUntilIdle()

        viewModel.simulate("Dining", 5000.0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val result = checkNotNull(state.whatIfResult)
        assertEquals(63000.0, result.simulated.monthlySavings, 0.0)
        assertTrue(result.isSimulation)
        assertNull(state.whatIfError)
        assertTrue(!state.whatIfLoading)
    }

    @Test
    fun `simulate invalid scenario surfaces error message`() = runTest(dispatcher) {
        val viewModel = RecommendationViewModel(
            FakeRecommendationRepository(
                recommendation = recommendation(),
                whatIfFail = ApiException.Server("Category not found", "invalid_scenario", 422),
            ),
            FakeCategoriesRepository(),
        )
        advanceUntilIdle()

        viewModel.simulate("Travel", 1000.0)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.whatIfResult)
        assertEquals("Category not found", viewModel.uiState.value.whatIfError)
        assertTrue(viewModel.uiState.value.whatIfError != null)
    }

    @Test
    fun `network failure marks offline`() = runTest(dispatcher) {
        val viewModel = RecommendationViewModel(
            FakeRecommendationRepository(
                recommendation = recommendation(),
                fail = ApiException.Network(),
            ),
            FakeCategoriesRepository(),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
    }

    private class FakeRecommendationRepository(
        val recommendation: Recommendation? = null,
        val whatIf: WhatIfResult? = null,
        val fail: ApiException? = null,
        val whatIfFail: ApiException? = null,
    ) : RecommendationRepository {
        override suspend fun fetchRecommendation(): Recommendation {
            fail?.let { throw it }
            return recommendation ?: throw UnsupportedOperationException()
        }

        override suspend fun simulate(category: String, monthlyReduction: Double): WhatIfResult {
            whatIfFail?.let { throw it }
            return whatIf ?: throw UnsupportedOperationException()
        }
    }

    private class FakeCategoriesRepository : InsightsRepository {
        override suspend fun fetchCategories(): List<String> = listOf("Rent", "Dining")
        override suspend fun fetchSummary(month: String?): InsightsSummary = throw UnsupportedOperationException()
        override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> = throw UnsupportedOperationException()
        override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> =
            throw UnsupportedOperationException()
        override suspend fun fetchRecurring(): List<RecurringItem> = throw UnsupportedOperationException()
        override suspend fun fetchRiskProfile(): RiskProfile = throw UnsupportedOperationException()
        override suspend fun fetchRiskQuestions(): List<RiskQuestion> = throw UnsupportedOperationException()
        override suspend fun submitRiskQuestionnaire(answers: Map<String, String>): RiskProfile =
            throw UnsupportedOperationException()
    }
}