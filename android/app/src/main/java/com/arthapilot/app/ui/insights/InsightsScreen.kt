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
    LaunchedEffect(uiState.trends) {
        if (selectedMonth == null || uiState.trends.none { it.month == selectedMonth }) {
            selectedMonth = uiState.trends.lastOrNull { it.income > 0.0 || it.expenses > 0.0 }?.month
                ?: uiState.trends.lastOrNull()?.month
        }
    }

    val latestActiveMonth = remember(uiState.trends) {
        uiState.trends.lastOrNull { it.income > 0.0 || it.expenses > 0.0 }?.month
            ?: uiState.trends.lastOrNull()?.month
    }

    var sortMode by remember { mutableStateOf(CategorySort.HighestSpend) }
    var filterExpanded by remember { mutableStateOf(false) }

    val selectedTrend = uiState.trends.firstOrNull { it.month == selectedMonth }
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
                                    Spacer(modifier = Modifier.height(16.dp))
                                    RonasDualBarChart(
                                        months = uiState.trends.map { trendMonthLabel(it.month) },
                                        primaryValues = uiState.trends.map { it.income.toFloat() },
                                        secondaryValues = uiState.trends.map { it.expenses.toFloat() },
                                        selectedIndex = selectedIndex,
                                        onBarSelected = { idx ->
                                            uiState.trends.getOrNull(idx)?.month?.let { selectedMonth = it }
                                        },
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        uiState.trends.forEach { trend ->
                                            val sel = trend.month == selectedMonth
                                            Surface(
                                                onClick = { selectedMonth = trend.month },
                                                color = if (sel) RonasColors.SurfaceElevated else RonasColors.BgCanvas,
                                                shape = RoundedCornerShape(20.dp),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (sel) RonasColors.GrowthGreen else RonasColors.BorderSubtle,
                                                ),
                                            ) {
                                                Text(
                                                    text = trendMonthLabel(trend.month),
                                                    color = if (sel) RonasColors.GrowthGreen else RonasColors.TextSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                )
                                            }
                                        }
                                    }
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
                                prevMonthRaw = prevMonthRaw,
                                prevTrend = prevTrend,
                                latestActiveMonth = latestActiveMonth,
                                onJump = { latestActiveMonth?.let { selectedMonth = it } },
                            )
                        }
                    }
                    if (uiState.breakdown.isNotEmpty()) {
                        item {
                            RonasCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Spending by category",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = RonasColors.TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    uiState.breakdown.take(6).forEach { item ->
                                        CategoryBar(item = item)
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (selectedMonth != null) {
                        item {
                            CategoryMoMCard(
                                selectedMonth = selectedMonth!!,
                                currItems = uiState.categoriesByMonth[selectedMonth] ?: emptyList(),
                                prevItems = uiState.categoriesByMonth[prevMonthRaw] ?: emptyList(),
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
    prevMonthRaw: String,
    prevTrend: MonthTrend?,
    latestActiveMonth: String?,
    onJump: () -> Unit,
) {
    if (selectedMonth == null) return
    val monthLabel = trendMonthLabel(selectedMonth)
    val prevLabel = trendMonthLabel(prevMonthRaw)
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$monthLabel Performance • vs $prevLabel",
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
                val prevExpenses = prevTrend?.expenses ?: 0.0
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
    selectedMonth: String,
    currItems: List<CategoryBreakdownItem>,
    prevItems: List<CategoryBreakdownItem>,
    sortMode: CategorySort,
    onSortChange: (CategorySort) -> Unit,
    filterExpanded: Boolean,
    onFilterExpandedChange: (Boolean) -> Unit,
) {
    val monthLabel = trendMonthLabel(selectedMonth)
    val prevLabel = trendMonthLabel(previousMonth(selectedMonth))
    val currMap = currItems.associate { it.category to it.amount }
    val prevMap = prevItems.associate { it.category to it.amount }
    val names = (currMap.keys + prevMap.keys).distinct()
    val rows = names.map { CatMoMRow(it, currMap[it] ?: 0.0, prevMap[it] ?: 0.0) }
    val totalCurr = rows.sumOf { it.curr }.coerceAtLeast(0.001)
    val sorted = when (sortMode) {
        CategorySort.HighestSpend -> rows.sortedByDescending { it.curr }
        CategorySort.LowestSpend -> rows.sortedBy { it.curr }
        CategorySort.BiggestIncrease -> rows.sortedByDescending { it.curr - it.prev }
        CategorySort.BiggestDrop -> rows.sortedBy { it.curr - it.prev }
        CategorySort.AZ -> rows.sortedBy { it.name.lowercase() }
    }

    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$monthLabel Category Spending",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = RonasColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "vs $prevLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = RonasColors.TextSecondary,
                    )
                }
                Box {
                    Surface(
                        onClick = { onFilterExpandedChange(true) },
                        color = RonasColors.SurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Sort categories",
                            tint = RonasColors.TextSecondary,
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { onFilterExpandedChange(false) },
                        containerColor = RonasColors.SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
                    ) {
                        CategorySort.values().forEach { mode ->
                            val selected = mode == sortMode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.label,
                                        color = if (selected) RonasColors.GrowthGreen else RonasColors.TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                },
                                onClick = {
                                    onSortChange(mode)
                                    onFilterExpandedChange(false)
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = RonasColors.GrowthGreen,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Category",
                    color = RonasColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = "Spend",
                    color = RonasColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "vs Prev Month",
                    color = RonasColors.TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.4f),
                )
            }
            androidx.compose.material3.HorizontalDivider(color = RonasColors.BorderSubtle, thickness = 1.dp)
            if (sorted.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No category spending data",
                    style = MaterialTheme.typography.bodySmall,
                    color = RonasColors.TextSecondary,
                )
            } else {
                sorted.forEach { row ->
                    val share = row.curr / totalCurr * 100.0
                    val deltaPct = if (row.prev > 0) (row.curr - row.prev) / row.prev * 100.0 else 0.0
                    val (deltaText, deltaColor) = when {
                        row.prev <= 0.0 && row.curr > 0.0 ->
                            "New (+₹${formatAmount(row.curr)})" to RonasColors.DeclineRed
                        row.curr > row.prev && row.prev > 0.0 ->
                            "↑ ${"%.1f".format(Locale.US, deltaPct)}% (+₹${formatAmount(row.curr - row.prev)})" to RonasColors.DeclineRed
                        row.curr < row.prev ->
                            "↓ ${"%.1f".format(Locale.US, kotlin.math.abs(deltaPct))}% (-₹${formatAmount(row.prev - row.curr)})" to RonasColors.GrowthGreen
                        else ->
                            "0.0% (No change)" to RonasColors.TextSecondary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                text = row.name,
                                color = RonasColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${"%.1f".format(Locale.US, share)}%",
                                color = RonasColors.TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            text = "₹${formatAmount(row.curr)}",
                            color = RonasColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = deltaText,
                            color = deltaColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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