package com.santhomach.plantationledger.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.santhomach.plantationledger.data.model.AdvanceEntry
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.IncomeEntry
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WorkerGroupEntry
import com.santhomach.plantationledger.data.repository.ExpenseSummary
import com.santhomach.plantationledger.ui.viewmodel.ReportsViewModel
import com.santhomach.plantationledger.ui.viewmodel.VendorLedger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Dashboard: estate performance hero card, vendor outstanding, quick actions and the
 * month-grouped list of recent expenses (tap to view details, long-press to clone).
 */
@Composable
fun HomeScreen(
    onNavigateToExpenseEntry: (LocalDate, Int?) -> Unit = { _, _ -> },
    onNavigateToReports: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPayments: () -> Unit = {},
    onNavigateToWeeklyFunds: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToExpenseTypeSummary: () -> Unit = {},
    onNavigateToVendorLedger: () -> Unit = {},
    viewModel: ReportsViewModel? = null
) {
    if (LocalInspectionMode.current && viewModel == null) {
        HomeScreenContent(
            recentExpenses = emptyList(),
            dailySummary = ExpenseSummary(),
            weekSummary = ExpenseSummary(),
            yearSummary = ExpenseSummary(),
            allTimeSummary = ExpenseSummary(),
            onNavigateToExpenseEntry = onNavigateToExpenseEntry,
            onNavigateToReports = onNavigateToReports,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPayments = onNavigateToPayments,
            onNavigateToWeeklyFunds = onNavigateToWeeklyFunds,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToExpenseTypeSummary = onNavigateToExpenseTypeSummary
        )
        return
    }
    val actualViewModel: ReportsViewModel = viewModel ?: hiltViewModel()

    val yearlyExpenses by actualViewModel.yearlyExpenses.collectAsState()
    val dailySummary by actualViewModel.dailySummary.collectAsState()
    val weekSummary by actualViewModel.weekSummary.collectAsState()
    val yearSummary by actualViewModel.yearSummary.collectAsState()
    val allTimeSummary by actualViewModel.allTimeSummary.collectAsState()
    val previousWeekCarryover by actualViewModel.previousWeekCarryover.collectAsState()
    val vendorLedger by actualViewModel.vendorLedger.collectAsState()
    val uiState by actualViewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedExpense by remember { mutableStateOf<DailyExpense?>(null) }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    var expenseToClone by remember { mutableStateOf<DailyExpense?>(null) }
    val selectedExpenseVendorPayments by actualViewModel.selectedExpenseVendorPayments.collectAsState()

    LaunchedEffect(uiState.cloneSuccessDate) {
        val date = uiState.cloneSuccessDate ?: return@LaunchedEffect
        snackbarHostState.showSnackbar("Cloned to ${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
        actualViewModel.clearCloneSuccess()
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onNavigateToExpenseEntry(date, null)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    selectedExpense?.let { expense ->
        ExpenseDetailDialog(
            expense = expense,
            vendorPayments = selectedExpenseVendorPayments,
            onDismiss = {
                actualViewModel.selectExpenseDate(null)
                selectedExpense = null
            },
            onEdit = {
                actualViewModel.selectExpenseDate(null)
                selectedExpense = null
                val date = try {
                    LocalDate.parse(expense.date)
                } catch (e: Exception) {
                    LocalDate.now()
                }
                onNavigateToExpenseEntry(date, expense.id)
            },
            onImageClick = { previewImagePath = it },
            onDeleteVendorPayment = { actualViewModel.deleteVendorPayment(it) }
        )
    }

    previewImagePath?.let { path ->
        ImagePreviewDialog(imagePath = path, onDismiss = { previewImagePath = null })
    }

    expenseToClone?.let { expense ->
        val sourceDate = try {
            LocalDate.parse(expense.date)
        } catch (e: Exception) {
            LocalDate.now()
        }
        CloneExpenseDialog(
            expense = expense,
            nextWorkday = actualViewModel.nextWorkday(sourceDate),
            onDismiss = { expenseToClone = null },
            onClone = { targetDate ->
                actualViewModel.cloneExpense(expense, targetDate)
                expenseToClone = null
            }
        )
    }

    HomeScreenContent(
        recentExpenses = yearlyExpenses,
        dailySummary = dailySummary,
        weekSummary = weekSummary,
        yearSummary = yearSummary,
        allTimeSummary = allTimeSummary,
        previousWeekCarryover = previousWeekCarryover,
        vendorLedger = vendorLedger,
        snackbarHostState = snackbarHostState,
        onNavigateToExpenseEntry = onNavigateToExpenseEntry,
        onNavigateToReports = onNavigateToReports,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPayments = onNavigateToPayments,
        onNavigateToWeeklyFunds = onNavigateToWeeklyFunds,
        onNavigateToSearch = onNavigateToSearch,
        onNavigateToExpenseTypeSummary = onNavigateToExpenseTypeSummary,
        onShowDatePicker = { showDatePicker = true },
        onViewExpense = {
            actualViewModel.selectExpenseDate(it.date)
            selectedExpense = it
        },
        onLongPressExpense = { expenseToClone = it },
        onNavigateToVendorLedger = onNavigateToVendorLedger
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    recentExpenses: List<DailyExpense>,
    dailySummary: ExpenseSummary,
    weekSummary: ExpenseSummary,
    yearSummary: ExpenseSummary,
    allTimeSummary: ExpenseSummary,
    previousWeekCarryover: BigDecimal = BigDecimal.ZERO,
    vendorLedger: VendorLedger = VendorLedger(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToExpenseEntry: (LocalDate, Int?) -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToWeeklyFunds: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToExpenseTypeSummary: () -> Unit = {},
    onShowDatePicker: () -> Unit = {},
    onViewExpense: (DailyExpense) -> Unit = {},
    onLongPressExpense: (DailyExpense) -> Unit = {},
    onNavigateToVendorLedger: () -> Unit = {}
) {
    // Group expenses by "yyyy-MM", newest month first: Triple(key, "MMMM yyyy", expenses)
    val groupedExpenses = remember(recentExpenses) {
        recentExpenses
            .groupBy { expense ->
                try {
                    val date = LocalDate.parse(expense.date)
                    "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
                } catch (e: Exception) {
                    "Unknown"
                }
            }
            .entries
            .sortedByDescending { it.key }
            .map { (key, list) ->
                val label = try {
                    val parts = key.split("-")
                    LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
                        .format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                } catch (e: Exception) {
                    key
                }
                Triple(key, label, list)
            }
    }
    val expandedMonths = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PLANTATION LEDGER",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToExpenseEntry(LocalDate.now(), null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Expense")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PerformanceHeroCard(
                        weekSummary = weekSummary,
                        yearSummary = yearSummary,
                        allTimeSummary = allTimeSummary,
                        previousWeekCarryover = previousWeekCarryover
                    )
                }

                if (vendorLedger.totalExpenses > BigDecimal.ZERO) {
                    item {
                        VendorOutstandingCard(ledger = vendorLedger, onNavigateToLedger = onNavigateToVendorLedger)
                    }
                }

                item {
                    QuickActionsSection(
                        onAddToday = { onNavigateToExpenseEntry(LocalDate.now(), null) },
                        onAddForDate = onShowDatePicker,
                        onReports = onNavigateToReports,
                        onPayments = onNavigateToPayments,
                        onWeekly = onNavigateToWeeklyFunds,
                        onExpenseTypes = onNavigateToExpenseTypeSummary
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onNavigateToReports) {
                            Text("View All")
                        }
                    }
                }

                groupedExpenses.forEach { (key, label, expenses) ->
                    val expanded = expandedMonths[key] ?: true
                    item(key = "header_$key") {
                        MonthGroupHeader(
                            label = label,
                            count = expenses.size,
                            expanded = expanded,
                            onToggle = { expandedMonths[key] = !expanded }
                        )
                    }
                    if (expanded) {
                        items(expenses, key = { it.id }) { expense ->
                            ExpenseListCard(
                                expense = expense,
                                onClick = { onViewExpense(expense) },
                                onLongClick = { onLongPressExpense(expense) }
                            )
                        }
                    }
                }

                if (recentExpenses.isEmpty()) {
                    item {
                        EmptyStateCard(onClick = { onNavigateToExpenseEntry(LocalDate.now(), null) })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero card
// ---------------------------------------------------------------------------------------------

private val HeroGradientTop = Color(0xFF1A2E3A)
private val HeroGradientBottom = Color(0xFF060F15)
private val HeroAccent = Color(0xFF52C08A)
private val HeroIncomeColor = Color(0xFF80CBB2)
private val HeroExpenseColor = Color(0xFFFFAB6D)
private val HeroExcessColor = Color(0xFFFDD800)
private val HeroShortColor = Color(0xFFFF6B6B)
private val HeroGoodColor = Color(0xFF69C97E)
private val HeroBadColor = Color(0xFFFF5252)

@Composable
private fun PerformanceHeroCard(
    weekSummary: ExpenseSummary,
    yearSummary: ExpenseSummary,
    allTimeSummary: ExpenseSummary,
    previousWeekCarryover: BigDecimal = BigDecimal.ZERO
) {
    // Pesticide / fertilizer bills are managed in the Vendor Ledger, so neither the purchases
    // nor the payments to vendors take part in the weekly excess / short balance.
    val weekExpenses = weekSummary.totalLaborCost
        .add(weekSummary.totalOvertimeCost)
        .add(weekSummary.totalOtherExpenses)
        .subtract(weekSummary.totalVendorPurchases)
    val weekPayments = weekSummary.totalAdvanceAmount
        .add(weekSummary.totalWeeklyPayment)
        .add(weekSummary.totalExcessBalance)
    val weekBalance = weekPayments.subtract(weekExpenses).add(previousWeekCarryover)

    val yearExpenses = yearSummary.totalLaborCost
        .add(yearSummary.totalOvertimeCost)
        .add(yearSummary.totalOtherExpenses)
    val allTimeExpenses = allTimeSummary.totalLaborCost
        .add(allTimeSummary.totalOvertimeCost)
        .add(allTimeSummary.totalOtherExpenses)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(HeroGradientTop, HeroGradientBottom)))
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Insights,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = HeroAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Plantation Performance",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            SummaryLine("THIS WEEK", weekSummary.totalIncome, weekExpenses, balance = weekBalance)
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.15f))
            SummaryLine("THIS YEAR", yearSummary.totalIncome, yearExpenses, showEfficiency = true)
            HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.15f))
            SummaryLine("ALL TIME", allTimeSummary.totalIncome, allTimeExpenses, showEfficiency = true)
        }
    }
}

@Composable
private fun SummaryLine(
    title: String,
    income: BigDecimal,
    expense: BigDecimal,
    balance: BigDecimal? = null,
    showEfficiency: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.55f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            style = MaterialTheme.typography.labelMedium
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            StatItemMini("Income", "₹$income", HeroIncomeColor, Modifier.weight(1f))
            StatItemMini("Expense", "₹$expense", HeroExpenseColor, Modifier.weight(1f))
            if (balance != null) {
                val positive = balance >= BigDecimal.ZERO
                StatItemMini(
                    label = if (positive) "Excess" else "Short",
                    value = "₹${balance.abs()}",
                    color = if (positive) HeroExcessColor else HeroShortColor,
                    modifier = Modifier.weight(1f)
                )
            } else if (showEfficiency) {
                val efficiency = if (expense > BigDecimal.ZERO) {
                    income.multiply(BigDecimal("100")).divide(expense, 0, RoundingMode.HALF_UP).toInt()
                } else {
                    if (income > BigDecimal.ZERO) 100 else 0
                }
                val good = income >= expense
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Effic.",
                        color = Color.White.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (good) Icons.AutoMirrored.Filled.TrendingUp
                            else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (good) HeroGoodColor else HeroBadColor
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "$efficiency%",
                            color = if (good) HeroGoodColor else HeroBadColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItemMini(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Suppress("unused")
@Composable
private fun StatItem(label: String, value: String, color: Color, icon: ImageVector? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(text = value, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------------------------------------
// Quick actions
// ---------------------------------------------------------------------------------------------

@Composable
private fun QuickActionsSection(
    onAddToday: () -> Unit,
    onAddForDate: () -> Unit,
    onReports: () -> Unit,
    onPayments: () -> Unit,
    onWeekly: () -> Unit,
    onExpenseTypes: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quick Actions",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                QuickActionButton("Add Today", Icons.Default.Today, Color(0xFF1D7A4D), Modifier.weight(1f), onAddToday)
                QuickActionButton("By Date", Icons.Default.CalendarMonth, Color(0xFF155FC0), Modifier.weight(1f), onAddForDate)
                QuickActionButton("Reports", Icons.Default.BarChart, Color(0xFF6A3E9A), Modifier.weight(1f), onReports)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                QuickActionButton("Payments", Icons.Default.Payments, Color(0xFFBF5B00), Modifier.weight(1f), onPayments)
                QuickActionButton("Weekly", Icons.Default.ListAlt, Color(0xFF00695C), Modifier.weight(1f), onWeekly)
                QuickActionButton("Breakdown", Icons.Default.PieChart, Color(0xFFC62828), Modifier.weight(1f), onExpenseTypes)
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.White)
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Recent activity list
// ---------------------------------------------------------------------------------------------

@Composable
private fun MonthGroupHeader(label: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
        }
        Text(
            text = "$count entries",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseListCard(expense: DailyExpense, onClick: () -> Unit, onLongClick: () -> Unit) {
    val hasAttachment = remember(expense.otherExpenses) {
        try {
            Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                .any { it.receiptImagePath != null }
        } catch (e: Exception) {
            false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val date = try {
                        LocalDate.parse(expense.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy, EEE"))
                    } catch (e: Exception) {
                        expense.date
                    }
                    Text(
                        text = date,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (hasAttachment) {
                        Icon(
                            imageVector = Icons.Default.Attachment,
                            contentDescription = "Has attachment",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AmountChip(
                        "Inc", "₹${expense.totalIncome}",
                        MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer
                    )
                    val totalExpense = expense.totalLaborCost
                        .add(expense.totalOvertimeCost)
                        .add(expense.totalOtherExpensesCost)
                    AmountChip(
                        "Exp", "₹$totalExpense",
                        MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer
                    )
                    val totalPayments = expense.advanceAmount
                        .add(expense.excessBalance)
                        .add(expense.weeklyPaymentDone)
                    AmountChip(
                        "Pay", "₹$totalPayments",
                        MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                val totalWorkers = expense.malayaliMaleCount + expense.bengaliMaleCount +
                    expense.malayaliFemaleCount + expense.bengaliFemaleCount
                if (totalWorkers > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$totalWorkers workers (${expense.malayaliMaleCount}M + ${expense.bengaliMaleCount}M + " +
                                "${expense.malayaliFemaleCount}F + ${expense.bengaliFemaleCount}F)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (expense.comments.isNotEmpty()) {
                    Text(
                        text = expense.comments,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountChip(label: String, value: String, color: Color, background: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$label:",
            color = color.copy(alpha = 0.65f),
            fontSize = 10.sp,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun EmptyStateCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.EventNote,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = "No expenses recorded yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onClick) {
                Text("Add Your First Expense")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Vendor outstanding
// ---------------------------------------------------------------------------------------------

private val VendorPaidGreen = Color(0xFF2E7D32)
private val VendorPaidText = Color(0xFF1B5E20)
private val VendorDueRed = Color(0xFFB71C1C)

@Composable
private fun VendorOutstandingCard(ledger: VendorLedger, onNavigateToLedger: () -> Unit) {
    val allPaid = ledger.outstanding.signum() <= 0
    val containerColor = if (allPaid) VendorPaidGreen.copy(alpha = 0.15f) else VendorDueRed.copy(alpha = 0.12f)
    val textColor = if (allPaid) VendorPaidText else VendorDueRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onNavigateToLedger),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (allPaid) "Vendor Bills — All Paid" else "Vendor Bills Outstanding",
                color = textColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = if (allPaid) "₹0" else "₹${ledger.outstanding}",
                color = textColor,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Total: ₹${ledger.totalExpenses}",
                    modifier = Modifier.weight(1f),
                    color = textColor.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Paid: ₹${ledger.totalPaid}",
                    color = textColor.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Expense detail dialog
// ---------------------------------------------------------------------------------------------

private inline fun <reified T> decodeList(json: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> =
    try {
        if (json.isBlank() || json == "[]") emptyList()
        else Json.decodeFromString(ListSerializer(serializer), json)
    } catch (e: Exception) {
        emptyList()
    }

@Composable
fun ExpenseDetailDialog(
    expense: DailyExpense,
    vendorPayments: List<VendorPayment> = emptyList(),
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onImageClick: (String) -> Unit = {},
    onDeleteVendorPayment: (VendorPayment) -> Unit = {}
) {
    val workerGroups = remember(expense.workerGroups) { decodeList(expense.workerGroups, WorkerGroupEntry.serializer()) }
    val otherExpenses = remember(expense.otherExpenses) { decodeList(expense.otherExpenses, OtherExpenseEntry.serializer()) }
    val incomeEntries = remember(expense.incomeEntries) { decodeList(expense.incomeEntries, IncomeEntry.serializer()) }
    val advanceEntries = remember(expense.advanceEntries) { decodeList(expense.advanceEntries, AdvanceEntry.serializer()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Edit Details")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
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
                Text(date, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Totals
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DetailRow("Total Labor", "₹${expense.totalLaborCost}")
                        DetailRow("Total Overtime", "₹${expense.totalOvertimeCost}")
                        DetailRow("Other Expenses", "₹${expense.totalOtherExpensesCost}")
                        DetailRow("Advances Paid", "₹${expense.advanceAmount}")
                        DetailRow("Weekly Settlement", "₹${expense.weeklyPaymentDone}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DetailRow("TOTAL INCOME", "₹${expense.totalIncome}", isTotal = true, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (workerGroups.isNotEmpty()) {
                    DetailSectionTitle("Labor Breakdown")
                    workerGroups.forEach { group ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp)
                        ) {
                            Text("${group.workerTypeName} (${group.count})", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Task: ${group.taskPerformed}",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text("Cost: ₹${group.calculateTotalGroupCost()}", style = MaterialTheme.typography.bodySmall)
                            if (group.comments.isNotEmpty()) {
                                Text(
                                    text = group.comments,
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        ThinDivider()
                    }
                }

                if (otherExpenses.isNotEmpty()) {
                    DetailSectionTitle("Other Expenses")
                    otherExpenses.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (entry.subtypeName != null) "${entry.typeName} (${entry.subtypeName})" else entry.typeName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (entry.notes.isNotEmpty()) {
                                    Text(
                                        text = entry.notes,
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text("Qty: ${entry.quantity} | ₹${entry.amount}", style = MaterialTheme.typography.bodySmall)
                            }
                            entry.receiptImagePath?.let { path ->
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = "Receipt",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onImageClick(path) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        ThinDivider()
                    }
                }

                if (incomeEntries.isNotEmpty()) {
                    DetailSectionTitle("Income Details")
                    incomeEntries.forEach { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp)
                        ) {
                            Text(entry.typeName, style = MaterialTheme.typography.bodyMedium)
                            Text("${entry.weight} kg @ ₹${entry.pricePerKilo}/kg", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "Total: ₹${entry.amount}",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        ThinDivider()
                    }
                }

                if (advanceEntries.isNotEmpty()) {
                    DetailSectionTitle("Advance Payments")
                    advanceEntries.forEach { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp)
                        ) {
                            Text("Recipient: ${entry.recipientName}", style = MaterialTheme.typography.bodyMedium)
                            Text("Amount: ₹${entry.amount}", style = MaterialTheme.typography.bodySmall)
                            if (entry.reason.isNotEmpty()) {
                                Text(
                                    text = "Reason: ${entry.reason}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        ThinDivider()
                    }
                }

                if (vendorPayments.isNotEmpty()) {
                    DetailSectionTitle("Vendor Payments")
                    vendorPayments.forEach { payment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val paidDate = try {
                                    LocalDate.parse(payment.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                } catch (e: Exception) {
                                    payment.date
                                }
                                Text(
                                    text = "Paid on $paidDate",
                                    color = VendorPaidGreen,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (payment.notes.isNotEmpty()) {
                                    Text(
                                        text = payment.notes,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(
                                text = "₹${payment.amount}",
                                color = VendorPaidGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(onClick = { onDeleteVendorPayment(payment) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete payment",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        ThinDivider()
                    }
                }

                if (expense.comments.isNotEmpty()) {
                    DetailSectionTitle("Daily Comments")
                    Text(
                        text = expense.comments,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

@Composable
private fun ThinDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun DetailSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun DetailRow(label: String, value: String, isTotal: Boolean = false, color: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall
        Text(label, style = style)
        Text(
            text = value,
            color = if (color == Color.Unspecified) {
                if (isTotal) MaterialTheme.colorScheme.primary else Color.Unspecified
            } else color,
            style = style
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Clone dialog & image preview
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneExpenseDialog(
    expense: DailyExpense,
    nextWorkday: LocalDate,
    onDismiss: () -> Unit,
    onClone: (LocalDate) -> Unit
) {
    val sourceDate = try {
        LocalDate.parse(expense.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    } catch (e: Exception) {
        expense.date
    }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = nextWorkday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onClone(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
                    }
                    showDatePicker = false
                }) {
                    Text("Clone")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onClone(nextWorkday) }) {
                Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(nextWorkday.format(DateTimeFormatter.ofPattern("EEE dd MMM")))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pick Date")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
        title = { Text("Clone Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Copy all labor, other expenses, and comments from $sourceDate to:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Overtime, other expenses, income, advances, and settlements are NOT copied.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

@Composable
fun ImagePreviewDialog(imagePath: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(imagePath),
                contentDescription = "Full Size Receipt",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
