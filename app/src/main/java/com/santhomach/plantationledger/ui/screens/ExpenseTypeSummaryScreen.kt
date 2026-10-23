package com.santhomach.plantationledger.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.ui.calculator.CalculatorState
import com.santhomach.plantationledger.ui.viewmodel.ExpenseTypeRow
import com.santhomach.plantationledger.ui.viewmodel.ExpenseTypeSummaryUiState
import com.santhomach.plantationledger.ui.viewmodel.ExpenseTypeSummaryViewModel
import com.santhomach.plantationledger.ui.viewmodel.SubtypeRow
import com.santhomach.plantationledger.ui.viewmodel.TaskLabourRow
import com.santhomach.plantationledger.ui.viewmodel.TaskRow
import com.santhomach.plantationledger.ui.viewmodel.TrendDirection
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Table of all expenses (labour by labour type, and other expenses) grouped by type and subtype for
 * this week / this year / all time, followed by labour cost per work task (this year / all time)
 * and bar charts of both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTypeSummaryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ExpenseTypeSummaryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Breakdown") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            uiState.rows.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No expenses found.\nAdd a day's labour or expenses to see the breakdown.",
                        modifier = Modifier.padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item(key = "header") { ColumnHeader() }
                    horizontalDividerItem()

                    items(uiState.rows, key = { it.typeName }) { row ->
                        TypeRow(row = row, onToggle = { viewModel.toggleExpanded(row.typeName) })
                        AnimatedVisibility(
                            visible = row.isExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column {
                                row.subtypes.forEach { subtype ->
                                    SubtypeRowItem(key = "${row.typeName}-${subtype.subtypeName}", subtype = subtype)
                                }
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    item(key = "footer") {
                        HorizontalDivider(thickness = 1.5.dp)
                        TotalRow(
                            thisWeekTotal = uiState.thisWeekTotal,
                            thisYearTotal = uiState.thisYearTotal,
                            allTimeTotal = uiState.allTimeTotal
                        )
                    }

                    // ---- Labour by work task ----
                    if (uiState.taskRows.isNotEmpty()) {
                        item(key = "task_title") {
                            Text(
                                text = "Labour by Task",
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 2.dp),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Wages and overtime for each task. Tap a task to see the labour types.",
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        item(key = "task_header") {
                            TaskColumnHeader()
                            HorizontalDivider(thickness = 1.5.dp)
                        }
                        items(uiState.taskRows, key = { "task_${it.taskName}" }) { row ->
                            TaskRowItem(row = row, onToggle = { viewModel.toggleTaskExpanded(row.taskName) })
                            AnimatedVisibility(
                                visible = row.isExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column {
                                    row.labourTypes.forEach { type -> TaskLabourRowItem(type) }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        item(key = "task_footer") {
                            HorizontalDivider(thickness = 1.5.dp)
                            TaskTotalRow(uiState.taskThisYearTotal, uiState.taskAllTimeTotal)
                        }
                    }

                    // ---- Charts ----
                    item(key = "charts") {
                        BreakdownCharts(uiState)
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Expense Type",
            modifier = Modifier.weight(2.2f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        HeaderCell("This Week", Modifier.weight(1.3f))
        HeaderCell("This Year", Modifier.weight(1.3f))
        HeaderCell("All Time", Modifier.weight(1.3f))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
fun TypeRow(row: ExpenseTypeRow, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.subtypes.isNotEmpty()) {
            Icon(
                imageVector = if (row.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (row.isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
        Text(
            text = row.typeName,
            modifier = Modifier.weight(2f),
            fontWeight = FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium
        )
        AmountCell(row.thisWeekAmount, row.thisWeekPercent, row.weekTrend, modifier = Modifier.weight(1.3f))
        AmountCell(row.thisYearAmount, row.thisYearPercent, row.yearTrend, modifier = Modifier.weight(1.3f))
        AmountCell(row.allTimeAmount, row.allTimePercent, TrendDirection.NEUTRAL, showTrend = false, modifier = Modifier.weight(1.3f))
    }
}

@Composable
fun SubtypeRowItem(key: String, subtype: SubtypeRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 36.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val name = subtype.subtypeName.ifBlank { "Unspecified" }
        Text(
            text = "└ $name",
            modifier = Modifier.weight(2f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall
        )
        SimpleAmountCell(subtype.thisWeekAmount, Modifier.weight(1.3f))
        SimpleAmountCell(subtype.thisYearAmount, Modifier.weight(1.3f))
        SimpleAmountCell(subtype.allTimeAmount, Modifier.weight(1.3f))
    }
}

@Composable
private fun TotalRow(thisWeekTotal: BigDecimal, thisYearTotal: BigDecimal, allTimeTotal: BigDecimal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = "TOTAL",
            modifier = Modifier.weight(2f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        TotalCell(thisWeekTotal, Modifier.weight(1.3f))
        TotalCell(thisYearTotal, Modifier.weight(1.3f))
        TotalCell(allTimeTotal, Modifier.weight(1.3f))
    }
}

@Composable
private fun AmountCell(
    amount: BigDecimal,
    percent: Float,
    trend: TrendDirection,
    showTrend: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showTrend && trend != TrendDirection.NEUTRAL) {
                Icon(
                    imageVector = if (trend == TrendDirection.UP) Icons.AutoMirrored.Filled.TrendingUp
                    else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (trend == TrendDirection.UP) Color(0xFFD32F2F) else Color(0xFF388E3C)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Text(
                text = "₹${formatAmount(amount)}",
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (percent > 0f) {
            Text(
                text = String.format("%.1f", percent) + "%",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SimpleAmountCell(amount: BigDecimal, modifier: Modifier) {
    Text(
        text = if (amount == BigDecimal.ZERO) "—" else "₹${formatAmount(amount)}",
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun TotalCell(amount: BigDecimal, modifier: Modifier) {
    Text(
        text = "₹${formatAmount(amount)}",
        modifier = modifier,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun TaskColumnHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Task",
            modifier = Modifier.weight(2.2f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        HeaderCell("This Year", Modifier.weight(1.3f))
        HeaderCell("All Time", Modifier.weight(1.3f))
    }
}

@Composable
private fun TaskRowItem(row: TaskRow, onToggle: () -> Unit) {
    val expandable = row.labourTypes.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (expandable) {
            Icon(
                imageVector = if (row.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (row.isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
        Text(
            text = row.taskName,
            modifier = Modifier.weight(2f),
            fontWeight = FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium
        )
        AmountCell(row.thisYearAmount, row.thisYearPercent, TrendDirection.NEUTRAL, showTrend = false, modifier = Modifier.weight(1.3f))
        AmountCell(row.allTimeAmount, row.allTimePercent, TrendDirection.NEUTRAL, showTrend = false, modifier = Modifier.weight(1.3f))
    }
}

@Composable
private fun TaskLabourRowItem(type: TaskLabourRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 36.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "└ ${type.labourType}",
            modifier = Modifier.weight(2f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            style = MaterialTheme.typography.bodySmall
        )
        SimpleAmountCell(type.thisYearAmount, Modifier.weight(1.3f))
        SimpleAmountCell(type.allTimeAmount, Modifier.weight(1.3f))
    }
}

@Composable
private fun TaskTotalRow(thisYearTotal: BigDecimal, allTimeTotal: BigDecimal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = "TOTAL LABOUR",
            modifier = Modifier.weight(2f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        TotalCell(thisYearTotal, Modifier.weight(1.3f))
        TotalCell(allTimeTotal, Modifier.weight(1.3f))
    }
}

/** One bar of a breakdown chart. */
private data class BarDatum(val label: String, val amount: BigDecimal, val percent: Float)

/**
 * Bar charts of expenses by type and labour by task, with a This year / All time switch.
 * One colour, largest first; the tables above hold the exact figures.
 */
@Composable
private fun BreakdownCharts(uiState: ExpenseTypeSummaryUiState) {
    var allTime by rememberSaveable { mutableStateOf(false) }

    val typeBars = uiState.rows
        .map {
            if (allTime) BarDatum(it.typeName, it.allTimeAmount, it.allTimePercent)
            else BarDatum(it.typeName, it.thisYearAmount, it.thisYearPercent)
        }
        .filter { it.amount.signum() > 0 }
        .sortedByDescending { it.amount }
    val taskBars = uiState.taskRows
        .map {
            if (allTime) BarDatum(it.taskName, it.allTimeAmount, it.allTimePercent)
            else BarDatum(it.taskName, it.thisYearAmount, it.thisYearPercent)
        }
        .filter { it.amount.signum() > 0 }
        .sortedByDescending { it.amount }

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Charts",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(false to "This year", true to "All time").forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = allTime == value,
                    onClick = { allTime = value },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                ) { Text(label) }
            }
        }
        BarChartCard(
            title = "Expenses by type",
            subtitle = if (allTime) "All time" else "This year",
            bars = typeBars
        )
        BarChartCard(
            title = "Labour by task",
            subtitle = (if (allTime) "All time" else "This year") + " \u00b7 wages and overtime",
            bars = taskBars
        )
    }
}

@Composable
private fun BarChartCard(title: String, subtitle: String, bars: List<BarDatum>) {
    // Bar colour checked for contrast and lightness against the light and dark card surfaces.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barColor = if (dark) Color(0xFF2E9E67) else Color(0xFF1D7A4D)
    var selected by remember(bars) { mutableStateOf<String?>(null) }
    val max = bars.maxOfOrNull { it.amount } ?: BigDecimal.ZERO

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "$subtitle. Tap a bar for the exact amount.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (bars.isEmpty() || max.signum() == 0) {
                Text(
                    text = "Nothing recorded for this period.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            bars.forEach { bar ->
                BarRow(
                    bar = bar,
                    fraction = bar.amount.divide(max, 4, RoundingMode.HALF_UP).toFloat(),
                    color = barColor,
                    selected = selected == bar.label,
                    onClick = { selected = if (selected == bar.label) null else bar.label }
                )
            }
        }
    }
}

@Composable
private fun BarRow(bar: BarDatum, fraction: Float, color: Color, selected: Boolean, onClick: () -> Unit) {
    val percentText = String.format(Locale.ENGLISH, "%.0f%%", bar.percent)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${bar.label}, \u20b9${fullAmount(bar.amount)}, $percentText"
            }
            .padding(vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bar.label,
                modifier = Modifier.width(104.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.width(8.dp))
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // Leave room after the bar for its value label.
                val track = (maxWidth - 88.dp).coerceAtLeast(0.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width((track * fraction).coerceAtLeast(2.dp))
                            .height(14.dp)
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "\u20b9${formatAmount(bar.amount)} \u00b7 $percentText",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (selected) {
            Text(
                text = "${bar.label}: \u20b9${fullAmount(bar.amount)} \u00b7 " +
                    String.format(Locale.ENGLISH, "%.1f%%", bar.percent) + " of the total",
                modifier = Modifier.padding(start = 112.dp, top = 2.dp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Whole rupees with Indian digit grouping, e.g. 9,90,925. */
private fun fullAmount(amount: BigDecimal): String =
    CalculatorState.formatNumber(amount.setScale(0, RoundingMode.HALF_UP).toPlainString())

private fun LazyListScope.horizontalDividerItem() {
    item(key = "divider_header") {
        HorizontalDivider(thickness = 1.5.dp)
    }
}

/** 1.2L / 45.6K style shortening for table cells. */
private fun formatAmount(amount: BigDecimal): String {
    if (amount == BigDecimal.ZERO) return "0"
    val value = amount.toLong()
    return when {
        value >= 100_000 -> String.format("%.1f", value / 100_000.0) + "L"
        value >= 1_000 -> String.format("%.1f", value / 1_000.0) + "K"
        else -> amount.stripTrailingZeros().toPlainString()
    }
}
