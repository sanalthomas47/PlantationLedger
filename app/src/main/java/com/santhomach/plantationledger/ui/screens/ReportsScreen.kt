package com.santhomach.plantationledger.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.ui.viewmodel.DateRange
import com.santhomach.plantationledger.ui.viewmodel.ReportsViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reports & analytics for a selectable date range: daily / weekly summaries, weekly specifics
 * (labor, other expenses, overtime, advances, settlement), grouped labor summary, top expense
 * categories, income breakdown and the recent expense list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToExpenseEntry: (LocalDate, Int?) -> Unit = { _, _ -> },
    viewModelArg: ReportsViewModel? = null
) {
    if (LocalInspectionMode.current && viewModelArg == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Reports Screen Preview")
        }
        return
    }
    val viewModel: ReportsViewModel = viewModelArg ?: hiltViewModel()

    // Apply a date range passed in through navigation (e.g. from Weekly Funds "View Summary").
    LaunchedEffect(startDate, endDate) {
        if (startDate != null && endDate != null) {
            viewModel.setDateRange(DateRange.Custom(startDate, endDate))
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val dailySummary by viewModel.dailySummary.collectAsState()
    val weeklySummary by viewModel.weeklySummary.collectAsState()
    val filteredExpenses by viewModel.filteredExpenses.collectAsState()
    val periodBroughtForward by viewModel.periodBroughtForward.collectAsState()

    var showDateRangePicker by remember { mutableStateOf(false) }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        val end = dateRangePickerState.selectedEndDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        if (start != null && end != null) {
                            viewModel.setDateRange(DateRange.Custom(start, end))
                            showDateRangePicker = false
                        }
                    },
                    enabled = dateRangePickerState.selectedStartDateMillis != null &&
                        dateRangePickerState.selectedEndDateMillis != null
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp),
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp)) }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Reserved */ }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save Report")
                    }
                    IconButton(onClick = { viewModel.refreshData() }) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Date range selector ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Date Range",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        var expanded by remember { mutableStateOf(false) }
                        val ranges = listOf(
                            "Current Week" to DateRange.CurrentWeek,
                            "Last 7 Days" to DateRange.Last7Days,
                            "Last 30 Days" to DateRange.Last30Days,
                            "Last 90 Days" to DateRange.Last90Days,
                            "This Month" to DateRange.ThisMonth,
                            "Last Month" to DateRange.LastMonth,
                            "This Year" to DateRange.ThisYear,
                            "Custom Range" to DateRange.Custom(LocalDate.now().minusDays(30), LocalDate.now())
                        )
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            val label = when (val range = dateRange) {
                                is DateRange.CurrentWeek -> "Current Week"
                                is DateRange.Last7Days -> "Last 7 Days"
                                is DateRange.Last30Days -> "Last 30 Days"
                                is DateRange.Last90Days -> "Last 90 Days"
                                is DateRange.LastYear -> "Last Year"
                                is DateRange.ThisMonth -> "This Month"
                                is DateRange.LastMonth -> "Last Month"
                                is DateRange.ThisYear -> "This Year"
                                is DateRange.Custom ->
                                    "${range.startDate.format(DateTimeFormatter.ofPattern("dd MMM"))} - " +
                                        range.endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                            }
                            OutlinedTextField(
                                value = label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Range") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                ranges.forEach { (name, range) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            if (range is DateRange.Custom) {
                                                showDateRangePicker = true
                                            } else {
                                                viewModel.setDateRange(range)
                                            }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- Daily summary ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily Summary",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        SummaryRow("Total Income", "₹${dailySummary.totalIncome}", icon = Icons.Default.TrendingUp, iconTint = MaterialTheme.colorScheme.primary)
                        SummaryRow("Total Labor Cost", "₹${dailySummary.totalLaborCost}", icon = Icons.Default.Groups)
                        SummaryRow("Total Overtime Cost", "₹${dailySummary.totalOvertimeCost}", icon = Icons.Default.AccessTime)
                        SummaryRow("Total Advance Paid", "₹${dailySummary.totalAdvanceAmount}", icon = Icons.Default.Payments)
                        SummaryRow("Weekly Payment Done", "₹${dailySummary.totalWeeklyPayment}", icon = Icons.Default.DoneAll)
                        SummaryRow("Total Other Expenses", "₹${dailySummary.totalOtherExpenses}", icon = Icons.Default.ShoppingBag)
                        BroughtForwardRow(periodBroughtForward)

                        val totalExpenses = dailySummary.totalLaborCost
                            .add(dailySummary.totalOvertimeCost)
                            .add(dailySummary.totalOtherExpenses)
                        // Offset leaves out pesticide / fertilizer bills (managed in the Vendor Ledger).
                        val weekExpenses = weeklySummary.totalLaborCost
                            .add(weeklySummary.totalOvertimeCost)
                            .add(weeklySummary.totalOtherExpenses)
                            .subtract(weeklySummary.totalVendorPurchases)
                        val weekPayments = weeklySummary.totalAdvanceAmount
                            .add(weeklySummary.totalWeeklyPayment)
                            .add(weeklySummary.totalExcessBalance)
                        val offset = weekExpenses.subtract(weekPayments)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SummaryRow("TOTAL EXPENSES", "₹$totalExpenses", isTotal = true, valueColor = MaterialTheme.colorScheme.onSurface)
                        OffsetRow(offset)
                        OffsetNote()
                        SummaryRow("TOTAL INCOME", "₹${dailySummary.totalIncome}", isTotal = true)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SummaryRow("Total Days", dailySummary.totalDays.toString())
                        SummaryRow("Avg Daily Income", "₹${dailySummary.averageDailyIncome}")
                        SummaryRow("Avg Daily Expense", "₹${dailySummary.averageDailyExpense}")
                    }
                }
            }

            // ---- Weekly summary ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Weekly Summary",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        SummaryRow("Total Income", "₹${weeklySummary.totalIncome}", icon = Icons.Default.TrendingUp, iconTint = MaterialTheme.colorScheme.primary)
                        SummaryRow("Total Labor Cost", "₹${weeklySummary.totalLaborCost}", icon = Icons.Default.Groups)
                        SummaryRow("Total Overtime Cost", "₹${weeklySummary.totalOvertimeCost}", icon = Icons.Default.AccessTime)
                        SummaryRow("Total Advance Paid", "₹${weeklySummary.totalAdvanceAmount}", icon = Icons.Default.Payments)
                        SummaryRow("Weekly Payment Done", "₹${weeklySummary.totalWeeklyPayment}", icon = Icons.Default.DoneAll)
                        SummaryRow("Total Other Expenses", "₹${weeklySummary.totalOtherExpenses}", icon = Icons.Default.ShoppingBag)
                        BroughtForwardRow(periodBroughtForward)

                        val totalExpenses = weeklySummary.totalLaborCost
                            .add(weeklySummary.totalOvertimeCost)
                            .add(weeklySummary.totalOtherExpenses)
                        val totalPayments = weeklySummary.totalAdvanceAmount
                            .add(weeklySummary.totalWeeklyPayment)
                            .add(weeklySummary.totalExcessBalance)
                        // Offset leaves out pesticide / fertilizer bills (managed in the Vendor Ledger).
                        val offset = totalExpenses
                            .subtract(weeklySummary.totalVendorPurchases)
                            .subtract(totalPayments)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SummaryRow("TOTAL EXPENSES", "₹$totalExpenses", isTotal = true, valueColor = MaterialTheme.colorScheme.onSurface)
                        OffsetRow(offset)
                        OffsetNote()
                        SummaryRow("TOTAL INCOME", "₹${weeklySummary.totalIncome}", isTotal = true)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        SummaryRow("Total Weeks", weeklySummary.totalWeeks.toString())
                        SummaryRow("Avg Weekly Income", "₹${weeklySummary.averageWeeklyIncome}")
                        SummaryRow("Avg Weekly Expense", "₹${weeklySummary.averageWeeklyExpense}")
                    }
                }
            }

            // ---- Weekly specifics ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Weekly Specifics",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium
                        )

                        val workers = viewModel.getWeeklySpecificWorkers(filteredExpenses)
                        val otherExpenses = viewModel.getIndividualOtherExpenses(filteredExpenses)
                        val settlement = viewModel.getWeeklySpecificSettlement(filteredExpenses)
                        val advances = viewModel.getWeeklySpecificAdvances(filteredExpenses)
                        val hasOvertime = workers.any { it.totalOvertimeCost > BigDecimal.ZERO }

                        if (workers.isEmpty() && otherExpenses.isEmpty() && settlement == BigDecimal.ZERO && advances.isEmpty()) {
                            Text("No data for this period", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            if (workers.isNotEmpty()) {
                                SectionTitle("LABOR", topPadding = 8.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Type", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                                    Text("Days", modifier = Modifier.weight(0.6f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                    Text("Cost", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                }
                                HorizontalDivider()
                                workers.forEach { worker ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(worker.workerTypeName, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                                        Text(worker.totalCount.toString(), modifier = Modifier.weight(0.6f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                                        Text("₹${worker.totalBaseCost}", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    HorizontalDivider()
                                }
                            }

                            if (otherExpenses.isNotEmpty()) {
                                SectionTitle("OTHER EXPENSES")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Item", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                                    Text("Qty", modifier = Modifier.weight(0.6f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                    Text("Amount", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                }
                                HorizontalDivider()
                                otherExpenses.forEach { (date, entry) ->
                                    OtherExpenseSpecificRow(date, entry)
                                    HorizontalDivider()
                                }
                            }

                            if (hasOvertime) {
                                SectionTitle("OVERTIME")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Type", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                                    Text("OT Cost", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                }
                                HorizontalDivider()
                                workers.filter { it.totalOvertimeCost > BigDecimal.ZERO }.forEach { worker ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(worker.workerTypeName, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                                        Text("₹${worker.totalOvertimeCost}", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    HorizontalDivider()
                                }
                            }

                            if (advances.isNotEmpty()) {
                                SectionTitle("ADVANCES")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Recipient", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                                    Text("Amount", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                }
                                HorizontalDivider()
                                advances.forEach { advance ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(2f)) {
                                            Text(advance.recipientName, style = MaterialTheme.typography.bodyMedium)
                                            if (advance.reason.isNotEmpty()) {
                                                Text(
                                                    text = advance.reason,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        Text("₹${advance.totalAmount}", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    HorizontalDivider()
                                }
                            }

                            if (settlement > BigDecimal.ZERO) {
                                SectionTitle("SETTLEMENT")
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Weekly Settlement Done", modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "₹$settlement",
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.End,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                HorizontalDivider()
                            }
                        }

                        // Totals
                        val salary = weeklySummary.totalLaborCost.add(weeklySummary.totalOvertimeCost)
                        val other = weeklySummary.totalOtherExpenses
                        val payments = weeklySummary.totalAdvanceAmount.add(weeklySummary.totalWeeklyPayment)
                        val income = weeklySummary.totalIncome
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "TOTALS",
                            modifier = Modifier.padding(bottom = 4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        TotalRow("Employee Salary (incl. OT)", "₹$salary")
                        TotalRow("Other Expenses", "₹$other")
                        TotalRow("Total Payments (Advances + Settlement)", "₹$payments")
                        TotalRow("Total Income", "₹$income", highlight = true)
                    }
                }
            }

            // ---- Labor summary (grouped) ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Labor Summary (Grouped)",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val summary = viewModel.getWeeklyWorkerSummary(filteredExpenses)
                        if (summary.isEmpty()) {
                            Text("No labor data for this period", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Type & Comment", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                                Text("Count", modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                                Text("Total Cost", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider()
                            summary.forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(2f)) {
                                        Text(row.workerType, style = MaterialTheme.typography.bodyMedium)
                                        if (row.comment.isNotEmpty()) {
                                            Text(
                                                text = row.comment,
                                                color = MaterialTheme.colorScheme.secondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    Text(row.totalCount.toString(), modifier = Modifier.weight(0.5f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                                    Text("₹${row.totalCost}", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (row != summary.last()) {
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }

            // ---- Top expense categories ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Top Expense Categories",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val categories = viewModel.getTopExpenseCategories(filteredExpenses)
                        if (categories.isEmpty()) {
                            Text("No expense data available", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            categories.forEach { category ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(category.categoryName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text("₹${category.totalAmount}", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (category != categories.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Income breakdown ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Income Breakdown",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val breakdown = viewModel.getIncomeBreakdown(filteredExpenses)
                        if (breakdown.isEmpty()) {
                            Text("No income data available", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            breakdown.forEach { income ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(income.commodityName, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "Total Weight: ${income.totalWeight} kg",
                                            color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        text = "₹${income.totalAmount}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (income != breakdown.last()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Recent expenses ----
            item {
                Text(
                    text = "Recent Expenses",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(filteredExpenses.take(20), key = { it.id }) { expense ->
                Card(
                    onClick = {
                        val date = try {
                            LocalDate.parse(expense.date)
                        } catch (e: Exception) {
                            LocalDate.now()
                        }
                        onNavigateToExpenseEntry(date, expense.id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val date = try {
                                LocalDate.parse(expense.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                            } catch (e: Exception) {
                                expense.date
                            }
                            Text(date, style = MaterialTheme.typography.titleSmall)
                            Row {
                                val total = expense.totalLaborCost
                                    .add(expense.totalOvertimeCost)
                                    .add(expense.totalOtherExpensesCost)
                                    .add(expense.advanceAmount)
                                    .add(expense.excessBalance)
                                Text(
                                    text = "Exp: ₹$total",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Inc: ₹${expense.totalIncome}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (expense.comments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = expense.comments,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No expense data found for the selected date range",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }
}

@Composable
private fun SectionTitle(title: String, topPadding: androidx.compose.ui.unit.Dp = 12.dp) {
    Text(
        text = title,
        modifier = Modifier.padding(top = topPadding, bottom = 2.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall
    )
}

@Composable
private fun OtherExpenseSpecificRow(date: String, entry: OtherExpenseEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = if (entry.subtypeName != null) "${entry.typeName} (${entry.subtypeName})" else entry.typeName,
                style = MaterialTheme.typography.bodyMedium
            )
            val formattedDate = try {
                LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMM"))
            } catch (e: Exception) {
                date
            }
            Text(
                text = formattedDate,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
            if (entry.notes.isNotEmpty()) {
                Text(
                    text = entry.notes,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        val qty = if (entry.quantity == entry.quantity.toLong().toDouble()) {
            entry.quantity.toLong().toString()
        } else {
            String.format("%.1f", entry.quantity)
        }
        Text(qty, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
        Text("₹${entry.amount}", modifier = Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TotalRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(
            text = value,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    isTotal: Boolean = false,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.secondary,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = iconTint
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            color = valueColor
                ?: if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OffsetNote() {
    Text(
        text = "Offset excludes pesticide/fertilizer bills (tracked in Vendor Ledger)",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * Offset = expenses - payments. A positive offset means the payments did not cover the expenses
 * (short, shown in red); a negative offset means more was paid than spent (excess, shown in green).
 * The amount is shown without a sign; the label says which side it is on.
 */
@Composable
private fun OffsetRow(offset: BigDecimal) {
    val (label, color) = when (offset.signum()) {
        1 -> "TOTAL OFFSET (Short)" to MaterialTheme.colorScheme.error
        -1 -> "TOTAL OFFSET (Excess)" to MaterialTheme.colorScheme.primary
        else -> "TOTAL OFFSET" to MaterialTheme.colorScheme.onSurface
    }
    SummaryRow(label, "₹${offset.abs()}", isTotal = true, valueColor = color)
}

/**
 * Cash balance carried into the start of the selected range from all earlier days.
 * Positive = excess (green), negative = short (red).
 */
@Composable
private fun BroughtForwardRow(amount: BigDecimal) {
    val (label, color) = when (amount.signum()) {
        1 -> "Brought Forward (Excess)" to MaterialTheme.colorScheme.primary
        -1 -> "Brought Forward (Short)" to MaterialTheme.colorScheme.error
        else -> "Brought Forward" to MaterialTheme.colorScheme.onSurface
    }
    SummaryRow(
        label = label,
        value = "₹${amount.abs()}",
        icon = Icons.Default.AccountBalanceWallet,
        valueColor = color
    )
}
