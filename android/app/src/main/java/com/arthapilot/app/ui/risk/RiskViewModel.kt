package com.arthapilot.app.ui.risk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.RiskProfile
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
                _uiState.update { it.copy(profile = profile, isLoading = false, offline = false) }
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

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}
