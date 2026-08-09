package com.arthapilot.app.ui.recommendations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.data.repository.RecommendationRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Recommendation
import com.arthapilot.app.domain.model.WhatIfResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationUiState(
    val recommendation: Recommendation? = null,
    val categories: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val whatIfLoading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val whatIfResult: WhatIfResult? = null,
    val whatIfError: String? = null,
)

@HiltViewModel
class RecommendationViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val insightsRepository: InsightsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val recommendation = recommendationRepository.fetchRecommendation()
                val categories = insightsRepository.fetchCategories()
                _uiState.update {
                    it.copy(
                        recommendation = recommendation,
                        categories = categories,
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

    fun simulate(category: String, monthlyReduction: Double) {
        if (_uiState.value.whatIfLoading) return
        _uiState.update { it.copy(whatIfLoading = true, whatIfError = null) }
        viewModelScope.launch {
            try {
                val result = recommendationRepository.simulate(category, monthlyReduction)
                _uiState.update {
                    it.copy(whatIfResult = result, whatIfLoading = false, offline = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update {
                    it.copy(
                        whatIfLoading = false,
                        offline = e is ApiException.Network,
                        whatIfError = e.userMessage,
                    )
                }
            }
        }
    }

    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    fun onWhatIfErrorDismissed() {
        _uiState.update { it.copy(whatIfError = null) }
    }
}