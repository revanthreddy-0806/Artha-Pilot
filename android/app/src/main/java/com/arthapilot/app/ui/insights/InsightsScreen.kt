package com.arthapilot.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthapilot.app.domain.model.CategoryBreakdownItem
import com.arthapilot.app.domain.model.InsightsSummary
import com.arthapilot.app.domain.model.MonthTrend
import com.arthapilot.app.ui.home.formatAmount
import com.arthapilot.app.ui.insights.previousMonth
import com.arthapilot.app.ui.theme.RonasCard
import com.arthapilot.app.ui.theme.RonasColors
import com.arthapilot.app.ui.theme.RonasDualBarChart
import com.arthapilot.app.ui.theme.RonasMetricItem
import com.arthapilot.app.ui.theme.RonasPillButton
import com.arthapilot.app.ui.theme.RonasTickStripBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }

    var selectedMonth by remember { mutableStateOf<String?>(null) }
    var compareMonth by remember { mutableStateOf<String?>(null) }
    var catMonth1 by remember { mutableStateOf<String?>(null) }
    var catMonth2 by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(uiState.trends) {
        if (selectedMonth == null || uiState.trends.none { it.month == selectedMonth }) {
            selectedMonth = uiState.trends.lastOrNull { it.income > 0.0 || it.expenses > 0.0 }?.month
                ?: uiState.trends.lastOrNull()?.month
        }
        if (compareMonth == null || uiState.trends.none { it.month == compareMonth }) {
            // default to immediately preceding month
            val sel = selectedMonth
            val idx = uiState.trends.indexOfFirst { it.month == sel }
            compareMonth = if (idx > 0) uiState.trends[idx - 1].month else uiState.trends.getOrNull(uiState.trends.size - 2)?.month ?: uiState.trends.firstOrNull()?.month
        }
        if (catMonth1 == null || uiState.trends.none { it.month == catMonth1 }) {
            catMonth1 = uiState.trends.lastOrNull { it.income > 0.0 || it.expenses > 0.0 }?.month
                ?: uiState.trends.lastOrNull()?.month
        }
        if (catMonth2 == null || uiState.trends.none { it.month == catMonth2 }) {
            val idx = uiState.trends.indexOfFirst { it.month == catMonth1 }
            catMonth2 = if (idx > 0) uiState.trends[idx - 1].month else uiState.trends.getOrNull(uiState.trends.size - 2)?.month ?: uiState.trends.firstOrNull()?.month
        }
    }

    val latestActiveMonth = remember(uiState.trends) {
        uiState.trends.lastOrNull { it.income > 0.0 || it.expenses > 0.0 }?.month
            ?: uiState.trends.lastOrNull()?.month
    }

    var sortMode by remember { mutableStateOf(CategorySort.HighestSpend) }
    var filterExpanded by remember { mutableStateOf(false) }

    val selectedTrend = uiState.trends.firstOrNull { it.month == selectedMonth }
    val compareMonthRaw = compareMonth ?: ""
    val compareTrend = uiState.trends.firstOrNull { it.month == compareMonthRaw }
    val prevMonthRaw = selectedMonth?.let { previousMonth(it) } ?: ""
    val prevTrend = uiState.trends.firstOrNull { it.month == prevMonthRaw }
    val selectedIndex = uiState.trends.indexOfFirst { it.month == selectedMonth }

    Scaffold(
        containerColor = RonasColors.BgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Insights", color = RonasColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
    ) { padding ->
        when {
            uiState.isLoading && uiState.summary == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.summary == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No insights yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Upload a statement to see your monthly picture",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RonasColors.TextSecondary,
                        )
                    }
                }
            }

            else -> {
                val summary = checkNotNull(uiState.summary)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    if (uiState.offline) {
                        item {
                            Text(
                                text = "Offline · showing last fetched insights",
                                style = MaterialTheme.typography.labelMedium,
                                color = RonasColors.TextSecondary,
                            )
                        }
                    }
                    item {
                        SummaryCard(
                            summary = summary,
                            selectedMonth = selectedMonth,
                            selectedTrend = selectedTrend,
                        )
                    }
                    if (uiState.trends.isNotEmpty()) {
                        item {
                            RonasCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Monthly trends",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = RonasColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "Tap any bar to switch month",
                                        fontSize = 12.sp,
                                        color = RonasColors.TextMuted,
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    RonasDualBarChart(
                                        months = uiState.trends.map { trendMonthLabel(it.month) },
                                        primaryValues = uiState.trends.map { it.income.toFloat() },
                                        secondaryValues = uiState.trends.map { it.expenses.toFloat() },
                                        selectedIndex = selectedIndex,
                                        onBarSelected = { idx ->
                                            uiState.trends.getOrNull(idx)?.month?.let { m ->
                                                selectedMonth = m
                                                catMonth1 = m
                                                val i = uiState.trends.indexOfFirst { it.month == m }
                                                compareMonth = if (i > 0) uiState.trends[i - 1].month else uiState.trends.getOrNull(uiState.trends.size - 2)?.month ?: uiState.trends.firstOrNull()?.month
                                            }
                                        },
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Legend(color = RonasColors.TextPrimary, label = "Income")
                                        Legend(color = RonasColors.DeclineRed, label = "Expenses")
                                    }
                                }
                            }
                        }
                        item {
                            MonthlyPerformanceCard(
                                selectedMonth = selectedMonth,
                                selectedTrend = selectedTrend,
                                compareMonthRaw = compareMonthRaw,
                                compareTrend = compareTrend,
                                latestActiveMonth = latestActiveMonth,
                                onJump = { latestActiveMonth?.let { selectedMonth = it } },
                            )
                        }
                    }
                    if (catMonth1 != null && catMonth2 != null) {
                        item {
                            CategoryMoMCard(
                                catMonth1 = catMonth1!!,
                                catMonth2 = catMonth2!!,
                                onCatMonth1Change = { catMonth1 = it },
                                onCatMonth2Change = { catMonth2 = it },
                                categoriesByMonth = uiState.categoriesByMonth,
                                availableMonths = (uiState.trends.map { it.month } + uiState.categoriesByMonth.keys).distinct().sorted(),
                                sortMode = sortMode,
                                onSortChange = { sortMode = it },
                                filterExpanded = filterExpanded,
                                onFilterExpandedChange = { filterExpanded = it },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: InsightsSummary,
    selectedMonth: String?,
    selectedTrend: MonthTrend?,
) {
    val income = selectedTrend?.income ?: summary.income
    val expenses = selectedTrend?.expenses ?: summary.expenses
    val savings = selectedTrend?.savings ?: summary.savings
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = RonasColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            selectedMonth?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selected: ${trendMonthLabel(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RonasColors.TextSecondary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${summary.transactionCount} transactions",
                style = MaterialTheme.typography.bodySmall,
                color = RonasColors.TextSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatBlock(label = "Income", value = "₹${formatAmount(income)}", color = RonasColors.GrowthGreen, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                StatBlock(label = "Expenses", value = "₹${formatAmount(expenses)}", color = RonasColors.DeclineRed, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(12.dp))
                StatBlock(
                    label = "Net Savings",
                    value = "₹${formatAmount(savings)}",
                    color = if (savings >= 0) RonasColors.GrowthGreen else RonasColors.DeclineRed,
                    modifier = Modifier.weight(1f),
                )
            }
            summary.savingsRate?.let { rate ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Savings rate: ${"%.1f".format(Locale.US, rate * 100)}% of income",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun MonthlyPerformanceCard(
    selectedMonth: String?,
    selectedTrend: MonthTrend?,
    compareMonthRaw: String,
    compareTrend: MonthTrend?,
    latestActiveMonth: String?,
    onJump: () -> Unit,
) {
    if (selectedMonth == null) return
    val monthLabel = trendMonthLabel(selectedMonth)
    val compLabel = trendMonthLabel(compareMonthRaw)
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$monthLabel Performance \u2022 vs $compLabel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = RonasColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(12.dp))
            val trend = selectedTrend
            val hasActivity = trend != null && (trend.income > 0.0 || trend.expenses > 0.0)
            if (hasActivity && trend != null) {
                val income = trend.income
                val expenses = trend.expenses
                val savings = trend.savings
                val savingsRate = if (income > 0) (income - expenses) / income * 100.0 else 0.0
                val prevExpenses = compareTrend?.expenses ?: 0.0
                val deltaPercent = if (prevExpenses > 0) (expenses - prevExpenses) / prevExpenses * 100.0 else 0.0
                val deltaText = when {
                    prevExpenses <= 0.0 && expenses > 0.0 -> "New"
                    expenses > prevExpenses && prevExpenses > 0.0 -> "↑ ${"%.1f".format(Locale.US, deltaPercent)}%"
                    expenses < prevExpenses -> "↓ ${"%.1f".format(Locale.US, kotlin.math.abs(deltaPercent))}%"
                    else -> "0.0%"
                }
                val deltaColor = when {
                    prevExpenses <= 0.0 && expenses > 0.0 -> RonasColors.DeclineRed
                    expenses > prevExpenses && prevExpenses > 0.0 -> RonasColors.DeclineRed
                    expenses < prevExpenses -> RonasColors.GrowthGreen
                    else -> RonasColors.TextSecondary
                }
                val avgDaily = expenses / 30.0
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RonasMetricItem(
                        label = "Savings Rate",
                        value = "${"%.1f".format(Locale.US, savingsRate)}%",
                        valueColor = if (savingsRate >= 0) RonasColors.GrowthGreen else RonasColors.DeclineRed,
                        modifier = Modifier.weight(1f),
                    )
                    RonasMetricItem(
                        label = "Expense MoM",
                        value = deltaText,
                        valueColor = deltaColor,
                        modifier = Modifier.weight(1f),
                    )
                    RonasMetricItem(
                        label = "Avg Daily",
                        value = "₹${formatAmount(avgDaily)}/day",
                        valueColor = RonasColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                val total = (income + expenses).coerceAtLeast(0.001)
                val primaryFraction = (income / total).toFloat().coerceIn(0f, 1f)
                val secondaryFraction = (expenses / total).toFloat().coerceIn(0f, 1f)
                RonasTickStripBar(
                    primaryFraction = primaryFraction,
                    secondaryFraction = secondaryFraction,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Net Cashflow: ₹${formatAmount(income - expenses)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RonasColors.TextSecondary,
                    )
                    val spentPct = if (income > 0) expenses / income * 100.0 else 0.0
                    Text(
                        text = "${"%.1f".format(Locale.US, spentPct)}% of income spent",
                        style = MaterialTheme.typography.bodySmall,
                        color = RonasColors.TextSecondary,
                    )
                }
            } else {
                Text(
                    text = "No transactions recorded in $monthLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RonasColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                latestActiveMonth?.let { latest ->
                    RonasPillButton(
                        text = "Jump to ${trendMonthLabel(latest)} (${trendMonthLabel(latest)})",
                        onClick = onJump,
                    )
                }
            }
        }
    }
}

private enum class CategorySort(val label: String) {
    HighestSpend("Highest Spend"),
    LowestSpend("Lowest Spend"),
    BiggestIncrease("Biggest MoM Increase ↑"),
    BiggestDrop("Biggest MoM Drop ↓"),
    AZ("A–Z"),
}

private data class CatMoMRow(
    val name: String,
    val curr: Double,
    val prev: Double,
)

@Composable
private fun CategoryMoMCard(
    catMonth1: String,
    catMonth2: String,
    onCatMonth1Change: (String) -> Unit,
    onCatMonth2Change: (String) -> Unit,
    categoriesByMonth: Map<String, List<CategoryBreakdownItem>>,
    availableMonths: List<String>,
    sortMode: CategorySort,
    onSortChange: (CategorySort) -> Unit,
    filterExpanded: Boolean,
    onFilterExpandedChange: (Boolean) -> Unit,
) {
    val month1Label = trendMonthLabel(catMonth1)
    val month2Label = trendMonthLabel(catMonth2)
    val items1 = categoriesByMonth[catMonth1] ?: emptyList()
    val items2 = categoriesByMonth[catMonth2] ?: emptyList()
    val map1 = items1.associate { it.category to it.amount }
    val map2 = items2.associate { it.category to it.amount }
    val names = (map1.keys + map2.keys).distinct()
    val rows = names.map { CatMoMRow(it, map1[it] ?: 0.0, map2[it] ?: 0.0) }
    val total1 = rows.sumOf { it.curr }
    val total2 = rows.sumOf { it.prev }
    val sorted = when (sortMode) {
        CategorySort.HighestSpend -> rows.sortedByDescending { it.curr }
        CategorySort.LowestSpend -> rows.sortedBy { it.curr }
        CategorySort.BiggestIncrease -> rows.sortedByDescending { it.curr - it.prev }
        CategorySort.BiggestDrop -> rows.sortedBy { it.curr - it.prev }
        CategorySort.AZ -> rows.sortedBy { it.name.lowercase() }
    }

    var menu1Expanded by remember { mutableStateOf(false) }
    var menu2Expanded by remember { mutableStateOf(false) }

    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header top row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Category Spending", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box {
                    Surface(onClick = { onFilterExpandedChange(true) }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                        Icon(imageVector = Icons.Default.FilterList, contentDescription = "Sort categories", tint = RonasColors.TextSecondary, modifier = Modifier.padding(8.dp).size(18.dp))
                    }
                    DropdownMenu(expanded = filterExpanded, onDismissRequest = { onFilterExpandedChange(false) }, containerColor = RonasColors.SurfaceElevated, border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                        CategorySort.values().forEach { mode ->
                            val selected = mode == sortMode
                            DropdownMenuItem(text = { Text(text = mode.label, color = if (selected) RonasColors.GrowthGreen else RonasColors.TextPrimary, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) }, onClick = { onSortChange(mode); onFilterExpandedChange(false) }, trailingIcon = if (selected) { { Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = RonasColors.GrowthGreen, modifier = Modifier.size(16.dp)) } } else null)
                        }
                    }
                }
            }
            // Dual month picker row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Compare:", fontSize = 12.sp, color = RonasColors.TextMuted, maxLines = 1)
                Box {
                    Surface(onClick = { menu1Expanded = true }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.GrowthGreen)) {
                        Text(text = "$month1Label \u25BE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RonasColors.GrowthGreen, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    DropdownMenu(expanded = menu1Expanded, onDismissRequest = { menu1Expanded = false }, containerColor = RonasColors.SurfaceElevated, border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                        availableMonths.forEach { m ->
                            val mTotal = (categoriesByMonth[m] ?: emptyList()).sumOf { it.amount }
                            DropdownMenuItem(text = { Text(text = "${trendMonthLabel(m)} \u2014 \u20B9${formatCompactAmount(mTotal)}", color = if (m == catMonth1) RonasColors.GrowthGreen else RonasColors.TextPrimary, fontSize = 13.sp) }, onClick = { onCatMonth1Change(m); menu1Expanded = false })
                        }
                    }
                }
                Text(text = "vs", fontSize = 13.sp, color = RonasColors.TextMuted, maxLines = 1)
                Box {
                    Surface(onClick = { menu2Expanded = true }, color = RonasColors.SurfaceElevated, shape = RoundedCornerShape(20.dp), border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                        Text(text = "$month2Label \u25BE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RonasColors.TextPrimary, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    DropdownMenu(expanded = menu2Expanded, onDismissRequest = { menu2Expanded = false }, containerColor = RonasColors.SurfaceElevated, border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle)) {
                        availableMonths.forEach { m ->
                            val mTotal = (categoriesByMonth[m] ?: emptyList()).sumOf { it.amount }
                            DropdownMenuItem(text = { Text(text = "${trendMonthLabel(m)} \u2014 \u20B9${formatCompactAmount(mTotal)}", color = if (m == catMonth2) RonasColors.GrowthGreen else RonasColors.TextPrimary, fontSize = 13.sp) }, onClick = { onCatMonth2Change(m); menu2Expanded = false })
                        }
                    }
                }
            }
            // Table header
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Category", color = RonasColors.TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1.25f))
                Text(text = month1Label, color = RonasColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
                Text(text = month2Label, color = RonasColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f))
                Text(text = "Change", color = RonasColors.TextMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f))
            }
            androidx.compose.material3.HorizontalDivider(color = RonasColors.BorderSubtle, thickness = 1.dp)
            // Total row
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Total", color = RonasColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.25f))
                Text(text = "\u20B9${formatCompactAmount(total1)}", color = RonasColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f), maxLines = 1, softWrap = false)
                Text(text = "\u20B9${formatCompactAmount(total2)}", color = RonasColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f), maxLines = 1, softWrap = false)
                val diff = total1 - total2
                val pct = if (total2 > 0) diff / total2 * 100.0 else 0.0
                val higher = total1 > total2
                Text(text = if (total2 <= 0 && total1 > 0) "New" else (if (higher) "\u2191 ${"%.1f".format(java.util.Locale.US, pct)}%" else if (total1 < total2) "\u2193 ${"%.1f".format(java.util.Locale.US, kotlin.math.abs(pct))}%" else "0.0%"), color = if (higher) RonasColors.DeclineRed else if (total1 < total2) RonasColors.GrowthGreen else RonasColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.95f), maxLines = 1)
            }
            androidx.compose.material3.HorizontalDivider(color = RonasColors.BorderSubtle, thickness = 1.dp)
            if (sorted.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "No category spending data", style = MaterialTheme.typography.bodySmall, color = RonasColors.TextSecondary)
            } else {
                sorted.forEach { row ->
                    val share = row.curr / total1.coerceAtLeast(0.001) * 100.0
                    val deltaPct = if (row.prev > 0) (row.curr - row.prev) / row.prev * 100.0 else 0.0
                    val (deltaTop, deltaBot, deltaColor) = when {
                        row.prev <= 0.0 && row.curr > 0.0 -> Triple("New", "+\u20B9${formatCompactAmount(row.curr)}", RonasColors.DeclineRed)
                        row.curr > row.prev && row.prev > 0.0 -> Triple("\u2191 ${"%.1f".format(java.util.Locale.US, deltaPct)}%", "+\u20B9${formatCompactAmount(row.curr - row.prev)}", RonasColors.DeclineRed)
                        row.curr < row.prev && row.curr > 0.0 -> Triple("\u2193 ${"%.1f".format(java.util.Locale.US, kotlin.math.abs(deltaPct))}%", "-\u20B9${formatCompactAmount(row.prev - row.curr)}", RonasColors.GrowthGreen)
                        row.curr == 0.0 && row.prev > 0.0 -> Triple("\u2193 100.0%", "-\u20B9${formatCompactAmount(row.prev)}", RonasColors.GrowthGreen)
                        else -> Triple("0.0%", "No change", RonasColors.TextSecondary)
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1.25f)) {
                            Text(text = row.name, color = RonasColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = "${"%.1f".format(java.util.Locale.US, share)}% share", color = RonasColors.TextMuted, fontSize = 11.sp, maxLines = 1)
                        }
                        Text(text = "\u20B9${formatCompactAmount(row.curr)}", color = RonasColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f), maxLines = 1, softWrap = false)
                        Text(text = "\u20B9${formatCompactAmount(row.prev)}", color = RonasColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(0.85f), maxLines = 1, softWrap = false)
                        Column(modifier = Modifier.weight(0.95f), horizontalAlignment = Alignment.End) {
                            Text(text = deltaTop, color = deltaColor, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 1)
                            Text(text = deltaBot, color = deltaColor, fontSize = 11.sp, textAlign = TextAlign.End, maxLines = 1)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = RonasColors.BorderSubtle, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = RonasColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun TrendBarChart(trends: List<MonthTrend>) {
    val maxValue = (trends.maxOfOrNull { maxOf(it.income, it.expenses) } ?: 0.0).coerceAtLeast(1.0)
    val chartHeight = 140.dp
    Row(
        modifier = Modifier.fillMaxWidth().height(chartHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        trends.forEach { trend ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxHeight(0.85f),
                ) {
                    val incomeFraction = (trend.income / maxValue).toFloat().coerceIn(0f, 1f)
                    val expenseFraction = (trend.expenses / maxValue).toFloat().coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight(incomeFraction.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(RonasColors.TextPrimary),
                    )
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .fillMaxHeight(expenseFraction.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(RonasColors.DeclineRed),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trendMonthLabel(trend.month),
                    style = MaterialTheme.typography.labelSmall,
                    color = RonasColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CategoryBar(item: CategoryBreakdownItem) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "₹${formatAmount(item.amount)} · ${"%.1f".format(Locale.US, item.percentage)}%",
                style = MaterialTheme.typography.bodySmall,
                color = RonasColors.TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (item.percentage / 100.0).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal fun trendMonthLabel(month: String): String {
    val parts = month.split("-")
    return if (parts.size == 2) parts[1] + "/" + parts[0].takeLast(2) else month
}

private fun formatCompactAmount(amount: Double): String {
    val rounded = String.format(java.util.Locale.US, "%.0f", amount)
    return rounded.replace(THOUSANDS_REGEX, "$1,")
}

private val THOUSANDS_REGEX = Regex("(\\d)(?=(\\d{3})+(?!\\d))")
