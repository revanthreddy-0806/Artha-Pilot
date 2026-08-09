package com.arthapilot.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.AssistantRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.ChatTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessageUi(
    val role: String,
    val content: String,
    val id: Long,
)

data class AssistantUiState(
    val messages: List<ChatMessageUi> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val assistantRepository: AssistantRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var nextId = 1L

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return

        val userMessage = ChatMessageUi(role = "user", content = trimmed, id = nextId++)
        val history = _uiState.value.messages
            .filter { it.role == "user" || it.role == "assistant" }
            .takeLast(18)
            .map { ChatTurn(role = it.role, content = it.content) }

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isSending = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val reply = assistantRepository.chat(trimmed, history)
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessageUi(
                            role = "assistant",
                            content = reply.answer,
                            id = nextId++,
                        ),
                        isSending = false,
                        offline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        offline = e is ApiException.Network,
                        error = e.userMessage,
                    )
                }
            }
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }
}