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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import java.util.Locale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthapilot.app.domain.model.Alert
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.ui.theme.RonasColors
import com.arthapilot.app.ui.theme.RonasCard
import com.arthapilot.app.ui.theme.RonasPillButton
import com.arthapilot.app.ui.theme.RonasMetricItem
import com.arthapilot.app.ui.theme.RonasTickStripBar
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
    onNavigateToAssistant: () -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var isBalanceHidden by remember { mutableStateOf(false) }
    var selectedMonthFilter by remember { mutableStateOf("All Time") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var monthFilterExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var txnTypeFilter by remember { mutableStateOf("ALL") }
    var txnSortOrder by remember { mutableStateOf("NEWEST") }
    var txnFilterExpanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    val availableMonths = uiState.transactions.map { it.date.take(7) }.distinct().sortedDescending()
    val periodTransactions = if (selectedMonthFilter == "All Time") uiState.transactions else uiState.transactions.filter { it.date.startsWith(selectedMonthFilter) }

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
                title = { Text("ArthaPilot", color = RonasColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
        val totalIncome = periodTransactions.filter { it.transactionType == "credit" }.sumOf { it.amount }
        val totalExpenses = periodTransactions.filter { it.transactionType == "debit" }.sumOf { it.amount }
        val netFlow = totalIncome - totalExpenses
        val typeFiltered = periodTransactions.filter { txn ->
            (selectedCategoryFilter == null || txn.category == selectedCategoryFilter) &&
                (searchQuery.isBlank() || txn.description.contains(searchQuery, ignoreCase = true) || txn.category.contains(searchQuery, ignoreCase = true) || txn.amount.toString().contains(searchQuery)) &&
                (txnTypeFilter == "ALL" || (txnTypeFilter == "INCOME" && txn.transactionType == "credit") || (txnTypeFilter == "EXPENSE" && txn.transactionType == "debit"))
        }
        val filteredTransactions = when (txnSortOrder) {
            "OLDEST" -> typeFiltered.sortedBy { it.date }
            "HIGHEST" -> typeFiltered.sortedByDescending { it.amount }
            "LOWEST" -> typeFiltered.sortedBy { it.amount }
            else -> typeFiltered.sortedByDescending { it.date }
        }
        val dotColors = listOf(
            RonasColors.TextPrimary,
            RonasColors.TextSecondary,
            RonasColors.TextMuted,
            RonasColors.DividerMuted,
            RonasColors.BorderSubtle,
        )
        val categoryRows = periodTransactions
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
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Portfolio Overview", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Surface(onClick = { isBalanceHidden = !isBalanceHidden }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                                Text(text = if (isBalanceHidden) "Show \u25CE" else "Hide \u25C9", fontSize = 11.sp, color = RonasColors.TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box {
                                Surface(onClick = { monthFilterExpanded = true }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                                    Text(text = "$selectedMonthFilter \u25BE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RonasColors.GrowthGreen, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                                DropdownMenu(expanded = monthFilterExpanded, onDismissRequest = { monthFilterExpanded = false }, containerColor = RonasColors.SurfaceElevated, border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                                    DropdownMenuItem(text = { Text("All Time", color = if (selectedMonthFilter == "All Time") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { selectedMonthFilter = "All Time"; monthFilterExpanded = false })
                                    availableMonths.forEach { m ->
                                        val label = m.takeLast(2) + "/" + m.take(4).takeLast(2)
                                        DropdownMenuItem(text = { Text(label, color = if (selectedMonthFilter == m) RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { selectedMonthFilter = m; monthFilterExpanded = false })
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val incomeVal = if (isBalanceHidden) "\u20B9 \u2022\u2022\u2022\u2022\u2022\u2022" else "+\u20B9${"%.0f".format(totalIncome)}"
                            val expenseVal = if (isBalanceHidden) "\u20B9 \u2022\u2022\u2022\u2022\u2022\u2022" else "-\u20B9${"%.0f".format(totalExpenses)}"
                            val netVal = if (isBalanceHidden) "\u20B9 \u2022\u2022\u2022\u2022\u2022\u2022" else "\u20B9${"%.0f".format(netFlow)}"
                            RonasMetricItem(label = "Income", value = incomeVal, valueColor = RonasColors.GrowthGreen, indicator = "\u2191", indicatorColor = RonasColors.GrowthGreen, modifier = Modifier.weight(1f))
                            RonasMetricItem(label = "Expenses", value = expenseVal, valueColor = RonasColors.DeclineRed, indicator = "\u2193", indicatorColor = RonasColors.DeclineRed, modifier = Modifier.weight(1f))
                            RonasMetricItem(label = "Net Cashflow", value = netVal, valueColor = if (netFlow >= 0) RonasColors.GrowthGreen else RonasColors.DeclineRed, modifier = Modifier.weight(1f))
                        }
                        val savingsRate = if (totalIncome > 0) (totalIncome - totalExpenses) / totalIncome * 100.0 else 0.0
                        RonasTickStripBar(primaryFraction = (totalIncome / (totalIncome + totalExpenses).coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f), secondaryFraction = (totalExpenses / (totalIncome + totalExpenses).coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Savings Rate: ${"%.1f".format(Locale.US, savingsRate)}%", fontSize = 12.sp, color = RonasColors.TextMuted)
                            Text(text = "${periodTransactions.size} txns in period", fontSize = 12.sp, color = RonasColors.TextMuted)
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickActionPill(text = "+ Add Txn", onClick = { /* existing add handled via top bar */ }, modifier = Modifier.weight(1f))
                    QuickActionPill(text = "Upload", onClick = onUploadClick, modifier = Modifier.weight(1f))
                    QuickActionPill(text = "Statements", onClick = onStatementsClick, modifier = Modifier.weight(1f))
                    QuickActionPill(text = "Ask AI", onClick = onNavigateToAssistant, modifier = Modifier.weight(1f))
                }
            }

            if (categoryRows.isNotEmpty()) {
                item {
                    RonasCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "Tap any category to filter transactions", fontSize = 12.sp, color = RonasColors.TextMuted)
                            RonasCategoryTable(rows = categoryRows, selectedCategory = selectedCategoryFilter, onCategoryClick = { cat -> selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat })
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Transactions (${filteredTransactions.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box {
                        Surface(onClick = { txnFilterExpanded = true }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                            Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filter transactions", tint = RonasColors.TextSecondary, modifier = Modifier.padding(8.dp).size(18.dp))
                        }
                        DropdownMenu(expanded = txnFilterExpanded, onDismissRequest = { txnFilterExpanded = false }, containerColor = RonasColors.SurfaceElevated, border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                            Text(text = "Type", fontSize = 11.sp, color = RonasColors.TextMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            DropdownMenuItem(text = { Text("All Types", color = if (txnTypeFilter == "ALL") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { txnTypeFilter = "ALL"; txnFilterExpanded = false })
                            DropdownMenuItem(text = { Text("Income Only (+\u20B9)", color = if (txnTypeFilter == "INCOME") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { txnTypeFilter = "INCOME"; txnFilterExpanded = false })
                            DropdownMenuItem(text = { Text("Expenses Only (-\u20B9)", color = if (txnTypeFilter == "EXPENSE") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { txnTypeFilter = "EXPENSE"; txnFilterExpanded = false })
                            HorizontalDivider(color = RonasColors.BorderSubtle, thickness = 1.dp)
                            Text(text = "Sort", fontSize = 11.sp, color = RonasColors.TextMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                            DropdownMenuItem(text = { Text("Newest First", color = if (txnSortOrder == "NEWEST") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { txnSortOrder = "NEWEST"; txnFilterExpanded = false })
                            DropdownMenuItem(text = { Text("Oldest First", color = if (txnSortOrder == "OLDEST") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { txnSortOrder = "OLDEST"; txnFilterExpanded = false })
                            DropdownMenuItem(text = { Text("Highest Amount \u2193", color = if (txnSortOrder == "HIGHEST") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { txnSortOrder = "HIGHEST"; txnFilterExpanded = false })
                            DropdownMenuItem(text = { Text("Lowest Amount \u2191", color = if (txnSortOrder == "LOWEST") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { txnSortOrder = "LOWEST"; txnFilterExpanded = false })
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search merchant, narration, category, or amount...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("\u2715", color = RonasColors.TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val hasActiveFilters = selectedMonthFilter != "All Time" || selectedCategoryFilter != null || txnTypeFilter != "ALL" || searchQuery.isNotBlank()
            if (hasActiveFilters) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedMonthFilter != "All Time") {
                            Surface(onClick = { selectedMonthFilter = "All Time" }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                                Text(text = "Month: $selectedMonthFilter \u2715", fontSize = 11.sp, color = RonasColors.GrowthGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        if (selectedCategoryFilter != null) {
                            Surface(onClick = { selectedCategoryFilter = null }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                                Text(text = "Category: $selectedCategoryFilter \u2715", fontSize = 11.sp, color = RonasColors.GrowthGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        Surface(onClick = { selectedMonthFilter = "All Time"; selectedCategoryFilter = null; txnTypeFilter = "ALL"; searchQuery = "" }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                            Text(text = "Clear All", fontSize = 11.sp, color = RonasColors.TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
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
                filteredTransactions.isEmpty() -> {
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
                    val grouped = filteredTransactions.groupBy { it.date }
                    grouped.forEach { (date, dayTxns) ->
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = date, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RonasColors.TextSecondary)
                                val dayNet = dayTxns.sumOf { it.amount * if (it.transactionType == "credit") 1 else -1 }
                                Text(text = "Day Net: ${if (dayNet >= 0) "+" else "-"}\u20B9${String.format(Locale.US, "%,.2f", kotlin.math.abs(dayNet))} \u2022 ${dayTxns.size} txns", fontSize = 12.sp, color = if (dayNet >= 0) RonasColors.GrowthGreen else RonasColors.TextMuted)
                            }
                        }
                        items(dayTxns, key = { it.id }) { transaction ->
                            TransactionCard(
                                transaction = transaction,
                                onClick = { selectedTransaction = transaction },
                                onEdit = { editingTransaction = transaction },
                                onDelete = { transactionToDelete = transaction },
                            )
                        }
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val categoryTotal = periodTransactions.filter { it.category == transaction.category }.sumOf { it.amount }
        val categoryCount = periodTransactions.count { it.category == transaction.category }
        val share = if (categoryTotal != 0.0) transaction.amount / categoryTotal * 100.0 else 0.0
        ModalBottomSheet(onDismissRequest = { selectedTransaction = null }, sheetState = sheetState, containerColor = Color(0xFF111314)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = if (transaction.transactionType == "credit") "+\u20B9${formatAmount(transaction.amount)}" else "-\u20B9${formatAmount(transaction.amount)}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = if (transaction.transactionType == "credit") RonasColors.GrowthGreen else RonasColors.DeclineRed)
                Text(text = transaction.description, fontSize = 14.sp, color = RonasColors.TextPrimary)
                Surface(color = Color(0xFF16191A), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Date", fontSize = 12.sp, color = RonasColors.TextMuted)
                            Text(text = transaction.date, fontSize = 12.sp, color = RonasColors.TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Category", fontSize = 12.sp, color = RonasColors.TextMuted)
                            Surface(color = RonasColors.GrowthGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                                Text(text = transaction.category, fontSize = 11.sp, color = RonasColors.GrowthGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Category Total (Period)", fontSize = 12.sp, color = RonasColors.TextMuted)
                            Text(text = "\u20B9${formatAmount(categoryTotal)} across $categoryCount txns", fontSize = 12.sp, color = RonasColors.TextPrimary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Share of Category", fontSize = 12.sp, color = RonasColors.TextMuted)
                            Text(text = "${"%.1f".format(Locale.US, share)}%", fontSize = 12.sp, color = RonasColors.TextPrimary)
                        }
                    }
                }
                Surface(onClick = { selectedCategoryFilter = transaction.category; selectedTransaction = null }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen), modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Filter Home by ${transaction.category}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RonasColors.GrowthGreen, modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
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

@Composable
private fun QuickActionPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle), modifier = modifier) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RonasColors.TextPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
