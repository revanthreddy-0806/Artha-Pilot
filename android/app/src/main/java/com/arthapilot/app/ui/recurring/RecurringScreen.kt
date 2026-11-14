package com.arthapilot.app.ui.recurring

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.arthapilot.app.ui.theme.RonasColors
import com.arthapilot.app.ui.theme.RonasCard
import com.arthapilot.app.ui.theme.RonasTickStripBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthapilot.app.domain.model.RecurringItem
import com.arthapilot.app.domain.model.Transaction
import com.arthapilot.app.ui.home.formatAmount
import com.arthapilot.app.ui.insights.trendMonthLabel
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedRecurring by remember { mutableStateOf<RecurringItem?>(null) }
    var filterMode by remember { mutableStateOf("all") } // all, essential, subs
    var sortMode by remember { mutableStateOf("amount_desc") } // amount_desc, amount_asc, freq, az
    var filterExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    val items = uiState.items
    val totalMonthly = items.sumOf { it.amount }
    val essentialCategories = setOf("Rent", "Utilities", "Bills & Recurring", "Investments", "Transfers")
    val essentialTotal = items.filter { it.category in essentialCategories || it.kind == "emi" }.sumOf { it.amount }
    val subsTotal = (totalMonthly - essentialTotal).coerceAtLeast(0.0)

    val filtered = when (filterMode) {
        "essential" -> items.filter { it.category in essentialCategories || it.kind == "emi" }
        "subs" -> items.filter { !(it.category in essentialCategories || it.kind == "emi") }
        else -> items
    }
    val sorted = when (sortMode) {
        "amount_asc" -> filtered.sortedBy { it.amount }
        "freq" -> filtered.sortedByDescending { it.occurrences }
        "az" -> filtered.sortedBy { it.description.lowercase() }
        else -> filtered.sortedByDescending { it.amount }
    }

    Scaffold(
        containerColor = RonasColors.BgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Recurring & EMIs", color = RonasColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("\u2039") }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No recurring charges detected", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Charges that repeat monthly are listed here",
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
                                text = "Offline \u00B7 showing last fetched detections",
                                style = MaterialTheme.typography.labelMedium,
                                color = RonasColors.TextSecondary,
                            )
                        }
                    }
                    // Hero card
                    item {
                        RonasCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Monthly Committed", fontSize = 12.sp, color = RonasColors.TextMuted)
                                        Text(text = "\u20B9${formatAmount(totalMonthly)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RonasColors.DeclineRed, maxLines = 1)
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "Annualized (12m)", fontSize = 12.sp, color = RonasColors.TextMuted)
                                        Text(text = "\u20B9${formatAmount(totalMonthly * 12)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary, maxLines = 1)
                                    }
                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text(text = "Recurring Series", fontSize = 12.sp, color = RonasColors.TextMuted)
                                        Text(text = "${items.size} active", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RonasColors.GrowthGreen, maxLines = 1)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                RonasTickStripBar(
                                    primaryFraction = (essentialTotal / totalMonthly.coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f),
                                    secondaryFraction = (subsTotal / totalMonthly.coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Essential: \u20B9${formatAmount(essentialTotal)} \u2022 Subscriptions/Other: \u20B9${formatAmount(subsTotal)}",
                                    fontSize = 11.sp,
                                    color = RonasColors.TextMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    // Section header + filter
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Detected Recurring Payments", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val filterLabel = when (filterMode) {
                                    "essential" -> "Essential Bills Only"
                                    "subs" -> "Subscriptions & Discretionary Only"
                                    else -> "All Commitments"
                                }
                                val sortLabel = when (sortMode) {
                                    "amount_asc" -> "Lowest Amount \u2191"
                                    "freq" -> "Most Frequent"
                                    "az" -> "A\u2013Z"
                                    else -> "Highest Amount \u2193"
                                }
                                Text(text = "$filterLabel \u2022 $sortLabel", fontSize = 12.sp, color = RonasColors.TextMuted, maxLines = 1)
                            }
                            Box {
                                Surface(onClick = { filterExpanded = true }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                                    Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filter recurring", tint = RonasColors.TextSecondary, modifier = Modifier.padding(8.dp).size(18.dp))
                                }
                                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }, containerColor = RonasColors.SurfaceElevated, border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                                    Text(text = "Filter", fontSize = 11.sp, color = RonasColors.TextMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                    DropdownMenuItem(text = { Text("All Commitments", color = if (filterMode == "all") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { filterMode = "all"; filterExpanded = false })
                                    DropdownMenuItem(text = { Text("Essential Bills Only", color = if (filterMode == "essential") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { filterMode = "essential"; filterExpanded = false })
                                    DropdownMenuItem(text = { Text("Subscriptions & Discretionary Only", color = if (filterMode == "subs") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { filterMode = "subs"; filterExpanded = false })
                                    HorizontalDivider(color = RonasColors.BorderSubtle, thickness = 1.dp)
                                    Text(text = "Sort", fontSize = 11.sp, color = RonasColors.TextMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                    DropdownMenuItem(text = { Text("Highest Amount \u2193", color = if (sortMode == "amount_desc") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { sortMode = "amount_desc"; filterExpanded = false })
                                    DropdownMenuItem(text = { Text("Lowest Amount \u2191", color = if (sortMode == "amount_asc") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { sortMode = "amount_asc"; filterExpanded = false })
                                    DropdownMenuItem(text = { Text("Most Frequent", color = if (sortMode == "freq") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { sortMode = "freq"; filterExpanded = false })
                                    DropdownMenuItem(text = { Text("A\u2013Z", color = if (sortMode == "az") RonasColors.GrowthGreen else RonasColors.TextPrimary) }, onClick = { sortMode = "az"; filterExpanded = false })
                                }
                            }
                        }
                    }
                    items(sorted, key = { it.transactionId }) { item ->
                        RecurringCard(item = item, totalMonthly = totalMonthly, transactions = uiState.transactions, onClick = { selectedRecurring = item })
                    }
                }
            }
        }
    }

    if (selectedRecurring != null) {
        val item = selectedRecurring!!
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val genericTokens = setOf("SAMPLE", "PAYMENT", "ORDER", "UNIQUE", "TXN", "CHARGE", "RECURRING")
        val tokens = item.description.split(Regex("\\\\s+")).map { it.trim() }.filter { it.length >= 4 && it.uppercase(Locale.US) !in genericTokens }
        val matched = uiState.transactions.filter { t ->
            t.transactionType == "debit" && (
                t.description.contains(item.description, ignoreCase = true) ||
                item.description.contains(t.description, ignoreCase = true) ||
                t.description.contains(item.description.take(8), ignoreCase = true) ||
                tokens.any { tok -> t.description.contains(tok, ignoreCase = true) }
            )
        }.sortedByDescending { it.date }
        val history = if (matched.isNotEmpty()) matched else synthesizeRecurringHistory(item)
        ModalBottomSheet(onDismissRequest = { selectedRecurring = null }, sheetState = sheetState, containerColor = Color(0xFF111314)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.description, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = "${item.category} \u2022 every ${item.intervalDays} days", fontSize = 13.sp, color = RonasColors.TextSecondary)
                    }
                    Surface(color = RonasColors.GrowthGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                        Text(text = "Active Recurring", color = RonasColors.GrowthGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                // 4-metric grid
                Surface(color = Color(0xFF16191A), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCell(label = "Average Amount", value = "\u20B9${formatAmount(item.amount)}", color = RonasColors.TextPrimary, modifier = Modifier.weight(1f))
                            MetricCell(label = "Annualized Cost", value = "\u20B9${formatAmount(item.amount * 12)}/yr", color = RonasColors.TextPrimary, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricCell(label = "Total Paid (Recorded)", value = "\u20B9${formatAmount(history.sumOf { it.amount })} (${history.size} charges)", color = RonasColors.TextPrimary, modifier = Modifier.weight(1f))
                            MetricCell(label = "Last / Next Expected", value = "${item.lastDate} / ${item.nextExpectedDate}", color = RonasColors.TextPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Text(text = "Payment History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary)
                if (history.isEmpty()) {
                    Text(text = "No recorded payments", fontSize = 12.sp, color = RonasColors.TextMuted)
                } else {
                    history.forEach { t ->
                        Surface(color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = t.date, fontSize = 12.sp, color = RonasColors.TextMuted)
                                    Text(text = t.description, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(text = "-\u20B9${formatAmount(t.amount)}", fontWeight = FontWeight.Bold, color = RonasColors.DeclineRed)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun resolveCategory(item: RecurringItem, transactions: List<Transaction>): String {
    val cat = item.category.trim()
    if (cat.isNotBlank() && !cat.equals("RECURRING", ignoreCase = true) && !cat.equals("Bills & Recurring", ignoreCase = true) && !cat.equals("Uncategorized", ignoreCase = true)) {
        return cat
    }
    val match = transactions.firstOrNull { t ->
        t.description.contains(item.description.take(8), ignoreCase = true)
    }?.category
    if (!match.isNullOrBlank()) return match
    val desc = item.description.uppercase(Locale.US)
    return when {
        desc.contains("BIGBASKET") || desc.contains("DMART") || desc.contains("GROCERY") || desc.contains("SUPERMARKET") -> "Groceries"
        desc.contains("SWIGGY") || desc.contains("ZOMATO") || desc.contains("FOOD") -> "Food & Dining"
        desc.contains("GYM") || desc.contains("CULT") || desc.contains("FITNESS") -> "Health & Fitness"
        desc.contains("RENT") -> "Rent"
        desc.contains("SIP") || desc.contains("MUTUAL") || desc.contains("INVEST") -> "Investments"
        desc.contains("NETFLIX") || desc.contains("SPOTIFY") || desc.contains("PRIME") || desc.contains("SUBSCRIPTION") -> "Subscriptions"
        item.kind == "emi" -> "EMI"
        else -> "Uncategorized"
    }
}

@Composable
private fun MetricCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, fontSize = 11.sp, color = RonasColors.TextMuted)
        Text(text = value, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecurringCard(item: RecurringItem, totalMonthly: Double, transactions: List<Transaction>, onClick: () -> Unit) {
    val isEmi = item.kind == "emi"
    val essentialCategories = setOf("Rent", "Utilities", "Bills & Recurring", "Investments", "Transfers")
    val isEssential = isEmi || item.category in essentialCategories
    val share = item.amount / totalMonthly.coerceAtLeast(0.001) * 100.0
    val nextDue = item.nextExpectedDate
    RonasCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.description,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RonasColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "-\u20B9${formatAmount(item.amount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RonasColors.DeclineRed,
                    maxLines = 1,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = RonasColors.GrowthGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                    Text(text = resolveCategory(item, transactions), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RonasColors.GrowthGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), maxLines = 1)
                }
                Surface(color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                    Text(text = "Monthly", fontSize = 11.sp, color = RonasColors.TextSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
                Surface(color = RonasColors.GrowthGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(50), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                    Text(text = "Next est: ${nextDue}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RonasColors.GrowthGreen, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${String.format(Locale.US, "%.1f", share)}% of fixed spend \u2022 \u20B9${formatAmount(item.amount * 12)}/yr",
                fontSize = 12.sp,
                color = RonasColors.TextMuted,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap for payment history \u2192",
                fontSize = 11.sp,
                color = RonasColors.TextMuted,
            )
        }
    }
}
private fun synthesizeRecurringHistory(item: RecurringItem): List<Transaction> {
    val count = item.occurrences.coerceAtLeast(1)
    val interval = item.intervalDays.coerceAtLeast(1)
    val result = mutableListOf<Transaction>()
    var date = try { LocalDate.parse(item.lastDate) } catch (e: Exception) { null }
    for (i in 0 until count) {
        val d = date?.minusDays((i * interval).toLong())?.toString() ?: item.lastDate
        result.add(
            Transaction(
                id = -(i + 1).toLong(),
                statementId = null,
                date = d,
                description = item.description,
                amount = item.amount,
                transactionType = "debit",
                balance = null,
                category = item.category,
                confidence = item.confidence,
                isRecurring = true,
            )
        )
    }
    return result.sortedByDescending { it.date }
}