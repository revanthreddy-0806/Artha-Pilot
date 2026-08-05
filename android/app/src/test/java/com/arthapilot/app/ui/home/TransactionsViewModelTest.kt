package com.arthapilot.app.ui.home

import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.ApiException
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
        val viewModel = TransactionsViewModel(repo)
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
        val viewModel = TransactionsViewModel(repo)
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
        val viewModel = TransactionsViewModel(repo)
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
        val viewModel = TransactionsViewModel(repo)
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
        val viewModel = TransactionsViewModel(repo)
        advanceUntilIdle()

        assertEquals("Cannot reach the server. Check your connection.", viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.transactions.isEmpty())
    }

    @Test
    fun `updateCategory propagates category to repository`() = runTest(dispatcher) {
        val repo = FakeTransactionRepository().apply {
            pages.addLast(TransactionPage(items = transactions(1), total = 1, page = 1, pageSize = 50))
        }
        val viewModel = TransactionsViewModel(repo)
        advanceUntilIdle()

        viewModel.updateCategory(1, "Travel")
        advanceUntilIdle()

        assertEquals(Pair(1L, "Travel"), repo.updatedCategory)
        assertNull(viewModel.uiState.value.error)
    }

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
}