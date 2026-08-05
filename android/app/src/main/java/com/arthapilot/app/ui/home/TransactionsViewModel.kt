package com.arthapilot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val page: Int = 0,
    val pageSize: Int = 50,
    val total: Int = 0,
    val categoryFilter: String? = null,
    val typeFilter: String? = null,
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
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh(reset = true)
    }

    fun refresh() = refresh(reset = true)

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        refresh(reset = false)
    }

    fun setCategoryFilter(category: String?) {
        _uiState.update { it.copy(categoryFilter = category) }
        refresh(reset = true)
    }

    fun setTypeFilter(type: String?) {
        _uiState.update { it.copy(typeFilter = type) }
        refresh(reset = true)
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun updateCategory(id: Long, category: String) {
        viewModelScope.launch {
            try {
                transactionRepository.updateCategory(id, category)
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            }
        }
    }

    private fun refresh(reset: Boolean) {
        val state = _uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val nextPage = if (reset) 1 else state.page + 1
            try {
                val page = transactionRepository.fetchTransactions(
                    page = nextPage,
                    pageSize = state.pageSize,
                    category = state.categoryFilter,
                    transactionType = state.typeFilter,
                    startDate = null,
                    endDate = null,
                )
                _uiState.update {
                    it.copy(
                        transactions = if (reset) page.items else it.transactions + page.items,
                        page = page.page,
                        total = page.total,
                        hasMore = page.page * page.pageSize < page.total,
                    )
                }
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
