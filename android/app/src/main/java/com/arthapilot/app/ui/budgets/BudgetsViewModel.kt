package com.arthapilot.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.BudgetRepository
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Budget
import com.arthapilot.app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetsUiState(
    val budgets: List<Budget> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val invalidationBus: DataInvalidationBus,
    private val transactionRepository: TransactionRepository?,
) : ViewModel() {

    constructor(budgetRepository: BudgetRepository) :
        this(budgetRepository, NoOpDataInvalidationBus, null)

    constructor(budgetRepository: BudgetRepository, invalidationBus: DataInvalidationBus) :
        this(budgetRepository, invalidationBus, null)

    private val _uiState = MutableStateFlow(BudgetsUiState())
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

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
                val budgets = budgetRepository.fetchBudgets()
                val transactions = transactionRepository?.let {
                    try { it.fetchTransactions(1, 100, null, null, null, null).items } catch (e: Exception) { emptyList() }
                } ?: emptyList()
                if (currentRequestId != requestId) return@launch
                _uiState.update { it.copy(budgets = budgets, transactions = transactions, isLoading = false, offline = false) }
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

    fun setBudget(category: String, monthlyLimit: Double) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = budgetRepository.setBudget(category, monthlyLimit)
                _uiState.update { state ->
                    val remaining = state.budgets.filterNot { it.category == category }
                    state.copy(
                        budgets = (remaining + updated).sortedBy { it.category },
                        isSaving = false,
                        offline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(isSaving = false, error = e.userMessage) }
            }
        }
    }

    fun deleteBudget(category: String) {
        viewModelScope.launch {
            try {
                budgetRepository.deleteBudget(category)
                _uiState.update { state ->
                    state.copy(budgets = state.budgets.filterNot { it.category == category })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            }
        }
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}
