package com.arthapilot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthapilot.app.data.refresh.DataInvalidationBus
import com.arthapilot.app.data.refresh.NoOpDataInvalidationBus
import com.arthapilot.app.data.repository.BudgetRepository
import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.domain.model.Alert
import com.arthapilot.app.domain.model.ApiException
import com.arthapilot.app.domain.model.Budget
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val deletingId: Long? = null,
    val error: String? = null,
    val offline: Boolean = false,
    val page: Int = 0,
    val pageSize: Int = 50,
    val total: Int = 0,
    val categoryFilter: String? = null,
    val searchQuery: String = "",
    val transactionTypeFilter: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val minAmount: String = "",
    val maxAmount: String = "",
    val sortBy: String = "date",
    val sortDir: String = "desc",
    val hasMore: Boolean = false,
    val categoryOptions: List<String> = FALLBACK_CATEGORIES,
    val alerts: List<Alert> = emptyList(),
)

val FALLBACK_CATEGORIES = listOf(
    "Food & Dining",
    "Groceries",
    "Transport",
    "Utilities",
    "Rent",
    "Entertainment",
    "Shopping",
    "Health & Medical",
    "Education",
    "Salary",
    "Investments",
    "Travel",
    "Bills & Recurring",
    "Transfer",
    "Cash",
    "Taxes",
    "Fees & Charges",
    "Uncategorized",
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val insightsRepository: InsightsRepository,
    private val budgetRepository: BudgetRepository,
    private val invalidationBus: DataInvalidationBus,
) : ViewModel() {

    constructor(
        transactionRepository: TransactionRepository,
        insightsRepository: InsightsRepository,
        budgetRepository: BudgetRepository,
    ) : this(transactionRepository, insightsRepository, budgetRepository, NoOpDataInvalidationBus)

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var requestId: Long = 0

    /** Offline mirror of the last successful fetch. */
    val cachedTransactions: StateFlow<List<Transaction>> =
        transactionRepository.cachedTransactions
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        loadCategoriesAndAlerts()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        load(page = 1, reset = true)
    }

    fun setSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "") }
        refresh()
    }

    fun setTransactionTypeFilter(value: String?) {
        _uiState.update { it.copy(transactionTypeFilter = value) }
        refresh()
    }

    fun setCategoryFilter(category: String?) {
        _uiState.update { it.copy(categoryFilter = category) }
        refresh()
    }

    fun setStartDate(value: String?) {
        _uiState.update { it.copy(startDate = value) }
        refresh()
    }

    fun setEndDate(value: String?) {
        _uiState.update { it.copy(endDate = value) }
        refresh()
    }

    fun setMinAmount(value: String) {
        _uiState.update { it.copy(minAmount = value) }
    }

    fun setMaxAmount(value: String) {
        _uiState.update { it.copy(maxAmount = value) }
    }

    fun applyAmountFilters() {
        refresh()
    }

    fun setSort(sortBy: String, sortDir: String) {
        _uiState.update { it.copy(sortBy = sortBy, sortDir = sortDir) }
        refresh()
    }

    fun clearFilters() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                categoryFilter = null,
                searchQuery = "",
                transactionTypeFilter = null,
                startDate = null,
                endDate = null,
                minAmount = "",
                maxAmount = "",
                sortBy = "date",
                sortDir = "desc",
            )
        }
        refresh()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        _uiState.update { it.copy(isLoading = true) }
        load(page = state.page + 1, reset = false)
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }

    fun createTransaction(
        transactionType: String,
        amount: Double,
        date: String,
        description: String,
        category: String,
        balance: Double?,
    ) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                transactionRepository.createTransaction(
                    transactionType = transactionType,
                    amount = amount,
                    date = date,
                    description = description,
                    category = category,
                    balance = balance,
                )
                invalidationBus.notifyDataChanged()
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun updateTransaction(
        id: Long,
        transactionType: String?,
        amount: Double?,
        date: String?,
        description: String?,
        category: String?,
        balance: Double?,
    ) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                transactionRepository.updateTransaction(
                    id = id,
                    transactionType = transactionType,
                    amount = amount,
                    date = date,
                    description = description,
                    category = category,
                    balance = balance,
                )
                invalidationBus.notifyDataChanged()
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteTransaction(id: Long) {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(deletingId = id, error = null) }
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(id)
                invalidationBus.notifyDataChanged()
                val state = _uiState.value
                val wasLastOnPage = state.transactions.size <= 1
                val targetPage = if (wasLastOnPage && state.page > 1) state.page - 1 else state.page
                load(targetPage.coerceAtLeast(1), reset = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            } finally {
                _uiState.update { it.copy(deletingId = null) }
            }
        }
    }

    fun updateCategory(id: Long, category: String) {
        viewModelScope.launch {
            try {
                transactionRepository.updateCategory(id, category)
                invalidationBus.notifyDataChanged()
                // Re-fetch so filtered lists drop a row that no longer matches the filter.
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _uiState.update { it.copy(error = e.userMessage) }
            }
        }
    }

    fun loadCategoriesAndAlerts() {
        viewModelScope.launch {
            try {
                val categories = insightsRepository.fetchCategories()
                _uiState.update { it.copy(categoryOptions = categories) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: ApiException) {
                // Keep the fallback category list while offline.
            }
        }
        viewModelScope.launch {
            try {
                val budgets = budgetRepository.fetchBudgets()
                val recurring = insightsRepository.fetchRecurring()
                _uiState.update { it.copy(alerts = buildAlerts(budgets, recurring, LocalDate.now())) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: ApiException) {
                // Alerts are best-effort; the transaction list remains usable offline.
            }
        }
    }

    private fun load(page: Int, reset: Boolean) {
        val currentRequestId = ++requestId
        viewModelScope.launch {
            val state = _uiState.value
            try {
                val result = transactionRepository.fetchTransactions(
                    page = page,
                    pageSize = state.pageSize,
                    category = state.categoryFilter,
                    transactionType = state.transactionTypeFilter,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    search = state.searchQuery.takeIf { it.isNotBlank() },
                    minAmount = state.minAmount.toDoubleOrNull(),
                    maxAmount = state.maxAmount.toDoubleOrNull(),
                    sortBy = state.sortBy,
                    sortDir = state.sortDir,
                )
                if (currentRequestId != requestId) return@launch
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
                if (currentRequestId != requestId) return@launch
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
            return@launch
        }
    }
}

/** Computes in-app alerts: budget overruns first, then EMIs due within a week. */
fun buildAlerts(budgets: List<Budget>, recurring: List<RecurringItem>, today: LocalDate): List<Alert> {
    val alerts = mutableListOf<Alert>()

    for (budget in budgets.sortedByDescending { it.percentage }) {
        if (!budget.overrun) continue
        alerts += Alert(
            title = "Budget exceeded",
            message = "${budget.category}: spent ₹${formatAmount(budget.spent)} of " +
                "₹${formatAmount(budget.monthlyLimit)} this month",
            severity = "warning",
        )
    }

    val dueWindow = today.plusDays(7)
    for (item in recurring) {
        if (item.kind != "emi") continue
        val due = parseDate(item.nextExpectedDate) ?: continue
        if (due in today..dueWindow) {
            alerts += Alert(
                title = "EMI due soon",
                message = "${item.description}: ₹${formatAmount(item.amount)} on $due",
                severity = "info",
            )
        }
    }
    return alerts
}

internal fun parseDate(value: String): LocalDate? = try {
    LocalDate.parse(value)
} catch (_: DateTimeParseException) {
    null
}

internal fun formatAmount(amount: Double): String {
    val rounded = String.format(java.util.Locale.US, "%.2f", amount)
    val parts = rounded.split(".")
    return "${parts[0].replace(THOUSANDS_REGEX, "$1,")}.${parts[1]}"
}

private val THOUSANDS_REGEX = Regex("(\\d)(?=(\\d{3})+(?!\\d))")
