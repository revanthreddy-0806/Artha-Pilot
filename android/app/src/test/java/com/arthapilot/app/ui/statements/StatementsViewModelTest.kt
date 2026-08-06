package com.arthapilot.app.ui.statements

import android.net.Uri
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementInfo
import com.arthapilot.app.domain.model.StatementPage
import com.arthapilot.app.domain.model.StatementUploadResult
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

    private class FakeStatementRepository(
        val pages: List<StatementPage> = emptyList(),
        val fail: ApiException? = null,
        val cached: List<StatementInfo> = emptyList(),
    ) : StatementRepository {

        val cachedFlow = MutableStateFlow(cached)
        val requestedPages = mutableListOf<Int>()

        override suspend fun upload(
            fileUri: Uri,
            fileName: String,
            mimeType: String,
        ): StatementUploadResult = throw UnsupportedOperationException()

        override suspend fun fetchStatements(page: Int, pageSize: Int): StatementPage {
            requestedPages += page
            fail?.let { throw it }
            return pages.getOrElse(page - 1) { StatementPage(emptyList(), 0) }
        }

        override val cachedStatements: Flow<List<StatementInfo>> = cachedFlow
    }
}
