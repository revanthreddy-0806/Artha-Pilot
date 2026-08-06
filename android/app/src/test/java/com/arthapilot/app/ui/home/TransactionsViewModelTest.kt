package com.arthapilot.app.ui.home

import com.arthapilot.app.data.repository.BudgetRepository
import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Budget
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.RiskProfile
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.domain.model.TransactionPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

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
    fun `init fetches first page and computes hasMore`() = runTest(dispatcher) {
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = transactions(1, 2), total = 3, page = 1, pageSize = 2))
            pages.addLast(TransactionPage(items = transactions(3), total = 3, page = 2, pageSize = 2))
        }
        val viewModel = viewModel(repo)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.transactions.size)
        assertTrue(viewModel.uiState.value.hasMore)
        assertEquals(3, viewModel.uiState.value.total)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadMore appends the next page`() = runTest(dispatcher) {
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = transactions(1, 2), total = 3, page = 1, pageSize = 2))
            pages.addLast(TransactionPage(items = transactions(4), total = 3, page = 2, pageSize = 2))
        }
        val viewModel = viewModel(repo)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.transactions.size)
        assertTrue(!viewModel.uiState.value.hasMore)
        assertEquals(listOf(1L, 2L, 4L), viewModel.uiState.value.transactions.map { it.id })
    }

    @Test
    fun `setting category filter resets list and refetches`() = runTest(dispatcher) {
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = transactions(1), total = 1, page = 1, pageSize = 50))
            pages.addLast(TransactionPage(items = transactions(2), total = 1, page = 1, pageSize = 50))
        }
        val viewModel = viewModel(repo)
        advanceUntilIdle()

        viewModel.setCategoryFilter("Rent")
        advanceUntilIdle()

        assertEquals("Rent", repo.lastCategory)
        assertEquals(listOf(2L), viewModel.uiState.value.transactions.map { it.id })
        assertEquals(1, viewModel.uiState.value.page)
    }

    @Test
    fun `fetch failure falls back to cached transactions offline`() = runTest(dispatcher) {
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = transactions(1, 2), total = 2, page = 1, pageSize = 50))
        }
        val viewModel = viewModel(repo)
        advanceUntilIdle()

        repo.fail = ApiException.Network()
        viewModel.refresh()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.transactions.map { it.id })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `fetch failure without cache surfaces user message`() = runTest(dispatcher) {
        val repo = FakeTransactionRepository().apply { fail = ApiException.Network() }
        val viewModel = viewModel(repo)
        advanceUntilIdle()

        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.transactions.isEmpty())
    }

    @Test
    fun `updateCategory propagates category to repository`() = runTest(dispatcher) {
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = transactions(1), total = 1, page = 1, pageSize = 50))
        }
        val viewModel = viewModel(repo)
        advanceUntilIdle()

        viewModel.updateCategory(1, "Travel")
        advanceUntilIdle()

        assertEquals(Pair(1L, "Travel"), repo.updatedCategory)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `categories come from the insights repository`() = runTest(dispatcher) {
        val insights = FakeInsightsRepository(categories = listOf("Rent", "Salary", "Groceries"))
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = emptyList(), total = 0, page = 1, pageSize = 50))
        }
        val viewModel = TransactionsViewModel(repo, insights, FakeBudgetRepository())
        advanceUntilIdle()

        assertEquals(listOf("Rent", "Salary", "Groceries"), viewModel.uiState.value.categoryOptions)
    }

    @Test
    fun `categories fall back when offline`() = runTest(dispatcher) {
        val insights = FakeInsightsRepository(fail = ApiException.Network())
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = emptyList(), total = 0, page = 1, pageSize = 50))
        }
        val viewModel = TransactionsViewModel(repo, insights, FakeBudgetRepository())
        advanceUntilIdle()

        assertEquals(FALLBACK_CATEGORIES, viewModel.uiState.value.categoryOptions)
    }

    @Test
    fun `budget overrun produces warning alert`() = runTest(dispatcher) {
        val budgets = FakeBudgetRepository(
            budgets = listOf(Budget("Groceries", 8000.0, 10000.0, 125.0, overrun = true)),
        )
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = emptyList(), total = 0, page = 1, pageSize = 50))
        }
        val viewModel = TransactionsViewModel(repo, FakeInsightsRepository(), budgets)
        advanceUntilIdle()

        val alerts = viewModel.uiState.value.alerts
        assertEquals(1, alerts.size)
        assertEquals("Budget exceeded", alerts[0].title)
        assertEquals("warning", alerts[0].severity)
    }

    @Test
    fun `buildAlerts flags emi due within a week`() {
        val today = LocalDate.of(2026, 8, 6)
        val recurring = listOf(
            recurringItem(due = "2026-08-08", kind = "emi"),
            recurringItem(due = "2026-09-05", kind = "emi"),
            recurringItem(due = "2026-08-20", kind = "recurring"),
        )
        val alerts = buildAlerts(emptyList(), recurring, today)
        assertEquals(1, alerts.size)
        assertEquals("EMI due soon", alerts[0].title)
    }

    @Test
    fun `buildAlerts orders overruns before emis`() {
        val today = LocalDate.of(2026, 8, 6)
        val budgets = listOf(Budget("Rent", 20000.0, 25000.0, 125.0, overrun = true))
        val recurring = listOf(recurringItem(due = "2026-08-08", kind = "emi"))
        val alerts = buildAlerts(budgets, recurring, today)
        assertEquals(listOf("Budget exceeded", "EMI due soon"), alerts.map { it.title })
    }

    private fun viewModel(repo: TransactionRepository) =
        TransactionsViewModel(repo, FakeInsightsRepository(), FakeBudgetRepository())

    private fun transactions(vararg ids: Long): List<Transaction> = ids.map { id ->
        Transaction(
            id = id,
            statementId = 1,
            date = "2026-01-01",
            description = "tx-$id",
            amount = 10.0,
            transactionType = "debit",
            balance = null,
            category = "Uncategorized",
            confidence = null,
            isRecurring = false,
        )
    }

    private fun recurringItem(due: String, kind: String) = RecurringItem(
        transactionId = 1,
        description = "EMI ITEM",
        amount = 12000.0,
        category = "Bills & Recurring",
        kind = kind,
        intervalDays = 30,
        occurrences = 3,
        confidence = 0.75,
        firstDate = "2026-05-05",
        lastDate = "2026-07-05",
        nextExpectedDate = due,
    )

    private class FakeTransactionRepository : TransactionRepository {
        private val cache = MutableStateFlow<List<Transaction>>(emptyList())
        override val cachedTransactions: Flow<List<Transaction>> = cache

        val pages = ArrayDeque<TransactionPage>()
        var fail: ApiException? = null
        var lastCategory: String? = null
        var updatedCategory: Pair<Long, String>? = null

        override suspend fun fetchTransactions(
            page: Int,
            pageSize: Int,
            category: String?,
            transactionType: String?,
            startDate: String?,
            endDate: String?,
        ): TransactionPage {
            lastCategory = category
            fail?.let { throw it }
            val result = pages.removeFirstOrNull() ?: TransactionPage(emptyList(), 0, page, pageSize)
            cache.value = result.items
            return result
        }

        override suspend fun updateCategory(id: Long, category: String): Transaction {
            updatedCategory = id to category
            return Transaction(
                id = id,
                statementId = 1,
                date = "2026-01-01",
                description = "tx-$id",
                amount = 10.0,
                transactionType = "debit",
                balance = null,
                category = category,
                confidence = null,
                isRecurring = false,
            )
        }
    }

    private class FakeInsightsRepository(
        val categories: List<String> = emptyList(),
        val fail: ApiException? = null,
    ) : InsightsRepository {
        override suspend fun fetchCategories(): List<String> {
            fail?.let { throw it }
            return categories
        }

        override suspend fun fetchSummary(month: String?): InsightsSummary =
            throw UnsupportedOperationException()

        override suspend fun fetchMonthlyTrends(months: Int): List<MonthTrend> =
            throw UnsupportedOperationException()

        override suspend fun fetchCategoryBreakdown(month: String?): List<CategoryBreakdownItem> =
            throw UnsupportedOperationException()

        override suspend fun fetchRecurring(): List<RecurringItem> = emptyList()

        override suspend fun fetchRiskProfile(): RiskProfile =
            throw UnsupportedOperationException()
    }

    private class FakeBudgetRepository(
        val budgets: List<Budget> = emptyList(),
    ) : BudgetRepository {
        override suspend fun fetchBudgets(): List<Budget> = budgets

        override suspend fun setBudget(category: String, monthlyLimit: Double): Budget =
            throw UnsupportedOperationException()

        override suspend fun deleteBudget(category: String) = Unit
    }
}