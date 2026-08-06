package com.arthapilot.app.ui.statements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatementsUiState(
    val statements: List<StatementInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val page: Int = 0,
    val pageSize: Int = 50,
    val total: Int = 0,
    val hasMore: Boolean = false,
)

@HiltViewModel
class StatementsViewModel @Inject constructor(
    private val statementRepository: StatementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatementsUiState())
    val uiState: StateFlow<StatementsUiState> = _uiState.asStateFlow()

    /** Offline mirror of the last fetched statement history. */
    val cachedStatements: StateFlow<List<StatementInfo>> =
        statementRepository.cachedStatements
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        load(page = 1, reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        _uiState.update { it.copy(isLoading = true) }
        load(page = state.page + 1, reset = false)
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    private fun load(page: Int, reset: Boolean) {
        viewModelScope.launch {
            try {
                val result = statementRepository.fetchStatements(
                    page = page,
                    pageSize = _uiState.value.pageSize,
                )
                _uiState.update {
                    it.copy(
                        statements = if (reset) result.items else it.statements + result.items,
                        page = page,
                        total = result.total,
                        hasMore = page * _uiState.value.pageSize < result.total,
                        isLoading = false,
                        offline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val cached = if (e is ApiException.Network) cachedStatements.value else emptyList()
                if (cached.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            statements = cached,
                            hasMore = false,
                            offline = true,
                            error = null,
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = e.userMessage, isLoading = false) }
                }
            }
        }
    }
}
