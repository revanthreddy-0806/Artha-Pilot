package com.arthapilot.app.ui.budgets

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.arthapilot.app.ui.theme.RonasColors
import com.arthapilot.app.ui.theme.RonasCard
import com.arthapilot.app.ui.theme.RonasPillButton
import com.arthapilot.app.ui.theme.RonasTickStripBar
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthapilot.app.domain.model.Budget
import com.arthapilot.app.domain.model.Transaction
import androidx.compose.ui.graphics.Color
import com.arthapilot.app.ui.insights.previousMonth
import com.arthapilot.app.ui.insights.trendMonthLabel
import com.arthapilot.app.ui.home.FALLBACK_CATEGORIES
import com.arthapilot.app.ui.home.formatAmount
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedBudget by remember { mutableStateOf<Budget?>(null) }

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
                title = { Text("Budgets", color = RonasColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("‹") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add budget")
            }
        },
    ) { padding ->
        when {
            uiState.isLoading && uiState.budgets.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.budgets.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No budgets yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Set a monthly limit per category to track overruns",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RonasColors.TextSecondary,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    if (uiState.offline) {
                        item {
                            Text(
                                text = "Offline · showing last fetched budgets",
                                style = MaterialTheme.typography.labelMedium,
                                color = RonasColors.TextSecondary,
                            )
                        }
                    }
                    items(uiState.budgets, key = { it.category }) { budget ->
                        BudgetCard(
                            budget = budget,
                            onClick = { selectedBudget = budget },
                            onDelete = { viewModel.deleteBudget(budget.category) },
                        )
                    }
                }
            }
        }
    }

    if (selectedBudget != null) {
        BudgetDetailSheet(
            budget = selectedBudget!!,
            transactions = uiState.transactions,
            onDismiss = { selectedBudget = null },
            onEdit = { limit ->
                viewModel.setBudget(selectedBudget!!.category, limit)
                selectedBudget = null
            },
            onDelete = {
                viewModel.deleteBudget(selectedBudget!!.category)
                selectedBudget = null
            },
        )
    }

    if (showAddDialog) {
        AddBudgetDialog(
            categories = uiState.budgets.map { it.category }.let { existing ->
                FALLBACK_CATEGORIES.filterNot { it in existing }
            },
            onDismiss = { showAddDialog = false },
            onConfirm = { category, limit ->
                viewModel.setBudget(category, limit)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun BudgetCard(
    budget: Budget,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = if (budget.overrun) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    RonasCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budget.category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = RonasColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "₹${formatAmount(budget.spent)} of ₹${formatAmount(budget.monthlyLimit)} spent",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RonasColors.TextSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${"%.1f".format(Locale.US, budget.percentage)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${budget.category} budget",
                            tint = RonasColors.TextSecondary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (budget.percentage / 100.0).coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = color,
            )
            if (budget.overrun) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Over budget by ₹${formatAmount(budget.spent - budget.monthlyLimit)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap to view breakdown & transactions →",
                fontSize = 12.sp,
                color = RonasColors.TextMuted,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var amountText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Monthly limit (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCategory, amount ?: 0.0) },
                enabled = selectedCategory.isNotBlank() && (amount ?: 0.0) > 0,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetDetailSheet(
    budget: Budget,
    transactions: List<Transaction>,
    onDismiss: () -> Unit,
    onEdit: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editLimitText by remember { mutableStateOf(budget.monthlyLimit.toString()) }
    var showEdit by remember { mutableStateOf(false) }
    var filterTab by remember { mutableStateOf(2) } // 0 current, 1 prev, 2 both

    val currMonth = remember(transactions, budget) {
        val categoryTxns = transactions.filter { t -> t.transactionType == "debit" && t.category.equals(budget.category, ignoreCase = true) }
        categoryTxns.mapNotNull { it.date.take(7) }.distinct().maxOrNull()
            ?: (transactions.mapNotNull { it.date.take(7) }.distinct().maxOrNull() ?: java.time.LocalDate.now().toString().take(7))
    }
    val prevMonth = previousMonth(currMonth)
    val currLabel = trendMonthLabel(currMonth)
    val prevLabel = trendMonthLabel(prevMonth)

    val currTxns = transactions.filter { t ->
        t.transactionType == "debit" && t.category.equals(budget.category, ignoreCase = true) && t.date.startsWith(currMonth)
    }
    val prevTxns = transactions.filter { t ->
        t.transactionType == "debit" && t.category.equals(budget.category, ignoreCase = true) && t.date.startsWith(prevMonth)
    }

    val status = when {
        budget.percentage >= 100.0 -> Triple("Over Budget", RonasColors.DeclineRed, RonasColors.DeclineRed)
        budget.percentage >= 80.0 -> Triple("Near Limit", Color(0xFFFFB020), Color(0xFFFFB020))
        else -> Triple("On Track", RonasColors.GrowthGreen, RonasColors.GrowthGreen)
    }

    val spent = budget.spent
    val limit = budget.monthlyLimit
    val remaining = limit - spent
    val pct = (spent / limit.coerceAtLeast(0.001)) * 100.0
    val safeDaily = if (remaining > 0) remaining / 30.0 else 0.0
    val projected = if (spent > 0) spent + (spent / 30.0) * 15 else spent

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = RonasColors.BgCanvas) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = budget.category, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = "Budget & Transaction Drill-Down", fontSize = 13.sp, color = RonasColors.TextSecondary)
                }
                Surface(color = status.second.copy(alpha = 0.15f), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, status.second)) {
                    Text(text = status.first, color = status.second, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            RonasTickStripBar(primaryFraction = (spent / limit.coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f), secondaryFraction = ((limit - spent) / limit.coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f))
            Text(text = "%.1f".format(java.util.Locale.US, pct) + "% utilized", fontSize = 12.sp, color = RonasColors.TextMuted)
            // 4-metric grid
            Surface(color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCell(label = "Monthly Limit", value = "?${formatAmount(limit)}", color = RonasColors.TextPrimary, modifier = Modifier.weight(1f))
                        MetricCell(label = "Actual Spent", value = "?${formatAmount(spent)}", color = if (budget.overrun) RonasColors.DeclineRed else RonasColors.TextPrimary, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCell(label = if (remaining >= 0) "Remaining" else "Overspent", value = if (remaining >= 0) "?${formatAmount(remaining)} left" else "?${formatAmount(-remaining)} over", color = if (remaining >= 0) RonasColors.GrowthGreen else RonasColors.DeclineRed, modifier = Modifier.weight(1f))
                        MetricCell(label = "Safe Daily", value = "?${formatAmount(safeDaily)}/day", color = RonasColors.TextPrimary, modifier = Modifier.weight(1f))
                    }
                    Text(text = "Projected month-end: ?${formatAmount(projected)}", fontSize = 12.sp, color = RonasColors.TextMuted)
                }
            }
            // MoM summary strip
            Surface(color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(text = currLabel, fontSize = 12.sp, color = RonasColors.TextMuted)
                        Text(text = "?${formatAmount(currTxns.sumOf { it.amount })}", fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary)
                        Text(text = "${currTxns.size} txns", fontSize = 11.sp, color = RonasColors.TextMuted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = prevLabel, fontSize = 12.sp, color = RonasColors.TextMuted)
                        Text(text = "?${formatAmount(prevTxns.sumOf { it.amount })}", fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary)
                        Text(text = "${prevTxns.size} txns", fontSize = 11.sp, color = RonasColors.TextMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val delta = if (prevTxns.isNotEmpty()) (currTxns.sumOf { it.amount } - prevTxns.sumOf { it.amount }) / prevTxns.sumOf { it.amount } * 100.0 else 0.0
                        val higher = currTxns.sumOf { it.amount } > prevTxns.sumOf { it.amount }
                        Text(text = "vs Prev", fontSize = 12.sp, color = RonasColors.TextMuted)
                        Text(text = if (prevTxns.isEmpty()) "New" else (if (higher) "? %.1f%%".format(java.util.Locale.US, delta) else "? %.1f%%".format(java.util.Locale.US, kotlin.math.abs(delta))), fontWeight = FontWeight.Bold, color = if (higher) RonasColors.DeclineRed else RonasColors.GrowthGreen)
                    }
                }
            }
            // Filter pills
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Current Month (${currTxns.size})", "Previous Month (${prevTxns.size})", "Both Months").forEachIndexed { idx, label ->
                    Surface(onClick = { filterTab = idx }, color = if (filterTab == idx) RonasColors.SurfaceElevated else RonasColors.BgCanvas, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, if (filterTab == idx) RonasColors.GrowthGreen else RonasColors.BorderSubtle)) {
                        Text(text = label, color = if (filterTab == idx) RonasColors.GrowthGreen else RonasColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            // Transaction lists
            if (filterTab == 0 || filterTab == 2) {
                Text(text = "Current Month ($currLabel) � Spent Transactions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = RonasColors.TextPrimary)
                TxnList(txns = currTxns, emptyMsg = "No spent transactions in $currLabel for ${budget.category}.")
            }
            if (filterTab == 1 || filterTab == 2) {
                Text(text = "Previous Month ($prevLabel) � Spent Transactions", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = RonasColors.TextPrimary)
                TxnList(txns = prevTxns, emptyMsg = "No spent transactions in $prevLabel for ${budget.category}.")
            }
            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showEdit = true }, modifier = Modifier.weight(1f)) { Text("Edit Limit") }
                Button(onClick = onDelete, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = RonasColors.DeclineRed), modifier = Modifier.weight(1f)) { Text("Delete Budget") }
            }
            if (showEdit) {
                OutlinedTextField(value = editLimitText, onValueChange = { editLimitText = it }, label = { Text("New limit") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { editLimitText.toDoubleOrNull()?.let { onEdit(it) } }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, color = RonasColors.TextMuted)
        Text(text = value, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TxnList(txns: List<Transaction>, emptyMsg: String) {
    if (txns.isEmpty()) {
        Text(text = emptyMsg, fontSize = 12.sp, color = RonasColors.TextMuted)
    } else {
        val highest = txns.maxByOrNull { it.amount }
        Text(text = "${txns.size} transactions � Highest: -?${formatAmount(highest?.amount ?: 0.0)}", fontSize = 12.sp, color = RonasColors.TextMuted)
        txns.forEach { t ->
            Surface(color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = t.description, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = t.date, fontSize = 12.sp, color = RonasColors.TextMuted)
                    }
                    Text(text = "-?${formatAmount(t.amount)}", fontWeight = FontWeight.Bold, color = RonasColors.DeclineRed)
                }
            }
        }
    }
}
