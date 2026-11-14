package com.arthapilot.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RonasDotMatrixItem(
    val label: String,
    val fraction: Float,
    val color: Color,
)

@Composable
fun RonasCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = RonasColors.SurfaceCard,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
    ) {
        content()
    }
}

@Composable
fun RonasPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = RonasColors.SurfaceElevated,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = RonasColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun RonasBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = RonasColors.TextPrimary,
) {
    Surface(
        color = RonasColors.SurfaceElevated,
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
        modifier = modifier,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun RonasMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = RonasColors.TextPrimary,
    indicator: String? = null,
    indicatorColor: Color = RonasColors.GrowthGreen,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            color = RonasColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (indicator != null) {
            Text(
                text = indicator,
                color = indicatorColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun RonasCircularBadge(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RonasColors.SurfaceIcon)
            .border(1.dp, RonasColors.BorderSubtle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = RonasColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RonasTickStripBar(
    modifier: Modifier = Modifier,
    tickCount: Int = 40,
    primaryFraction: Float = 0.2f,
    secondaryFraction: Float = 0.75f,
) {
    val mutedFraction = (1f - primaryFraction - secondaryFraction).coerceAtLeast(0f)
    Canvas(modifier = modifier.height(24.dp).fillMaxWidth()) {
        val tickWidth = 2.dp.toPx()
        val spacing = 2.dp.toPx()
        val totalWidth = size.width
        val ticks = tickCount.coerceAtLeast(1)
        val step = (totalWidth + spacing) / ticks
        var cumulative = 0f
        for (i in 0 until ticks) {
            val fraction = (i + 0.5f) / ticks
            val color = when {
                fraction <= primaryFraction -> RonasColors.TextPrimary
                fraction <= primaryFraction + secondaryFraction -> RonasColors.TextSecondary
                else -> RonasColors.DividerMuted
            }
            val x = i * step
            drawRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(tickWidth, size.height),
            )
            cumulative += step
        }
    }
}

@Composable
fun RonasDotMatrixGrid(
    items: List<RonasDotMatrixItem>,
    modifier: Modifier = Modifier,
) {
    val source = if (items.isEmpty()) {
        listOf(RonasDotMatrixItem("Other", 1f, RonasColors.DividerMuted))
    } else {
        items
    }
    val total = source.sumOf { it.fraction.toDouble() }.toFloat().coerceAtLeast(0.001f)
    val legendItems = if (source.size <= 4) {
        source
    } else {
        source.take(3) + RonasDotMatrixItem(
            label = "Other",
            fraction = source.drop(3).sumOf { it.fraction.toDouble() }.toFloat(),
            color = RonasColors.DividerMuted,
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        legendItems.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = RonasColors.SurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(item.color, RoundedCornerShape(2.dp)),
                                )
                                Text(
                                    text = item.label,
                                    color = RonasColors.TextSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                text = "${(item.fraction / total * 100).toInt()}%",
                                color = RonasColors.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun RonasDualBarChart(
    months: List<String>,
    primaryValues: List<Float>,
    secondaryValues: List<Float>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    onBarSelected: ((Int) -> Unit)? = null,
) {
    val maxValue = ((primaryValues + secondaryValues).maxOrNull() ?: 1f).coerceAtLeast(1f)
    val chartHeight = 180.dp
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(chartHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            months.forEachIndexed { index, month ->
                val selected = index == selectedIndex
                val primary = primaryValues.getOrElse(index) { 0f }
                val secondary = secondaryValues.getOrElse(index) { 0f }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = onBarSelected != null) { onBarSelected?.invoke(index) }
                        .background(
                            if (selected) RonasColors.SurfaceElevated else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .border(
                            1.dp,
                            if (selected) RonasColors.GrowthGreen.copy(alpha = 0.45f) else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight * 0.85f),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight((primary / maxValue).coerceIn(0f, 1f).coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(RonasColors.TextPrimary),
                            )
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight((secondary / maxValue).coerceIn(0f, 1f).coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(RonasColors.DeclineRed),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = month,
                        color = if (selected) RonasColors.GrowthGreen else RonasColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

data class RonasCategoryRow(
    val label: String,
    val transactionType: String,
    val amount: Double,
)

enum class RonasCategorySort(val label: String) {
    Alphabetical("A–Z"),
    HighestIncome("Highest Income"),
    LowestIncome("Lowest Income"),
    HighestExpense("Highest Expense"),
    LowestExpense("Lowest Expense"),
}

@Composable
fun RonasCategoryTable(
    rows: List<RonasCategoryRow>,
    modifier: Modifier = Modifier,
) {
    var sortMode by rememberSaveable { mutableStateOf(RonasCategorySort.HighestExpense) }

    val incomeTotal = rows.filter { it.transactionType == "credit" }.sumOf { it.amount }.coerceAtLeast(0.001)
    val expenseTotal = rows.filter { it.transactionType == "debit" }.sumOf { it.amount }.coerceAtLeast(0.001)

    val sortedRows = remember(rows, sortMode) {
        when (sortMode) {
            RonasCategorySort.Alphabetical ->
                rows.sortedBy { it.label.lowercase() }
            RonasCategorySort.HighestIncome ->
                rows.sortedWith(
                    compareByDescending<RonasCategoryRow> { it.transactionType == "credit" }
                        .thenByDescending { if (it.transactionType == "credit") it.amount else 0.0 },
                )
            RonasCategorySort.LowestIncome ->
                rows.sortedWith(
                    compareByDescending<RonasCategoryRow> { it.transactionType == "credit" }
                        .thenBy { if (it.transactionType == "credit") it.amount else Double.MAX_VALUE },
                )
            RonasCategorySort.HighestExpense ->
                rows.sortedWith(
                    compareByDescending<RonasCategoryRow> { it.transactionType == "debit" }
                        .thenByDescending { if (it.transactionType == "debit") it.amount else 0.0 },
                )
            RonasCategorySort.LowestExpense ->
                rows.sortedWith(
                    compareByDescending<RonasCategoryRow> { it.transactionType == "debit" }
                        .thenBy { if (it.transactionType == "debit") it.amount else Double.MAX_VALUE },
                )
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Category breakdown",
                color = RonasColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                Surface(
                    onClick = { menuExpanded = true },
                    color = RonasColors.SurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "Sort categories",
                        tint = RonasColors.TextSecondary,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = RonasColors.SurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, RonasColors.BorderSubtle),
                ) {
                    RonasCategorySort.values().forEach { mode ->
                        val selected = mode == sortMode
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    color = if (selected) RonasColors.GrowthGreen else RonasColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                sortMode = mode
                                menuExpanded = false
                            },
                            trailingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Category",
                color = RonasColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Share %",
                color = RonasColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        androidx.compose.material3.HorizontalDivider(
            color = RonasColors.BorderSubtle,
            thickness = 1.dp,
        )

        sortedRows.forEach { row ->
            val isIncome = row.transactionType == "credit"
            val percentage = if (isIncome) {
                row.amount / incomeTotal * 100.0
            } else {
                row.amount / expenseTotal * 100.0
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    color = RonasColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${"%.1f".format(percentage)}%",
                    color = if (isIncome) RonasColors.GrowthGreen else RonasColors.DeclineRed,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            androidx.compose.material3.HorizontalDivider(
                color = RonasColors.BorderSubtle,
                thickness = 1.dp,
            )
        }
    }
}
