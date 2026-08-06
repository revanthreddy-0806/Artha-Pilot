package com.arthapilot.app.ui.risk

import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskFactor
import com.arthapilot.app.domain.model.RiskProfile
import com.arthapilot.app.domain.model.RiskQuestion
import com.arthapilot.app.domain.model.RiskQuestionOption
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

    private fun profile(score: Int = 82, completed: Boolean = true) = RiskProfile(
        score = score,
        band = "Aggressive",
        summary = "Healthy",
        factors = listOf(RiskFactor("Savings rate", "60.0%", "positive")),
        income = 100000.0,
        monthlyExpenses = 40000.0,
        savingsRate = 0.6,
        emiLoadRatio = 0.1,
        safeEmiAmount = 30000.0,
        maxLoanAmount = 1398000.0,
        maxLoanTenureMonths = 60,
        toleranceScore = if (completed) 90 else null,
        toleranceBand = if (completed) "Aggressive" else null,
        behaviorScore = 80,
        questionnaireCompleted = completed,
    )

    private fun questions() = listOf(
        RiskQuestion("q1", "Question one?", listOf(RiskQuestionOption("a", "A"), RiskQuestionOption("b", "B"))),
        RiskQuestion("q2", "Question two?", listOf(RiskQuestionOption("a", "A"), RiskQuestionOption("b", "B"))),
    )

    @Test
    fun `init loads risk profile`() = runTest(dispatcher) {
        val viewModel = RiskViewModel(FakeInsightsRepository(profile = profile()))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(82, state.profile!!.score)
        assertEquals("Aggressive", state.profile.band)
        assertNull(state.questions)
        assertNull(state.error)
        assertTrue(!state.isLoading)
    }

    @Test
    fun `questionnaire is shown when not completed`() = runTest(dispatcher) {
        val viewModel = RiskViewModel(
            FakeInsightsRepository(profile = profile(completed = false), questions = questions()),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.profile)
        assertEquals(2, state.questions!!.size)
        assertTrue(state.answers.isEmpty())
    }

    @Test
    fun `submit with all answers returns assessment`() = runTest(dispatcher) {
        val submittedProfile = profile()
        val viewModel = RiskViewModel(
            FakeInsightsRepository(
                profile = profile(completed = false),
                questions = questions(),
                submitResult = submittedProfile,
            ),
        )
        advanceUntilIdle()

        viewModel.selectAnswer("q1", "a")
        viewModel.selectAnswer("q2", "b")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(submittedProfile, state.profile)
        assertNull(state.questions)
        assertTrue(!state.isSubmitting)
        assertNull(state.error)
    }

    @Test
    fun `submit with missing answers is rejected`() = runTest(dispatcher) {
        val viewModel = RiskViewModel(
            FakeInsightsRepository(
                profile = profile(completed = false),
                questions = questions(),
                submitResult = profile(),
            ),
        )
        advanceUntilIdle()

        viewModel.selectAnswer("q1", "a")
        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.profile)
        assertEquals(2, state.questions!!.size)
        assertEquals("Please answer every question before submitting.", state.error)
    }

    @Test
    fun `retake questionnaire loads questions`() = runTest(dispatcher) {
        val viewModel = RiskViewModel(
            FakeInsightsRepository(profile = profile(), questions = questions()),
        )
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.questions)

        viewModel.retakeQuestionnaire()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.questions!!.size)
        assertTrue(viewModel.uiState.value.answers.isEmpty())
    }

    @Test
    fun `empty user gets poor profile`() = runTest(dispatcher) {
        val emptyProfile = RiskProfile(
            score = 0,
            band = "Conservative",
            summary = "No data",
            factors = emptyList(),
            income = null,
            monthlyExpenses = 0.0,
            savingsRate = null,
            emiLoadRatio = null,
            safeEmiAmount = 0.0,
            maxLoanAmount = null,
            maxLoanTenureMonths = 60,
            questionnaireCompleted = true,
        )
        val viewModel = RiskViewModel(FakeInsightsRepository(profile = emptyProfile))
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
        val questions: List<RiskQuestion>? = null,
        val submitResult: RiskProfile? = null,
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

        override suspend fun fetchRiskQuestions(): List<RiskQuestion> {
            fail?.let { throw it }
            return questions ?: throw UnsupportedOperationException()
        }

        override suspend fun submitRiskQuestionnaire(answers: Map<String, String>): RiskProfile {
            fail?.let { throw it }
            return submitResult ?: throw UnsupportedOperationException()
        }
    }
}