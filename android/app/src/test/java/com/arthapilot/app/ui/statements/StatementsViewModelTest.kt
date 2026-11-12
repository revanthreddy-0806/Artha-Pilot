package com.arthapilot.app.ui.statements

import android.net.Uri
import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementInfo
import com.arthapilot.app.domain.model.StatementPage
import com.arthapilot.app.domain.model.StatementUploadResult
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.domain.model.TransactionPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatementsViewModelTest {

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
    fun `init loads first page`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(
            pages = listOf(StatementPage(items = listOf(statement("a.csv")), total = 1)),
        )
        val viewModel = StatementsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.statements.size)
        assertEquals("a.csv", state.statements[0].filename)
        assertEquals(1, state.total)
        assertFalse(state.hasMore)
        assertNull(state.error)
    }

    @Test
    fun `loadMore appends next page`() = runTest(dispatcher) {
        val second = statement("b.csv")
        val repository = FakeStatementRepository(
            pages = listOf(
                StatementPage(items = (0 until 50).map { statement("a$it.csv") }, total = 51),
                StatementPage(items = listOf(second), total = 51),
            ),
        )
        val viewModel = StatementsViewModel(repository)
        advanceUntilIdle()
        assertEquals(50, viewModel.uiState.value.statements.size)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(51, viewModel.uiState.value.statements.size)
        assertEquals("b.csv", viewModel.uiState.value.statements.last().filename)
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(listOf(1, 2), repository.requestedPages)
    }

    @Test
    fun `loadMore is a no-op when no more pages`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(
            pages = listOf(StatementPage(items = listOf(statement("a.csv")), total = 1)),
        )
        val viewModel = StatementsViewModel(repository)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.statements.size)
        assertEquals(listOf(1), repository.requestedPages)
    }

    @Test
    fun `network failure falls back to cached statements`() = runTest(dispatcher) {
        val cached = statement("cached.csv")
        val repository = FakeStatementRepository(
            fail = ApiException.Network(),
            cached = listOf(cached),
        )
        val viewModel = StatementsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.offline)
        assertEquals(listOf("cached.csv"), state.statements.map { it.filename })
        assertNull(state.error)
    }

    @Test
    fun `network failure without cache surfaces message`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(fail = ApiException.Network())
        val viewModel = StatementsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.offline)
        assertEquals("Cannot reach the server. Check your connection.", state.error)
        assertTrue(state.statements.isEmpty())
    }

    @Test
    fun `openStatementDrillDown loads transactions for statement`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(
            pages = listOf(StatementPage(items = listOf(statement("a.csv")), total = 1)),
        )
        val transactionRepository = FakeTransactionRepository().apply {
            pages.addLast(
                TransactionPage(
                    items = listOf(
                        Transaction(10, 1, "2026-01-01", "tx-10", 10.0, "debit", null, "Groceries", null, false),
                        Transaction(11, 1, "2026-01-02", "tx-11", 11.0, "debit", null, "Groceries", null, false),
                    ),
                    total = 2,
                    page = 1,
                    pageSize = 100,
                ),
            )
        }
        val viewModel = StatementsViewModel(repository, transactionRepository)
        advanceUntilIdle()

        viewModel.openStatementDrillDown(statement("a.csv"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1L, state.selectedStatement?.id)
        assertEquals(listOf(10L, 11L), state.statementTransactions.map { it.id })
        assertFalse(state.isDrillDownLoading)
        assertNull(state.drillDownError)
        assertEquals(1L, transactionRepository.lastStatementId)
    }

    @Test
    fun `drillDown error and close clears state`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(
            pages = listOf(StatementPage(items = listOf(statement("a.csv")), total = 1)),
        )
        val transactionRepository = FakeTransactionRepository().apply {
            fail = ApiException.Network()
        }
        val viewModel = StatementsViewModel(repository, transactionRepository)
        advanceUntilIdle()

        viewModel.openStatementDrillDown(statement("a.csv"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Cannot reach the server. Check your connection.", state.drillDownError)
        assertFalse(state.isDrillDownLoading)

        viewModel.closeStatementDrillDown()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedStatement)
        assertTrue(viewModel.uiState.value.statementTransactions.isEmpty())
        assertNull(viewModel.uiState.value.drillDownError)
    }

    @Test
    fun `invalidation bus refreshes statements list`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(
            pages = listOf(
                StatementPage(items = listOf(statement("a.csv")), total = 1),
                StatementPage(items = listOf(statement("b.csv")), total = 1),
            ),
        )
        val bus = FakeDataInvalidationBus()
        val viewModel = StatementsViewModel(repository, invalidationBus = bus)
        advanceUntilIdle()

        assertEquals("a.csv", viewModel.uiState.value.statements[0].filename)

        bus.notifyDataChanged()
        advanceUntilIdle()

        assertEquals("b.csv", viewModel.uiState.value.statements[0].filename)
        assertEquals(listOf(1, 1), repository.requestedPages)
    }

    private fun statement(fileName: String) = StatementInfo(
        id = 1,
        filename = fileName,
        fileType = "csv",
        bankName = null,
        periodStart = "2026-06-01",
        periodEnd = "2026-07-01",
        parseStatus = "processed",
        transactionCount = 5,
        errorMessage = null,
        createdAt = "2026-07-20T10:00:00",
    )

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

    private class FakeTransactionRepository : TransactionRepository {
        val pages = ArrayDeque<TransactionPage>()
        var fail: ApiException? = null
        var lastStatementId: Long? = null
        private val cachedFlow = MutableStateFlow<List<Transaction>>(emptyList())
        override val cachedTransactions: Flow<List<Transaction>> = cachedFlow

        override suspend fun fetchTransactions(
            page: Int,
            pageSize: Int,
            category: String?,
            transactionType: String?,
            startDate: String?,
            endDate: String?,
            search: String?,
            minAmount: Double?,
            maxAmount: Double?,
            statementId: Long?,
            sortBy: String?,
            sortDir: String?,
        ): TransactionPage {
            lastStatementId = statementId
            fail?.let { throw it }
            return pages.removeFirstOrNull() ?: TransactionPage(emptyList(), 0, page, pageSize)
        }

        override suspend fun updateCategory(id: Long, category: String): Transaction =
            throw UnsupportedOperationException()

        override suspend fun createTransaction(
            transactionType: String,
            amount: Double,
            date: String,
            description: String,
            category: String,
            balance: Double?,
        ): Transaction = throw UnsupportedOperationException()

        override suspend fun updateTransaction(
            id: Long,
            transactionType: String?,
            amount: Double?,
            date: String?,
            description: String?,
            category: String?,
            balance: Double?,
        ): Transaction = throw UnsupportedOperationException()

        override suspend fun deleteTransaction(id: Long) = Unit
    }

    private class FakeStatementRepository(
        val pages: List<StatementPage> = emptyList(),
        val fail: ApiException? = null,
        val cached: List<StatementInfo> = emptyList(),
    ) : StatementRepository {

        val cachedFlow = MutableStateFlow(cached)
        val requestedPages = mutableListOf<Int>()
        private val remainingPages = ArrayDeque(pages)

        override suspend fun upload(
            fileUri: Uri?,
            fileName: String,
            mimeType: String,
        ): StatementUploadResult = throw UnsupportedOperationException()

        override suspend fun fetchStatements(page: Int, pageSize: Int): StatementPage {
            requestedPages += page
            fail?.let { throw it }
            return remainingPages.removeFirstOrNull() ?: StatementPage(emptyList(), 0)
        }

        override val cachedStatements: Flow<List<StatementInfo>> = cachedFlow
    }
}
