package com.arthapilot.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthapilot.app.domain.model.Alert
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.ui.theme.RonasColors
import com.arthapilot.app.ui.theme.RonasCard
import com.arthapilot.app.ui.theme.RonasPillButton
import com.arthapilot.app.ui.theme.RonasMetricItem
import com.arthapilot.app.ui.theme.RonasCircularBadge
import com.arthapilot.app.ui.theme.RonasCategoryTable
import com.arthapilot.app.ui.theme.RonasCategoryRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onUploadClick: () -> Unit,
    onStatementsClick: () -> Unit,
    onLogout: () -> Unit,
    refreshSignal: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(refreshSignal) {
        if (refreshSignal) {
            viewModel.refresh()
            onRefreshHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    Scaffold(
        containerColor = RonasColors.BgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Portfolio", color = RonasColors.TextPrimary) },
                actions = {
                    RonasPillButton(text = "+ Add txn", onClick = { showCreateDialog = true })
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onStatementsClick) {
                        Icon(Icons.Default.History, contentDescription = "Statement history", tint = RonasColors.TextPrimary)
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onUploadClick) {
                Icon(Icons.Default.Add, contentDescription = "Upload statement")
            }
        },
    ) { padding ->
        val totalIncome = uiState.transactions.filter { it.transactionType == "credit" }.sumOf { it.amount }
        val totalExpenses = uiState.transactions.filter { it.transactionType == "debit" }.sumOf { it.amount }
        val netFlow = totalIncome - totalExpenses
        val dotColors = listOf(
            RonasColors.TextPrimary,
            RonasColors.TextSecondary,
            RonasColors.TextMuted,
            RonasColors.DividerMuted,
            RonasColors.BorderSubtle,
        )
        val categoryRows = uiState.transactions
            .groupBy { it.category to it.transactionType }
            .map { (key, txs) ->
                RonasCategoryRow(
                    label = key.first,
                    transactionType = key.second,
                    amount = txs.sumOf { it.amount },
                )
            }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                RonasCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Portfolio summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RonasColors.TextPrimary,
                            maxLines = 1,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RonasMetricItem(
                                label = "Income",
                                value = "₹${"%.0f".format(totalIncome)}",
                                valueColor = RonasColors.GrowthGreen,
                                indicator = "↑",
                                indicatorColor = RonasColors.GrowthGreen,
                                modifier = Modifier.weight(1f),
                            )
                            RonasMetricItem(
                                label = "Expenses",
                                value = "₹${"%.0f".format(totalExpenses)}",
                                valueColor = RonasColors.DeclineRed,
                                indicator = "↓",
                                indicatorColor = RonasColors.DeclineRed,
                                modifier = Modifier.weight(1f),
                            )
                            RonasMetricItem(
                                label = "Net flow",
                                value = "₹${"%.0f".format(netFlow)}",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (categoryRows.isNotEmpty()) {
                item {
                    RonasCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "Category breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = RonasColors.TextPrimary,
                                maxLines = 1,
                            )
                            RonasCategoryTable(rows = categoryRows)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text("Search transactions") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = uiState.transactionTypeFilter == null,
                        onClick = { viewModel.setTransactionTypeFilter(null) },
                        label = { Text("All") },
                    )
                    FilterChip(
                        selected = uiState.transactionTypeFilter == "credit",
                        onClick = { viewModel.setTransactionTypeFilter("credit") },
                        label = { Text("Income") },
                    )
                    FilterChip(
                        selected = uiState.transactionTypeFilter == "debit",
                        onClick = { viewModel.setTransactionTypeFilter("debit") },
                        label = { Text("Expense") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = viewModel::clearFilters,
                        label = { Text("Clear filters") },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.minAmount,
                        onValueChange = viewModel::setMinAmount,
                        label = { Text("Min") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = uiState.maxAmount,
                        onValueChange = viewModel::setMaxAmount,
                        label = { Text("Max") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::applyAmountFilters) { Text("Apply") }
                }
            }

            item {
                CategoryFilterRow(
                    options = uiState.categoryOptions,
                    selected = uiState.categoryFilter,
                    onSelect = viewModel::setCategoryFilter,
                )
            }

            if (uiState.alerts.isNotEmpty()) {
                item { AlertsSection(alerts = uiState.alerts) }
            }

            when {
                uiState.isLoading && uiState.transactions.isEmpty() -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                uiState.transactions.isEmpty() -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No transactions yet", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = onUploadClick) { Text("Upload a statement") }
                            }
                        }
                    }
                }
                else -> {
                    if (uiState.offline) {
                        item {
                            Text(
                                text = "Offline showing cached transactions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(uiState.transactions, key = { it.id }) { transaction ->
                        TransactionCard(
                            transaction = transaction,
                            onClick = { selectedTransaction = transaction },
                            onEdit = { editingTransaction = transaction },
                            onDelete = { transactionToDelete = transaction },
                        )
                    }
                    if (uiState.hasMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.height(24.dp))
                            }
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                        }
                    }
                }
            }
        }

    }

    selectedTransaction?.let { transaction ->
        CategoryCorrectionDialog(
            transaction = transaction,
            choices = uiState.categoryOptions,
            onDismiss = { selectedTransaction = null },
            onConfirm = { category ->
                viewModel.updateCategory(transaction.id, category)
                selectedTransaction = null
            },
        )
    }

    if (showCreateDialog) {
        TransactionFormDialog(
            title = "Add transaction",
            initial = null,
            categories = uiState.categoryOptions,
            isSaving = uiState.isSaving,
            onDismiss = { showCreateDialog = false },
            onSave = { type, amount, date, description, category, balance ->
                viewModel.createTransaction(type, amount, date, description, category, balance)
                showCreateDialog = false
            },
        )
    }

    editingTransaction?.let { transaction ->
        TransactionFormDialog(
            title = "Edit transaction",
            initial = transaction,
            categories = uiState.categoryOptions,
            isSaving = uiState.isSaving,
            onDismiss = { editingTransaction = null },
            onSave = { type, amount, date, description, category, balance ->
                viewModel.updateTransaction(
                    id = transaction.id,
                    transactionType = type,
                    amount = amount,
                    date = date,
                    description = description,
                    category = category,
                    balance = balance,
                )
                editingTransaction = null
            },
        )
    }

    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { if (uiState.deletingId == null) transactionToDelete = null },
            title = { Text("Delete transaction?") },
            text = {
                Text(
                    "Delete \"${transaction.description}\" on ${transaction.date} for ${amountText(transaction)}? " +
                        "This action cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTransaction(transaction.id)
                        transactionToDelete = null
                    },
                    enabled = uiState.deletingId != transaction.id,
                ) {
                    Text(if (uiState.deletingId == transaction.id) "Deleting…" else "Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { transactionToDelete = null },
                    enabled = uiState.deletingId != transaction.id,
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AlertsSection(alerts: List<Alert>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        alerts.forEach { alert ->
            val color = if (alert.severity == "warning") {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(color.copy(alpha = 0.08f)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.width(4.dp).height(28.dp).background(color, MaterialTheme.shapes.small))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            alert.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        Text(
                            alert.message,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val allOptions = listOf(null as String?) + options
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(allOptions.size) { index ->
            val category = allOptions[index]
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category ?: "All") },
            )
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    RonasCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${transaction.date} · ${transaction.category}" +
                        if (transaction.isRecurring) " · ↻ recurring" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit transaction")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete transaction")
            }
            Text(
                amountText(transaction),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.transactionType == "credit") {
                    RonasColors.GrowthGreen
                } else {
                    RonasColors.DeclineRed
                },
            )
        }
    }
}

private fun amountText(transaction: Transaction): String {
    val sign = if (transaction.transactionType == "credit") "+" else "−"
    return "$sign₹${formatAmount(transaction.amount)}"
}

@Composable
private fun CategoryCorrectionDialog(
    transaction: Transaction,
    choices: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    transaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.height(240.dp),
                ) {
                    items(choices.size) { index ->
                        val choice = choices[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(choice) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(
                                choice,
                                color = if (choice == transaction.category) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (choice == transaction.category) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionFormDialog(
    title: String,
    initial: Transaction?,
    categories: List<String>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, String, String, Double?) -> Unit,
) {
    var type by remember(initial) { mutableStateOf(initial?.transactionType ?: "debit") }
    var amountText by remember(initial) { mutableStateOf(initial?.amount?.toString() ?: "") }
    var dateText by remember(initial) { mutableStateOf(initial?.date ?: java.time.LocalDate.now().toString()) }
    var description by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var category by remember(initial) { mutableStateOf(initial?.category ?: (categories.firstOrNull() ?: "Uncategorized")) }
    var balanceText by remember(initial) { mutableStateOf(initial?.balance?.toString() ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "credit", onClick = { type = "credit" }, label = { Text("Income") })
                    FilterChip(selected = type == "debit", onClick = { type = "debit" }, label = { Text("Expense") })
                }
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        categories.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice) }, onClick = {
                                category = choice
                                categoryExpanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(value = dateText, onValueChange = { dateText = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
                OutlinedTextField(value = balanceText, onValueChange = { balanceText = it }, label = { Text("Balance (optional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    error = "Amount must be greater than zero"
                    return@TextButton
                }
                if (description.isBlank()) {
                    error = "Description is required"
                    return@TextButton
                }
                try {
                    java.time.LocalDate.parse(dateText)
                } catch (_: java.time.format.DateTimeParseException) {
                    error = "Date must be YYYY-MM-DD"
                    return@TextButton
                }
                val balance = balanceText.toDoubleOrNull()
                if (balanceText.isNotBlank() && balance == null) {
                    error = "Balance must be a number"
                    return@TextButton
                }
                onSave(type, amount, dateText, description.trim(), category, balance)
            }, enabled = !isSaving) {
                Text(if (isSaving) "Saving�" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        },
    )
}
