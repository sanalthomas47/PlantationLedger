package com.santhomach.plantationledger.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.ui.viewmodel.ExpenseTypeRow
import com.santhomach.plantationledger.ui.viewmodel.ExpenseTypeSummaryViewModel
import com.santhomach.plantationledger.ui.viewmodel.SubtypeRow
import com.santhomach.plantationledger.ui.viewmodel.TrendDirection
import java.math.BigDecimal

/**
 * Table of "other expenses" grouped by type and subtype for this week / this year / all time.
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
                        text = "No expense type data found.\nAdd some other expenses to see the breakdown.",
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
