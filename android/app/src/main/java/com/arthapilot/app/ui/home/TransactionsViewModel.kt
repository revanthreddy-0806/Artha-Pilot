package com.arthapilot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Transaction
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

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val page: Int = 0,
    val pageSize: Int = 50,
    val total: Int = 0,
    val categoryFilter: String? = null,
    val hasMore: Boolean = false,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    /** Offline mirror of the last successful fetch. */
    val cachedTransactions: StateFlow<List<Transaction>> =
        transactionRepository.cachedTransactions
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

    fun setCategoryFilter(category: String?) {
        _uiState.update { it.copy(categoryFilter = category) }
        refresh()
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun updateCategory(id: Long, category: String) {
        viewModelScope.launch {
            try {
                transactionRepository.updateCategory(id, category)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            }
        }
    }

    private fun load(page: Int, reset: Boolean) {
        viewModelScope.launch {
            val state = _uiState.value
            try {
                val result = transactionRepository.fetchTransactions(
                    page = page,
                    pageSize = state.pageSize,
                    category = state.categoryFilter,
                    transactionType = null,
                    startDate = null,
                    endDate = null,
                )
                _uiState.update {
                    it.copy(
                        transactions = if (reset) result.items else it.transactions + result.items,
                        page = result.page,
                        total = result.total,
                        hasMore = result.page * result.pageSize < result.total,
                        offline = false,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val cached = if (e is ApiException.Network) cachedTransactions.value else emptyList()
                if (cached.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            transactions = cached,
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
