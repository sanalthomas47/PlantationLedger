package com.santhomach.plantationledger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.model.CropEfficiency
import com.santhomach.plantationledger.data.model.DEFAULT_CROP
import com.santhomach.plantationledger.data.model.ExpenseSource
import com.santhomach.plantationledger.data.model.IncomePeriod
import com.santhomach.plantationledger.data.model.IncomeSale
import com.santhomach.plantationledger.data.model.IncomeTypeSummary
import com.santhomach.plantationledger.ui.calculator.CalculatorState
import com.santhomach.plantationledger.ui.viewmodel.EfficiencyViewModel
import com.santhomach.plantationledger.ui.viewmodel.IncomeBreakdownViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// -------------------------------------------------------------------------------------------------
// Income Breakdown
// -------------------------------------------------------------------------------------------------

/** Income by type for this week, this year and all time; tap a type to see every sale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeBreakdownScreen(
    onNavigateBack: () -> Unit,
    viewModel: IncomeBreakdownViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { BackTopBar("Income Breakdown", onNavigateBack) }) { padding ->
        when {
            state.isLoading -> Loading(Modifier.padding(padding))
            state.error != null -> Message("Error: ${state.error}", Modifier.padding(padding))
            state.types.isEmpty() -> Message("No income recorded yet.", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") {
                    GridHeader("Income type")
                    HorizontalDivider(thickness = 1.5.dp)
                }
                items(state.types, key = { it.typeName }) { type ->
                    val open = type.typeName in state.expanded
                    IncomeTypeRow(type, open, onToggle = { viewModel.toggle(type.typeName) })
                    if (open) IncomeSales(type)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                item(key = "total") {
                    HorizontalDivider(thickness = 1.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            "TOTAL", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        listOf(
                            state.thisWeekTotal,
                            state.thisYearTotal,
                            state.allTimeTotal
                        ).forEach {
                            Text(
                                "₹${shortAmount(it)}",
                                modifier = Modifier.weight(1.3f),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridHeader(firstColumn: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            firstColumn, modifier = Modifier.weight(2.2f), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
        listOf("This Week", "This Year", "All Time").forEach {
            Text(
                it,
                modifier = Modifier.weight(1.3f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun IncomeTypeRow(type: IncomeTypeSummary, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (open) "Hide sales" else "Show sales",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            type.typeName,
            modifier = Modifier.weight(2f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        PeriodCell(type.thisWeek, Modifier.weight(1.3f))
        PeriodCell(type.thisYear, Modifier.weight(1.3f))
        PeriodCell(type.allTime, Modifier.weight(1.3f))
    }
}

@Composable
private fun PeriodCell(period: IncomePeriod, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = if (period.amount.signum() == 0) "—" else "₹${shortAmount(period.amount)}",
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = if (period.amount.signum() == 0) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall
        )
        if (period.weight > 0) {
            Text(
                text = "${weightText(period.weight)} kg",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun IncomeSales(type: IncomeTypeSummary) {
    val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(start = 36.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            "This year" to type.thisYear,
            "All time" to type.allTime
        ).forEach { (label, period) ->
            period.pricePerKg?.let { price ->
                Text(
                    text = "$label: ${weightText(period.weight)} kg, average ₹${fullAmount(price)}/kg",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            SaleCell("Date", Modifier.weight(1.4f), header = true, alignEnd = false)
            SaleCell("kg", Modifier.weight(0.9f), header = true)
            SaleCell("Amount", Modifier.weight(1.2f), header = true)
            SaleCell("₹/kg", Modifier.weight(1f), header = true)
        }
        type.sales.forEach { sale: IncomeSale ->
            val date = runCatching {
                LocalDate.parse(sale.date).format(dateFormat)
            }.getOrDefault(sale.date)
            val price = if (sale.weight > 0) sale.amount.divide(
                BigDecimal.valueOf(sale.weight),
                0,
                RoundingMode.HALF_UP
            ) else null
            Row {
                SaleCell(date, Modifier.weight(1.4f), alignEnd = false)
                SaleCell(
                    if (sale.weight > 0) weightText(sale.weight) else "—",
                    Modifier.weight(0.9f)
                )
                SaleCell("₹${fullAmount(sale.amount)}", Modifier.weight(1.2f))
                SaleCell(price?.let { "₹${fullAmount(it)}" } ?: "—", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SaleCell(
    text: String,
    modifier: Modifier,
    header: Boolean = false,
    alignEnd: Boolean = true
) {
    Text(
        text = text,
        modifier = modifier,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall
    )
}

// -------------------------------------------------------------------------------------------------
// Income vs Expense
// -------------------------------------------------------------------------------------------------

/** Each crop's income against the expenses that belong to it, for this year or all time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EfficiencyScreen(
    onNavigateBack: () -> Unit,
    viewModel: EfficiencyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { BackTopBar("Income vs Expense", onNavigateBack) }) { padding ->
        when {
            state.isLoading -> Loading(Modifier.padding(padding))
            state.error != null -> Message("Error: ${state.error}", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "period") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            false to "This year",
                            true to "All time"
                        ).forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = state.allTime == value,
                                onClick = { viewModel.setAllTime(value) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                            ) { Text(label) }
                        }
                    }
                }
                if (state.report.crops.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Nothing recorded for this period.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                items(state.report.crops, key = { it.crop }) { crop ->
                    CropCard(
                        crop,
                        open = crop.crop in state.expanded,
                        onToggle = { viewModel.toggle(crop.crop) })
                }
                item(key = "notes") {
                    Text(
                        text = "Expenses are matched to a crop when the expense type, subtype or labour task " +
                                "names it (e.g. \"Pepper Drying\"). Anything naming no crop is counted under " +
                                "$DEFAULT_CROP. Pesticide, fertilizer and foliar bills are included. Capital " +
                                "expenses are left out" +
                                (if (state.report.excludedCapital.signum() > 0)
                                    " (₹${fullAmount(state.report.excludedCapital)} in this period)." else "."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun CropCard(crop: CropEfficiency, open: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        crop.crop,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (crop.incomeTypes.isNotEmpty()) {
                        Text(
                            crop.incomeTypes.joinToString(", "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Icon(
                    imageVector = if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (open) "Hide matched expenses" else "Show matched expenses",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Figure("Income", crop.income, Modifier.weight(1f))
                Figure("Expense", crop.expense, Modifier.weight(1f))
                Figure("Net", crop.net, Modifier.weight(1f), emphasise = true)
            }
            val returnPerRupee = crop.returnPerRupee
            Text(
                text = when {
                    returnPerRupee != null -> "Returns ₹${returnPerRupee.toPlainString()} for every ₹1 spent"
                    crop.income.signum() > 0 -> "No expenses matched to this crop"
                    else -> "No income in this period"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (open) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                if (crop.sources.isEmpty()) {
                    Text(
                        "No expenses.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val (matched, assigned) = crop.sources.partition { it.matched }
                SourceGroup("Matched by name", matched)
                SourceGroup("No crop named, counted here", assigned)
            }
        }
    }
}

@Composable
private fun SourceGroup(title: String, sources: List<ExpenseSource>) {
    if (sources.isEmpty()) return
    Text(
        title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium
    )
    sources.forEach { source ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (source.isLabour) "Labour · " else "") + source.label,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall
            )
            Text("₹${fullAmount(source.amount)}", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun Figure(
    label: String,
    amount: BigDecimal,
    modifier: Modifier,
    emphasise: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = (if (amount.signum() < 0) "−" else "") + "₹${shortAmount(amount.abs())}",
            fontWeight = if (emphasise) FontWeight.Bold else FontWeight.SemiBold,
            color = when {
                !emphasise -> MaterialTheme.colorScheme.onSurface
                amount.signum() < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.titleSmall
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Shared bits
// -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackTopBar(title: String, onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            modifier = Modifier.padding(32.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 1.2L / 45.6K style shortening for grid cells, as on the Expense Breakdown page. */
private fun shortAmount(amount: BigDecimal): String {
    val value = amount.toLong()
    return when {
        value >= 100_000 -> String.format(Locale.ENGLISH, "%.1fL", value / 100_000.0)
        value >= 1_000 -> String.format(Locale.ENGLISH, "%.1fK", value / 1_000.0)
        else -> amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
    }
}

/** Whole rupees with Indian digit grouping, e.g. 1,23,456. */
private fun fullAmount(amount: BigDecimal): String =
    CalculatorState.formatNumber(amount.setScale(0, RoundingMode.HALF_UP).toPlainString())

private fun weightText(weight: Double): String =
    if (weight % 1.0 == 0.0) String.format(Locale.ENGLISH, "%,.0f", weight)
    else String.format(Locale.ENGLISH, "%,.1f", weight)
