package com.arthapilot.app.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val summary: InsightsSummary? = null,
    val trends: List<MonthTrend> = emptyList(),
    val breakdown: List<CategoryBreakdownItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val insightsRepository: InsightsRepository,
    private val invalidationBus: DataInvalidationBus,
) : ViewModel() {

    constructor(insightsRepository: InsightsRepository) :
        this(insightsRepository, NoOpDataInvalidationBus)

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private var lastRevision: Long = invalidationBus.revision.value
    private var requestId: Long = 0
    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            invalidationBus.revision.collect { newRevision ->
                if (newRevision > lastRevision) {
                    lastRevision = newRevision
                    refresh(force = true)
                }
            }
        }
    }

    fun refresh(force: Boolean = false) {
        if (!force && _uiState.value.isLoading) return
        if (force) refreshJob?.cancel()
        val currentRequestId = ++requestId
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val summary = insightsRepository.fetchSummary(null)
                val trends = insightsRepository.fetchMonthlyTrends(6)
                val breakdown = insightsRepository.fetchCategoryBreakdown(null)
                if (currentRequestId != requestId) return@launch
                _uiState.update {
                    it.copy(
                        summary = summary,
                        trends = trends,
                        breakdown = breakdown,
                        isLoading = false,
                        offline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (currentRequestId != requestId) return@launch
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