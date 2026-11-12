package com.arthapilot.app.ui.budgets

import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.repository.BudgetRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Budget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class BudgetsViewModelTest {

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
    fun `init loads budgets`() = runTest(dispatcher) {
        val repository = FakeBudgetRepository(
            budgets = listOf(budget("Rent", 20000.0, 15000.0)),
        )
        val viewModel = BudgetsViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.budgets.size)
        assertEquals("Rent", viewModel.uiState.value.budgets[0].category)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `network failure surfaces message`() = runTest(dispatcher) {
        val viewModel = BudgetsViewModel(FakeBudgetRepository(fail = ApiException.Network()))
        advanceUntilIdle()

        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.offline)
    }

    @Test
    fun `setBudget adds or replaces the category entry`() = runTest(dispatcher) {
        val repository = FakeBudgetRepository(
            budgets = listOf(budget("Rent", 20000.0, 15000.0)),
            setBudgetResult = budget("Groceries", 8000.0, 0.0),
        )
        val viewModel = BudgetsViewModel(repository)
        advanceUntilIdle()

        viewModel.setBudget("Groceries", 8000.0)
        advanceUntilIdle()

        assertEquals(listOf("Groceries", "Rent"), viewModel.uiState.value.budgets.map { it.category })
        assertEquals(Pair("Groceries", 8000.0), repository.lastSet)
    }

    @Test
    fun `deleteBudget removes the category`() = runTest(dispatcher) {
        val repository = FakeBudgetRepository(
            budgets = listOf(budget("Rent", 20000.0, 15000.0), budget("Groceries", 8000.0, 0.0)),
        )
        val viewModel = BudgetsViewModel(repository)
        advanceUntilIdle()

        viewModel.deleteBudget("Rent")
        advanceUntilIdle()

        assertEquals(listOf("Groceries"), viewModel.uiState.value.budgets.map { it.category })
        assertEquals("Rent", repository.lastDeleted)
    }

    @Test
    fun `deleteBudget failure surfaces message and keeps list`() = runTest(dispatcher) {
        val repository = FakeBudgetRepository(
            budgets = listOf(budget("Rent", 20000.0, 15000.0)),
            deleteFail = ApiException.Server("Server error", "internal_error", 500),
        )
        val viewModel = BudgetsViewModel(repository)
        advanceUntilIdle()

        viewModel.deleteBudget("Rent")
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.budgets.size)
        assertEquals("Server error", viewModel.uiState.value.error)
    }

    @Test
    fun `invalidation bus reloads budgets`() = runTest(dispatcher) {
        val repository = FakeBudgetRepository(
            budgets = listOf(budget("Rent", 20000.0, 15000.0)),
        )
        val bus = FakeDataInvalidationBus()
        val viewModel = BudgetsViewModel(repository, bus)
        advanceUntilIdle()

        assertEquals(1, repository.fetchCount)
        bus.notifyDataChanged()
        advanceUntilIdle()

        assertEquals(2, repository.fetchCount)
        assertEquals(1, viewModel.uiState.value.budgets.size)
    }

    private fun budget(category: String, limit: Double, spent: Double) =
        Budget(category, limit, spent, spent / limit * 100, spent > limit)

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

    private class FakeBudgetRepository(
        val budgets: List<Budget> = emptyList(),
        val setBudgetResult: Budget? = null,
        val fail: ApiException? = null,
        val deleteFail: ApiException? = null,
    ) : BudgetRepository {
        var lastSet: Pair<String, Double>? = null
        var lastDeleted: String? = null
        var fetchCount: Int = 0

        override suspend fun fetchBudgets(): List<Budget> {
            fetchCount += 1
            fail?.let { throw it }
            return budgets
        }

        override suspend fun setBudget(category: String, monthlyLimit: Double): Budget {
            lastSet = category to monthlyLimit
            return setBudgetResult ?: Budget(category, monthlyLimit, 0.0, 0.0, false)
        }

        override suspend fun deleteBudget(category: String) {
            deleteFail?.let { throw it }
            lastDeleted = category
        }
    }
}
