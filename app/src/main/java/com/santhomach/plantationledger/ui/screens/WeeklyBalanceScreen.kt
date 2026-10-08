package com.santhomach.plantationledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.model.WeekBalance
import com.santhomach.plantationledger.ui.viewmodel.WeeklyBalanceViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")
private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE d MMM")

private fun rupees(amount: BigDecimal): String =
    "₹" + amount.abs().stripTrailingZeros().toPlainString()

/**
 * Every week's payments against its costs (pesticide / fertilizer bills excluded) with the
 * running balance, newest first. Weeks with no payment or edits after settlement are flagged.
 * Tapping a week opens Reports for that week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyBalanceScreen(
    onNavigateBack: () -> Unit,
    onOpenWeek: (LocalDate, LocalDate) -> Unit,
    viewModel: WeeklyBalanceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Balance") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Balance with the manager", style = MaterialTheme.typography.titleMedium)
                        BalanceLine("Brought forward into this week", state.broughtForward)
                        BalanceLine("Balance now (incl. this week)", state.currentBalance, bold = true)
                        Text(
                            text = "Payments (advances + settlements) minus labour, overtime and other expenses. " +
                                "Pesticide/fertilizer bills are excluded (tracked in Vendor Ledger).",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.onlyUnbalanced,
                        onClick = { viewModel.setOnlyUnbalanced(!state.onlyUnbalanced) },
                        label = { Text("Only weeks that don't balance (${state.unbalancedCount})") }
                    )
                }
            }

            if (state.weeks.isEmpty()) {
                item {
                    Text(
                        text = if (state.onlyUnbalanced) "Every completed week balances." else "No expenses recorded yet.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(state.weeks, key = { it.weekStart.toString() }) { week ->
                WeekCard(week = week, onClick = { onOpenWeek(week.weekStart, week.weekEnd) })
            }
        }
    }
}

@Composable
private fun WeekCard(week: WeekBalance, onClick: () -> Unit) {
    val flagged = week.noPayment || (week.result.signum() != 0 && week.editedAfterSettlement.isNotEmpty())
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (flagged) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${week.weekStart.format(dayMonth)} – ${week.weekEnd.format(dayMonthYear)}",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (week.isCurrentWeek) Tag("This week", MaterialTheme.colorScheme.primary)
            }

            AmountLine("Costs (${week.days} days)", rupees(week.costs))
            AmountLine(
                label = week.settlementDate?.let { "Paid (settled ${it.format(weekdayDayMonth)})" } ?: "Paid",
                value = rupees(week.payments)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            BalanceLine(
                label = if (week.isCurrentWeek && week.settlementDate == null) "Week so far (not settled)" else "Week result",
                amount = week.result
            )
            BalanceLine("Balance after this week", week.runningTotal, bold = true)

            if (week.noPayment) {
                Warning("No payment recorded for this week")
            }
            if (week.result.signum() != 0 && week.editedAfterSettlement.isNotEmpty()) {
                Warning(
                    "Changed after settlement: " +
                        week.editedAfterSettlement.joinToString { it.format(weekdayDayMonth) }
                )
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Positive = Excess (green), negative = Short (red), zero = Balanced. */
@Composable
private fun BalanceLine(label: String, amount: BigDecimal, bold: Boolean = false) {
    val (suffix, color) = when (amount.signum()) {
        1 -> "Excess " to MaterialTheme.colorScheme.primary
        -1 -> "Short " to MaterialTheme.colorScheme.error
        else -> "Balanced " to MaterialTheme.colorScheme.onSurface
    }
    val style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = style)
        Text(
            text = if (amount.signum() == 0) "Balanced" else suffix + rupees(amount),
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            style = style
        )
    }
}

@Composable
private fun Warning(text: String) {
    Text(
        text = "⚠ $text",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
