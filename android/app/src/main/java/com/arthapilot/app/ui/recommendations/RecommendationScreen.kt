package com.arthapilot.app.ui.recommendations

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.arthapilot.app.ui.theme.RonasColors
import com.arthapilot.app.ui.theme.RonasCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthapilot.app.domain.model.AllocationItem
import com.arthapilot.app.domain.model.MarketQuote
import com.arthapilot.app.domain.model.Recommendation
import com.arthapilot.app.domain.model.WhatIfResult
import com.arthapilot.app.ui.home.formatAmount
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    onBack: () -> Unit,
    viewModel: RecommendationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorDismissed()
        }
    }
    LaunchedEffect(uiState.whatIfError) {
        uiState.whatIfError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onWhatIfErrorDismissed()
        }
    }

    Scaffold(
        containerColor = RonasColors.BgCanvas,
        topBar = {
            TopAppBar(
                title = { Text("Investment Suggestions", color = RonasColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            uiState.isLoading && uiState.recommendation == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.recommendation == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No suggestion available", style = MaterialTheme.typography.titleMedium)
                }
            }

            else -> {
                val recommendation = checkNotNull(uiState.recommendation)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    if (recommendation.status == "insufficient") {
                        item { InsufficientCard(recommendation) }
                    } else {
                        item { SummaryCard(recommendation) }
                        item { AllocationCard(recommendation.allocation) }
                        item { MarketCard(recommendation.market) }
                    }
                    if (recommendation.notes.isNotEmpty()) {
                        item {
                            Text(
                                text = "What shapes this suggestion",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(recommendation.notes, key = { it.text }) { note ->
                            NoteCard(kind = note.kind, text = note.text)
                        }
                    }
                    item {
                        Text(
                            text = recommendation.educationalDisclaimer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                    item { WhatIfSection(uiState = uiState, viewModel = viewModel) }
                }
            }
        }
    }
}

@Composable
private fun InsufficientCard(recommendation: Recommendation) {
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Suggestions need more data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recommendation.insufficientReason ?: "Not enough data yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Current risk appetite: ${recommendation.riskBand} (${recommendation.riskScore}/100)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(recommendation: Recommendation) {
    val context = recommendation.context ?: return
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Your suggested mix",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = recommendation.allocationSummary ?: "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SummaryRow("Risk appetite", "${recommendation.riskBand} (${recommendation.riskScore}/100)")
            SummaryRow("Monthly income", formatAmount(context.monthlyIncome))
            SummaryRow("Monthly expenses", formatAmount(context.monthlyExpenses))
            SummaryRow("Monthly investable surplus", formatAmount(context.investableMonthly))
            context.savingsRate?.let {
                SummaryRow("Savings rate", String.format(Locale.US, "%.1f%%", it * 100))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AllocationCard(allocation: List<AllocationItem>) {
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Allocation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            allocation.forEach { item ->
                AllocationRow(item = item)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun AllocationRow(item: AllocationItem) {
    val color = when (item.label.lowercase()) {
        "equity" -> RonasColors.TextPrimary
        "debt" -> RonasColors.TextSecondary
        "gold" -> RonasColors.AccentAmber
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${item.percentage}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { item.percentage / 100f },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MarketCard(quotes: List<MarketQuote>) {
    if (quotes.isEmpty()) return
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = "Market reference (daily refresh)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            quotes.forEach { quote ->
                val statusText = when (quote.status) {
                    "live" -> "today"
                    "cached" -> "last known"
                    else -> "unavailable"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = quote.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (quote.status == "unavailable") {
                            statusText
                        } else {
                            val change = quote.changePct?.let { " (${String.format(Locale.US, "%+.1f%%", it)})" } ?: ""
                            "${formatAmount(quote.price)}$change · $statusText"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(kind: String, text: String) {
    val color = if (kind == "caution") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, RoundedCornerShape(5.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WhatIfSection(uiState: RecommendationUiState, viewModel: RecommendationViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "What-if simulator",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Pick a category and a monthly reduction to see how savings would change. Simulation only - nothing is modified.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        WhatIfControls(uiState = uiState, viewModel = viewModel)
        uiState.whatIfResult?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            WhatIfResultCard(result)
        }
    }
}

@Composable
private fun WhatIfControls(uiState: RecommendationUiState, viewModel: RecommendationViewModel) {
    var selectedCategory by remember(uiState.categories) { mutableStateOf(uiState.categories.firstOrNull() ?: "") }
    var reduction by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { Text("▾") },
                    modifier = Modifier.fillMaxWidth().clickable(enabled = uiState.categories.isNotEmpty()) { expanded = true },
                    enabled = uiState.categories.isNotEmpty(),
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    uiState.categories.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate) },
                            onClick = {
                                selectedCategory = candidate
                                expanded = false
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = reduction,
                onValueChange = { reduction = it },
                label = { Text("Monthly reduction (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val amount = reduction.toDoubleOrNull() ?: 0.0
                    if (selectedCategory.isNotBlank() && amount > 0) {
                        viewModel.simulate(selectedCategory, amount)
                    }
                },
                enabled = !uiState.whatIfLoading && selectedCategory.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.whatIfLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulating…")
                } else {
                    Text("Simulate")
                }
            }
        }
    }
}

@Composable
private fun WhatIfResultCard(result: WhatIfResult) {
    val currentRate = result.original.savingsRate ?: 0.0
    val newRate = result.simulated.savingsRate ?: 0.0
    RonasCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Result - ${result.category}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { newRate.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Savings rate ${String.format(Locale.US, "%.1f%%", currentRate * 100)} → ${String.format(Locale.US, "%.1f%%", newRate * 100)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(
                "Monthly expenses",
                "${formatAmount(result.original.monthlyExpenses)} → ${formatAmount(result.simulated.monthlyExpenses)}",
            )
            SummaryRow(
                "Monthly savings",
                "${formatAmount(result.original.monthlySavings)} → ${formatAmount(result.simulated.monthlySavings)}",
            )
            SummaryRow("Extra investable / year", formatAmount(result.impact.annualisedExtraInvestable))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.narrative,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.educationalDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}