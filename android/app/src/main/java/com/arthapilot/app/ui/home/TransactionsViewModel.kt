package com.arthapilot.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null,
    val offline: Boolean = false,
    val page: Int = 0,
    val pageSize: Int = 50,
    val total: Int = 0,
    val categoryFilter: String? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

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
