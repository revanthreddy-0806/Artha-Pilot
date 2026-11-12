package com.arthapilot.app.ui.assistant

import com.arthapilot.app.data.repository.AssistantRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.AssistantReply
import com.arthapilot.app.domain.model.ChatTurn
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
class AssistantViewModelTest {

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
    fun `send appends user message and assistant reply`() = runTest(dispatcher) {
        val viewModel = AssistantViewModel(FakeAssistantRepository())
        advanceUntilIdle()

        viewModel.send("how much do I spend?")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals("user", state.messages[0].role)
        assertEquals("how much do I spend?", state.messages[0].content)
        assertEquals("assistant", state.messages[1].role)
        assertEquals("Grounded answer.", state.messages[1].content)
        assertTrue(!state.isSending)
        assertNull(state.error)
    }

    @Test
    fun `history sent to repository excludes nothing and keeps order`() = runTest(dispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = AssistantViewModel(repo)
        advanceUntilIdle()

        viewModel.send("first question")
        advanceUntilIdle()
        viewModel.send("second question")
        advanceUntilIdle()

        assertEquals(2, repo.received.size)
        val second = repo.received[1]
        assertEquals("second question", second.first)
        assertTrue(second.second.any { it.role == "user" && it.content == "first question" })
        assertTrue(second.second.any { it.role == "assistant" && it.content == "Grounded answer." })
    }

    @Test
    fun `blank message is ignored`() = runTest(dispatcher) {
        val repo = FakeAssistantRepository()
        val viewModel = AssistantViewModel(repo)
        advanceUntilIdle()

        viewModel.send("   ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertTrue(repo.received.isEmpty())
    }

    @Test
    fun `server error surfaces message and keeps conversation`() = runTest(dispatcher) {
        val viewModel = AssistantViewModel(
            FakeAssistantRepository(
                fail = ApiException.Server("Assistant not configured", "llm_not_configured", 503),
            ),
        )
        advanceUntilIdle()

        viewModel.send("hello")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("The AI assistant is not available right now. Please try again later.", state.error)
        assertEquals(1, state.messages.size)
        assertTrue(!state.isSending)
    }

    @Test
    fun `network failure marks offline`() = runTest(dispatcher) {
        val viewModel = AssistantViewModel(FakeAssistantRepository(fail = ApiException.Network()))
        advanceUntilIdle()

        viewModel.send("hello")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.offline)
        assertEquals("Cannot reach the server. Check your connection.", state.error)
    }

    @Test
    fun `dismiss clears error`() = runTest(dispatcher) {
        val viewModel = AssistantViewModel(FakeAssistantRepository(fail = ApiException.Network()))
        advanceUntilIdle()

        viewModel.send("hello")
        advanceUntilIdle()
        viewModel.onErrorDismissed()

        assertNull(viewModel.uiState.value.error)
    }

    private class FakeAssistantRepository(
        val fail: ApiException? = null,
    ) : AssistantRepository {
        val received: MutableList<Pair<String, List<ChatTurn>>> = mutableListOf()

        override suspend fun chat(message: String, history: List<ChatTurn>): AssistantReply {
            fail?.let { throw it }
            received.add(message to history)
            return AssistantReply(answer = "Grounded answer.", disclaimer = "Educational only")
        }
    }
}