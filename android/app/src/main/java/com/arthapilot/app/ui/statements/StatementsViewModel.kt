package com.arthapilot.app.ui.statements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.StatementInfo
import com.arthapilot.app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val selectedStatement: StatementInfo? = null,
    val statementTransactions: List<Transaction> = emptyList(),
    val isDrillDownLoading: Boolean = false,
    val drillDownError: String? = null,
)

@HiltViewModel
class StatementsViewModel @Inject constructor(
    private val statementRepository: StatementRepository,
    private val transactionRepository: TransactionRepository? = null,
    private val invalidationBus: DataInvalidationBus = NoOpDataInvalidationBus,
) : ViewModel() {

    constructor(statementRepository: StatementRepository) :
        this(statementRepository, null, NoOpDataInvalidationBus)

    private val _uiState = MutableStateFlow(StatementsUiState())
    val uiState: StateFlow<StatementsUiState> = _uiState.asStateFlow()

    /** Offline mirror of the last fetched statement history. */
    val cachedStatements: StateFlow<List<StatementInfo>> =
        statementRepository.cachedStatements
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var lastRevision: Long = invalidationBus.revision.value
    private var listRequestId: Long = 0
    private var drillDownRequestId: Long = 0
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
        val currentRequestId = ++listRequestId
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = statementRepository.fetchStatements(
                    page = 1,
                    pageSize = _uiState.value.pageSize,
                )
                if (currentRequestId != listRequestId) return@launch
                _uiState.update {
                    it.copy(
                        statements = result.items,
                        page = 1,
                        total = result.total,
                        hasMore = 1 * _uiState.value.pageSize < result.total,
                        isLoading = false,
                        offline = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (currentRequestId != listRequestId) return@launch
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

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val result = statementRepository.fetchStatements(
                    page = state.page + 1,
                    pageSize = state.pageSize,
                )
                _uiState.update {
                    it.copy(
                        statements = it.statements + result.items,
                        page = state.page + 1,
                        total = result.total,
                        hasMore = (state.page + 1) * state.pageSize < result.total,
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

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun openStatementDrillDown(statement: StatementInfo) {
        _uiState.update {
            it.copy(
                selectedStatement = statement,
                statementTransactions = emptyList(),
                isDrillDownLoading = true,
                drillDownError = null,
            )
        }
        val repository = transactionRepository
        if (repository == null) {
            _uiState.update { it.copy(isDrillDownLoading = false, drillDownError = "Transactions are unavailable.") }
            return
        }
        val currentRequestId = ++drillDownRequestId
        viewModelScope.launch {
            try {
                val result = repository.fetchTransactions(
                    page = 1,
                    pageSize = 100,
                    category = null,
                    transactionType = null,
                    startDate = null,
                    endDate = null,
                    search = null,
                    minAmount = null,
                    maxAmount = null,
                    statementId = statement.id,
                    sortBy = null,
                    sortDir = null,
                )
                if (currentRequestId != drillDownRequestId ||
                    _uiState.value.selectedStatement?.id != statement.id
                ) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        statementTransactions = result.items,
                        isDrillDownLoading = false,
                        drillDownError = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (currentRequestId != drillDownRequestId ||
                    _uiState.value.selectedStatement?.id != statement.id
                ) {
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isDrillDownLoading = false,
                        drillDownError = e.userMessage,
                    )
                }
            }
        }
    }

    fun retryStatementDrillDown() {
        _uiState.value.selectedStatement?.let { openStatementDrillDown(it) }
    }

    fun closeStatementDrillDown() {
        drillDownRequestId += 1
        _uiState.update {
            it.copy(
                selectedStatement = null,
                statementTransactions = emptyList(),
                isDrillDownLoading = false,
                drillDownError = null,
            )
        }
    }
}
