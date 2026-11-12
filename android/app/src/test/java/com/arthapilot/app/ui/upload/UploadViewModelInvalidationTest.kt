package com.arthapilot.app.ui.upload

import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementUploadResult
import com.arthapilot.app.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelInvalidationTest {

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
    fun `successful upload notifies invalidation`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(result = uploadResult())
        val bus = FakeDataInvalidationBus()
        val viewModel = UploadViewModel(null, repository, bus)

        val flowField = viewModel::class.java.getDeclaredField("_uiState")
        flowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = flowField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<UploadUiState>
        flow.value = UploadUiState(fileName = "statement.csv", mimeType = "text/csv")

        viewModel.upload()
        advanceUntilIdle()

        assertEquals(1, repository.uploadCount)
        assertEquals(1, bus.revision.value.toInt())
        assertEquals(7, viewModel.uiState.value.success?.transactionCount)
    }

    @Test
    fun `duplicate upload does not notify invalidation`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(
            failure = ApiException.Server("duplicate", code = "duplicate_statement", statusCode = 409),
        )
        val bus = FakeDataInvalidationBus()
        val viewModel = UploadViewModel(null, repository, bus)

        val flowField = viewModel::class.java.getDeclaredField("_uiState")
        flowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = flowField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<UploadUiState>
        flow.value = UploadUiState(fileName = "statement.csv", mimeType = "text/csv")

        viewModel.upload()
        advanceUntilIdle()

        assertEquals(1, repository.uploadCount)
        assertEquals(0, bus.revision.value.toInt())
        assertNull(viewModel.uiState.value.success)
    }

    @Test
    fun `failed upload does not notify invalidation`() = runTest(dispatcher) {
        val repository = FakeStatementRepository(failure = ApiException.Network())
        val bus = FakeDataInvalidationBus()
        val viewModel = UploadViewModel(null, repository, bus)

        val flowField = viewModel::class.java.getDeclaredField("_uiState")
        flowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = flowField.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<UploadUiState>
        flow.value = UploadUiState(fileName = "statement.csv", mimeType = "text/csv")

        viewModel.upload()
        advanceUntilIdle()

        assertEquals(1, repository.uploadCount)
        assertEquals(0, bus.revision.value.toInt())
        assertNull(viewModel.uiState.value.success)
    }

    private fun uploadResult() = StatementUploadResult(
        id = 1,
        filename = "statement.csv",
        bankName = "Test Bank",
        parseStatus = "processed",
        transactionCount = 7,
        transactions = emptyList(),
    )

    private fun setPrivate(target: Any, name: String, value: Any) {
        val field: Field = target::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(target, value)
    }

    private class FakeDataInvalidationBus : DataInvalidationBus {
        private val _revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
        override val revision: kotlinx.coroutines.flow.StateFlow<Long> = _revision.asStateFlow()
        override fun notifyDataChanged() {
            _revision.value = _revision.value + 1
        }

        override fun reset() {
            _revision.value = 0L
        }
    }

    private class FakeStatementRepository(
        val result: StatementUploadResult? = null,
        val failure: ApiException? = null,
    ) : StatementRepository {
        var uploadCount: Int = 0

        override suspend fun upload(fileUri: android.net.Uri?, fileName: String, mimeType: String): StatementUploadResult {
            uploadCount += 1
            failure?.let { throw it }
            return result ?: throw UnsupportedOperationException()
        }

        override suspend fun fetchStatements(page: Int, pageSize: Int): com.arthapilot.app.domain.model.StatementPage =
            throw UnsupportedOperationException()

        override val cachedStatements: kotlinx.coroutines.flow.Flow<List<com.arthapilot.app.domain.model.StatementInfo>> =
            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }
}