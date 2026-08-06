package com.arthapilot.app.ui.risk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.RiskProfile
import com.arthapilot.app.domain.model.RiskQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RiskUiState(
    val profile: RiskProfile? = null,
    val questions: List<RiskQuestion>? = null,
    val answers: Map<String, String> = emptyMap(),
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
)

@HiltViewModel
class RiskViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RiskUiState())
    val uiState: StateFlow<RiskUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = insightsRepository.fetchRiskProfile()
                val questions = if (profile.questionnaireCompleted) null else {
                    insightsRepository.fetchRiskQuestions()
                }
                _uiState.update {
                    it.copy(
                        profile = if (questions == null) profile else null,
                        questions = questions,
                        answers = emptyMap(),
                        isLoading = false,
                        offline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        offline = e is ApiException.Network,
                        error = e.userMessage,
                    )
                }
            }
        }
    }

    fun retakeQuestionnaire() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val questions = insightsRepository.fetchRiskQuestions()
                _uiState.update { it.copy(questions = questions, answers = emptyMap(), isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        offline = e is ApiException.Network,
                        error = e.userMessage,
                    )
                }
            }
        }
    }

    fun selectAnswer(questionId: String, optionId: String) {
        _uiState.update {
            it.copy(answers = it.answers + (questionId to optionId), error = null)
        }
    }

    fun submit() {
        val state = _uiState.value
        val questions = state.questions ?: return
        if (state.answers.size < questions.size) {
            _uiState.update { it.copy(error = "Please answer every question before submitting.") }
            return
        }
        if (state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = insightsRepository.submitRiskQuestionnaire(state.answers)
                _uiState.update {
                    it.copy(
                        profile = profile,
                        questions = null,
                        answers = emptyMap(),
                        isSubmitting = false,
                        offline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        offline = e is ApiException.Network,
                        error = e.userMessage,
                    )
                }
            }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}