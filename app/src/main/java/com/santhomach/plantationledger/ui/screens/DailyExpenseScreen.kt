package com.santhomach.plantationledger.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.santhomach.plantationledger.data.model.AdvanceEntry
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeEntry
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerGroupEntry
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.ui.viewmodel.DailyExpenseViewModel
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val PaidGreenColor = Color(0xFF2E7D32)
private const val OTHER_OPTION = "Other (Enter New)"

/**
 * Add / edit the expense record of a single day: manager, worker groups, extra overtime,
 * other expenses (with receipt images), income, advances, weekly settlement and comments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyExpenseScreen(
    date: LocalDate = LocalDate.now(),
    expenseId: Int? = null,
    onNavigateBack: () -> Unit = {},
    viewModelArg: DailyExpenseViewModel? = null
) {
    if (LocalInspectionMode.current && viewModelArg == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Daily Expense Screen Preview")
        }
        return
    }
    val viewModel: DailyExpenseViewModel = viewModelArg ?: hiltViewModel()

    val uiState by viewModel.uiState.collectAsState()
    val currentExpense by viewModel.currentExpense.collectAsState()
    val expenseTypes by viewModel.expenseTypes.collectAsState()
    val expenseSubtypes by viewModel.expenseSubtypes.collectAsState()
    val incomeTypes by viewModel.incomeTypes.collectAsState()
    val workerTypes by viewModel.workerTypes.collectAsState()
    val permanentWorkers by viewModel.permanentWorkers.collectAsState()
    val workTasks by viewModel.workTasks.collectAsState()
    val previousExcessBalance by viewModel.previousExcessBalance.collectAsState()
    val vendorPaymentsForExpense by viewModel.vendorPaymentsForCurrentExpense.collectAsState()

    val scrollState = rememberScrollState()

    var shouldNavigateBack by remember { mutableStateOf(false) }
    var editingWorkerGroupIndex by remember { mutableStateOf<Int?>(null) }
    var showAddWorkerGroupDialog by remember { mutableStateOf(false) }
    var editingExpenseIndex by remember { mutableStateOf<Int?>(null) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var editingIncomeIndex by remember { mutableStateOf<Int?>(null) }
    var showAddIncomeDialog by remember { mutableStateOf(false) }
    var editingAdvanceIndex by remember { mutableStateOf<Int?>(null) }
    var showAddAdvanceDialog by remember { mutableStateOf(false) }

    val workerGroups = remember(currentExpense) { viewModel.getWorkerGroups() }
    val otherExpenses = remember(currentExpense) { viewModel.getOtherExpenses() }
    val incomeEntries = remember(currentExpense) { viewModel.getIncomeEntries() }
    val advanceEntries = remember(currentExpense) { viewModel.getAdvanceEntries() }

    LaunchedEffect(expenseId, date) {
        if (expenseId != null) viewModel.loadExpense(expenseId) else viewModel.loadOrCreateExpense(date)
    }

    LaunchedEffect(shouldNavigateBack, uiState.isSaving) {
        if (shouldNavigateBack && !uiState.isSaving) {
            shouldNavigateBack = false
            onNavigateBack()
        }
    }

    val isExistingRecord = expenseId != null || (currentExpense?.id ?: 0) != 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isExistingRecord) "Edit Expense" else "Add Daily Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isExistingRecord) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete Expense") },
                                text = { Text("Are you sure you want to delete this expense record?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.deleteExpense()
                                            onNavigateBack()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteConfirm = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                    if (uiState.isEditing) {
                        IconButton(onClick = {
                            shouldNavigateBack = true
                            viewModel.saveExpense()
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- General details ----
            SectionCard {
                Text("General Details", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Date: ${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = previousExcessBalance.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Previous Excess Balance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                var managerExpanded by remember { mutableStateOf(false) }
                val selectedManager = permanentWorkers.find { worker ->
                    currentExpense?.managerId?.let { it == worker.id } ?: false
                }
                ExposedDropdownMenuBox(
                    expanded = managerExpanded,
                    onExpandedChange = { managerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedManager?.name ?: "Select Manager (Optional)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Estate Manager") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = managerExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = managerExpanded,
                        onDismissRequest = { managerExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                viewModel.updateManager(null)
                                managerExpanded = false
                            }
                        )
                        permanentWorkers
                            .filter { it.role.contains("Manager", ignoreCase = true) }
                            .forEach { worker ->
                                DropdownMenuItem(
                                    text = { Text(worker.name) },
                                    onClick = {
                                        viewModel.updateManager(worker.id)
                                        managerExpanded = false
                                    }
                                )
                            }
                    }
                }
            }

            // ---- Worker groups ----
            SectionCard {
                SectionHeader("Worker Groups & Tasks")
                val groupedByTask = remember(workerGroups) {
                    workerGroups.withIndex()
                        .map { it.index to it.value }
                        .groupBy { it.second.taskPerformed }
                        .entries
                        .toList()
                }
                groupedByTask.forEachIndexed { groupIndex, (task, entries) ->
                    if (groupIndex > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    Text(
                        text = task,
                        modifier = Modifier.padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    entries.forEach { (index, group) ->
                        WorkerGroupItemRow(
                            group = group,
                            onRemove = { viewModel.removeWorkerGroup(index) },
                            onEdit = { editingWorkerGroupIndex = index }
                        )
                    }
                }
                if (workerGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { showAddWorkerGroupDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Worker Group")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Total Labor Cost: ₹${currentExpense?.totalLaborCost ?: BigDecimal.ZERO}",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            // ---- Extra overtime ----
            SectionCard {
                SectionHeader("Extra Overtime")
                OutlinedTextField(
                    value = (currentExpense?.extraOvertimeAmount ?: BigDecimal.ZERO).toString(),
                    onValueChange = { viewModel.updateExtraOvertimeAmount(it.toBigDecimalOrNull() ?: BigDecimal.ZERO) },
                    label = { Text("Extra Overtime Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Total Overtime Cost (incl. Workers): ₹${currentExpense?.totalOvertimeCost ?: BigDecimal.ZERO}",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Other expenses ----
            SectionCard {
                SectionHeader("Other Expenses")
                otherExpenses.forEachIndexed { index, entry ->
                    ExpenseItemRow(
                        expense = entry,
                        onRemove = { viewModel.removeOtherExpense(index) },
                        onEdit = { editingExpenseIndex = index }
                    )
                }
                OutlinedButton(
                    onClick = { showAddExpenseDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Expense")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Total Other Expenses: ₹${currentExpense?.totalOtherExpensesCost ?: BigDecimal.ZERO}",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            // ---- Vendor bill payments linked to this day's purchases ----
            if (vendorPaymentsForExpense.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = PaidGreenColor.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = PaidGreenColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vendor Bill Payments", style = MaterialTheme.typography.titleMedium)
                        }
                        vendorPaymentsForExpense.forEachIndexed { index, payment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val paidDate = try {
                                        LocalDate.parse(payment.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                    } catch (e: Exception) {
                                        payment.date
                                    }
                                    Text(
                                        text = "Paid on $paidDate",
                                        color = PaidGreenColor,
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (!payment.sourceTypeName.isNullOrEmpty()) {
                                        val subtype = payment.sourceSubtypeName?.let { " ($it)" } ?: ""
                                        Text(
                                            text = "${payment.sourceTypeName}$subtype",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
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
                                    color = PaidGreenColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            if (index < vendorPaymentsForExpense.lastIndex) {
                                HorizontalDivider(color = PaidGreenColor.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }

            // ---- Income ----
            SectionCard {
                SectionHeader("Income")
                incomeEntries.forEachIndexed { index, entry ->
                    IncomeItemRow(
                        income = entry,
                        onRemove = { viewModel.removeIncome(index) },
                        onEdit = { editingIncomeIndex = index }
                    )
                }
                OutlinedButton(
                    onClick = { showAddIncomeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Income")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Total Income: ₹${currentExpense?.totalIncome ?: BigDecimal.ZERO}",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            // ---- Advance payments ----
            SectionCard {
                SectionHeader("Advance Payments")
                Spacer(modifier = Modifier.height(8.dp))
                advanceEntries.forEachIndexed { index, entry ->
                    AdvanceItemRow(
                        advance = entry,
                        onRemove = { viewModel.removeAdvanceEntry(index) },
                        onEdit = { editingAdvanceIndex = index }
                    )
                    if (index < advanceEntries.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                if (advanceEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { showAddAdvanceDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Advance")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Total Advance: ₹${currentExpense?.advanceAmount ?: BigDecimal.ZERO}",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            // ---- Weekly settlement ----
            SectionCard(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Weekly Settlement",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = (currentExpense?.weeklyPaymentDone ?: BigDecimal.ZERO).toString(),
                    onValueChange = { viewModel.updateWeeklyPaymentDone(it.toBigDecimalOrNull() ?: BigDecimal.ZERO) },
                    label = { Text("Payment Done for the Week (₹)") },
                    prefix = { Text("₹") },
                    supportingText = { Text("Amount paid to manager/workers for the full week") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Comments ----
            SectionCard {
                Text(
                    text = "Daily Comments",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = currentExpense?.comments ?: "",
                    onValueChange = { viewModel.updateComments(it) },
                    label = { Text("General comments for the day") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Daily summary ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Daily Summary",
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (workerGroups.isNotEmpty()) {
                        Text(
                            text = "Labor Summary:",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                        workerGroups.forEach { group ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${group.workerTypeName} (${group.count}) - ${group.taskPerformed}",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("₹${group.calculateTotalGroupCost()}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    SummaryRow("Total Labor", "₹${currentExpense?.totalLaborCost ?: BigDecimal.ZERO}")
                    SummaryRow("Total Overtime", "₹${currentExpense?.totalOvertimeCost ?: BigDecimal.ZERO}")
                    SummaryRow("Other Expenses", "₹${currentExpense?.totalOtherExpensesCost ?: BigDecimal.ZERO}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SummaryRow("TOTAL ACTUAL COSTS", "₹${viewModel.getTotalActualExpenses()}", isTotal = true)
                    val vendorPurchases = viewModel.getVendorPurchases()
                    if (vendorPurchases > BigDecimal.ZERO) {
                        SummaryRow("Less: pesticide/fertilizer (Vendor Ledger)", "-₹$vendorPurchases")
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Payments & Offset",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                    SummaryRow("Previous Excess/Deficit", "₹$previousExcessBalance")
                    SummaryRow("Total Advances Paid", "₹${currentExpense?.advanceAmount ?: BigDecimal.ZERO}")
                    SummaryRow("Weekly Settlement Done", "₹${currentExpense?.weeklyPaymentDone ?: BigDecimal.ZERO}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SummaryRow("TOTAL PAYMENTS", "₹${viewModel.getTotalPaymentsMade()}", isTotal = true)
                    val net = viewModel.getNetAmount()
                    SummaryRow(
                        label = if (net >= BigDecimal.ZERO) "REMAINING BALANCE (Excess)" else "REMAINING DEFICIT",
                        value = "₹${net.abs()}",
                        isTotal = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("TOTAL INCOME", "₹${currentExpense?.totalIncome ?: BigDecimal.ZERO}", isTotal = true)
                }
            }

            // ---- Actions ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.cancelEditing()
                        onNavigateBack()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        shouldNavigateBack = true
                        viewModel.saveExpense()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Save")
                    }
                }
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // ---- Dialogs ----

    if (showAddWorkerGroupDialog || editingWorkerGroupIndex != null) {
        val initial = editingWorkerGroupIndex?.let { workerGroups.getOrNull(it) }
        AddWorkerGroupDialog(
            workerTypes = workerTypes,
            workTasks = workTasks,
            onDismiss = {
                showAddWorkerGroupDialog = false
                editingWorkerGroupIndex = null
            },
            onConfirm = { workerTypeId, count, wage, otHours, otWage, task, comments ->
                val index = editingWorkerGroupIndex
                if (index != null) {
                    viewModel.updateWorkerGroup(index, workerTypeId, count, wage, otHours, otWage, task, comments)
                } else {
                    viewModel.addWorkerGroup(workerTypeId, count, wage, otHours, otWage, task, comments)
                }
                showAddWorkerGroupDialog = false
                editingWorkerGroupIndex = null
            },
            onAddNewTask = { viewModel.addNewWorkTask(it) },
            initialGroup = initial
        )
    }

    if (showAddExpenseDialog || editingExpenseIndex != null) {
        val initial = editingExpenseIndex?.let { otherExpenses.getOrNull(it) }
        AddExpenseDialog(
            expenseTypes = expenseTypes,
            expenseSubtypes = expenseSubtypes,
            onDismiss = {
                showAddExpenseDialog = false
                editingExpenseIndex = null
            },
            onConfirm = { typeId, customTypeName, customSubtypeName, amount, quantity, notes, receiptPath ->
                val index = editingExpenseIndex
                if (index != null) {
                    viewModel.updateOtherExpense(index, typeId, amount, quantity, notes, customTypeName, customSubtypeName, receiptPath)
                } else {
                    viewModel.addOtherExpense(typeId, amount, quantity, notes, customTypeName, customSubtypeName, receiptPath)
                }
                showAddExpenseDialog = false
                editingExpenseIndex = null
            },
            initialExpense = initial,
            onSaveImage = { viewModel.saveReceiptImage(it) }
        )
    }

    if (showAddIncomeDialog || editingIncomeIndex != null) {
        val initial = editingIncomeIndex?.let { incomeEntries.getOrNull(it) }
        AddIncomeDialog(
            incomeTypes = incomeTypes,
            onDismiss = {
                showAddIncomeDialog = false
                editingIncomeIndex = null
            },
            onConfirm = { typeId, customTypeName, amount, weight, price, transport, notes ->
                val index = editingIncomeIndex
                if (index != null) {
                    viewModel.updateIncome(index, typeId, amount, weight, price, transport, notes, customTypeName)
                } else {
                    viewModel.addIncome(typeId, amount, weight, price, transport, notes, customTypeName)
                }
                showAddIncomeDialog = false
                editingIncomeIndex = null
            },
            initialIncome = initial
        )
    }

    if (showAddAdvanceDialog || editingAdvanceIndex != null) {
        val initial = editingAdvanceIndex?.let { advanceEntries.getOrNull(it) }
        AddAdvanceDialog(
            onDismiss = {
                showAddAdvanceDialog = false
                editingAdvanceIndex = null
            },
            onConfirm = { amount, reason, recipient ->
                val index = editingAdvanceIndex
                if (index != null) {
                    viewModel.updateAdvanceEntry(index, amount, reason, recipient)
                } else {
                    viewModel.addAdvanceEntry(amount, reason, recipient)
                }
                showAddAdvanceDialog = false
                editingAdvanceIndex = null
            },
            initialAdvance = initial
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun SectionCard(
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = verticalArrangement
        ) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, isTotal: Boolean = false) {
    val style = if (isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = style)
        Text(value, style = style)
    }
}

@Composable
private fun EditRemoveButtons(onEdit: () -> Unit, onRemove: () -> Unit) {
    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
        Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
    }
    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Remove",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.error
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Item rows
// ---------------------------------------------------------------------------------------------

@Composable
private fun WorkerGroupItemRow(group: WorkerGroupEntry, onRemove: () -> Unit, onEdit: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "- ${group.workerTypeName} x ${group.count}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("₹${group.calculateTotalGroupCost()}", style = MaterialTheme.typography.bodyMedium)
                EditRemoveButtons(onEdit = onEdit, onRemove = onRemove)
            }
        }
        if (group.overtimeHours > 0) {
            Text(
                text = "OT: ${group.overtimeHours} hrs @ ₹${group.overtimeWagePerHour}/hr",
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (group.comments.isNotEmpty()) {
            Text(
                text = group.comments,
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ExpenseItemRow(expense: OtherExpenseEntry, onRemove: () -> Unit, onEdit: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        expense.receiptImagePath?.let { path ->
            AsyncImage(
                model = File(path),
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (expense.subtypeName != null) "${expense.typeName} (${expense.subtypeName})" else expense.typeName,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (expense.isPaid) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = PaidGreenColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Paid",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = PaidGreenColor,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Text(
                text = "Qty: ${expense.quantity}",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
            if (expense.notes.isNotEmpty()) {
                Text(expense.notes, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("₹${expense.amount}", style = MaterialTheme.typography.bodyMedium)
            EditRemoveButtons(onEdit = onEdit, onRemove = onRemove)
        }
    }
}

@Composable
private fun IncomeItemRow(income: IncomeEntry, onRemove: () -> Unit, onEdit: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(income.typeName, style = MaterialTheme.typography.bodyMedium)
            Text("${income.weight} kg @ ₹${income.pricePerKilo}/kg", style = MaterialTheme.typography.bodySmall)
            if (income.transportationCharge > BigDecimal.ZERO) {
                Text(
                    text = "Transport: ₹${income.transportationCharge}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (income.notes.isNotEmpty()) {
                Text(income.notes, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("₹${income.amount}", style = MaterialTheme.typography.bodyMedium)
            EditRemoveButtons(onEdit = onEdit, onRemove = onRemove)
        }
    }
}

@Composable
private fun AdvanceItemRow(advance: AdvanceEntry, onRemove: () -> Unit, onEdit: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (advance.recipientName.isNotEmpty()) "To: ${advance.recipientName}" else "Advance",
                style = MaterialTheme.typography.bodyMedium
            )
            if (advance.reason.isNotEmpty()) {
                Text(
                    text = advance.reason,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("₹${advance.amount}", style = MaterialTheme.typography.bodyMedium)
            EditRemoveButtons(onEdit = onEdit, onRemove = onRemove)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Advance dialog
// ---------------------------------------------------------------------------------------------

@Composable
private fun AddAdvanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, String, String) -> Unit,
    initialAdvance: AdvanceEntry? = null
) {
    var amount by remember { mutableStateOf(initialAdvance?.amount?.toString() ?: "") }
    var reason by remember { mutableStateOf(initialAdvance?.reason ?: "") }
    var recipient by remember { mutableStateOf(initialAdvance?.recipientName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialAdvance == null) "Add Advance Payment" else "Edit Advance Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason / Note") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount.toBigDecimalOrNull() ?: BigDecimal.ZERO, reason, recipient) },
                enabled = amount.isNotEmpty()
            ) {
                Text(if (initialAdvance == null) "Add" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Worker group dialog
// ---------------------------------------------------------------------------------------------

/** One editable worker-type row inside [AddWorkerGroupDialog]. */
private data class RowState(
    val id: Int = System.nanoTime().toInt(),
    val workerTypeId: Int,
    val count: TextFieldValue,
    val wage: String,
    val otHours: String,
    val otWage: String,
    val isOtExpanded: Boolean = false
)

private fun defaultRow(workerTypes: List<WorkerType>): RowState = RowState(
    workerTypeId = workerTypes.firstOrNull()?.id ?: 0,
    count = TextFieldValue("0"),
    wage = workerTypes.firstOrNull()?.dailyBasicWage?.toString() ?: "0",
    otHours = "0",
    otWage = "0"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWorkerGroupDialog(
    workerTypes: List<WorkerType>,
    workTasks: List<WorkTask>,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, BigDecimal, Int, BigDecimal, String, String) -> Unit,
    onAddNewTask: (String) -> Unit,
    initialGroup: WorkerGroupEntry? = null
) {
    var rows by remember {
        mutableStateOf(
            if (initialGroup != null) {
                listOf(
                    RowState(
                        workerTypeId = initialGroup.workerTypeId,
                        count = TextFieldValue(initialGroup.count.toString()),
                        wage = initialGroup.wagePerDay.toString(),
                        otHours = initialGroup.overtimeHours.toString(),
                        otWage = initialGroup.overtimeWagePerHour.toString(),
                        isOtExpanded = initialGroup.overtimeHours > 0
                    )
                )
            } else {
                listOf(defaultRow(workerTypes))
            }
        )
    }
    var selectedTask by remember {
        mutableStateOf(initialGroup?.taskPerformed ?: (workTasks.firstOrNull()?.taskName ?: ""))
    }
    var customTask by remember { mutableStateOf("") }
    var isCustomTask by remember {
        mutableStateOf(initialGroup != null && workTasks.none { it.taskName == initialGroup.taskPerformed })
    }
    var comments by remember { mutableStateOf(initialGroup?.comments ?: "") }

    val taskName = if (isCustomTask) customTask else selectedTask
    val canSave = ((isCustomTask && customTask.isNotEmpty()) || (!isCustomTask && selectedTask.isNotEmpty())) &&
        rows.all { it.count.text.toIntOrNull() != null && it.wage.toBigDecimalOrNull() != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialGroup == null) "Add Worker Groups" else "Edit Worker Group") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Task selector
                var taskExpanded by remember { mutableStateOf(false) }
                val taskOptions = workTasks.map { it.taskName } + OTHER_OPTION
                ExposedDropdownMenuBox(
                    expanded = taskExpanded,
                    onExpandedChange = { taskExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (isCustomTask) OTHER_OPTION else selectedTask,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Work Performed") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taskExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = taskExpanded,
                        onDismissRequest = { taskExpanded = false }
                    ) {
                        taskOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    isCustomTask = option == OTHER_OPTION
                                    if (!isCustomTask) selectedTask = option
                                    taskExpanded = false
                                }
                            )
                        }
                    }
                }
                if (isCustomTask) {
                    OutlinedTextField(
                        value = customTask,
                        onValueChange = { customTask = it },
                        label = { Text("Enter New Task Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                rows.forEachIndexed { index, row ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                var typeExpanded by remember { mutableStateOf(false) }
                                val selectedType = workerTypes.find { it.id == row.workerTypeId }
                                ExposedDropdownMenuBox(
                                    expanded = typeExpanded,
                                    onExpandedChange = { typeExpanded = it },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selectedType?.workerTypeName ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Worker Type") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = typeExpanded,
                                        onDismissRequest = { typeExpanded = false }
                                    ) {
                                        workerTypes.forEach { type ->
                                            DropdownMenuItem(
                                                text = { Text(type.workerTypeName) },
                                                onClick = {
                                                    rows = rows.toMutableList().also {
                                                        it[index] = row.copy(
                                                            workerTypeId = type.id,
                                                            wage = type.dailyBasicWage.toString()
                                                        )
                                                    }
                                                    typeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                if (rows.size > 1) {
                                    IconButton(onClick = {
                                        rows = rows.toMutableList().also { it.removeAt(index) }
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove row",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = row.count,
                                    onValueChange = { v ->
                                        if (v.text.all { it.isDigit() }) {
                                            rows = rows.toMutableList().also { it[index] = row.copy(count = v) }
                                        }
                                    },
                                    label = { Text("Count") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { state ->
                                            // Select-all on focus so a tap replaces the default "0"
                                            if (state.isFocused) {
                                                rows = rows.toMutableList().also {
                                                    it[index] = row.copy(
                                                        count = row.count.copy(selection = TextRange(0, row.count.text.length))
                                                    )
                                                }
                                            }
                                        }
                                )
                                OutlinedTextField(
                                    value = row.wage,
                                    onValueChange = { v ->
                                        rows = rows.toMutableList().also { it[index] = row.copy(wage = v) }
                                    },
                                    label = { Text("Daily Wage") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1.5f)
                                )
                            }

                            TextButton(
                                onClick = {
                                    rows = rows.toMutableList().also {
                                        it[index] = row.copy(isOtExpanded = !row.isOtExpanded)
                                    }
                                },
                                modifier = Modifier.align(Alignment.Start),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = if (row.isOtExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (row.isOtExpanded) "Hide OT Details" else "Show OT Details")
                            }

                            AnimatedVisibility(
                                visible = row.isOtExpanded,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = row.otHours,
                                        onValueChange = { v ->
                                            if (v.all { it.isDigit() }) {
                                                rows = rows.toMutableList().also { it[index] = row.copy(otHours = v) }
                                            }
                                        },
                                        label = { Text("OT Hours") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = row.otWage,
                                        onValueChange = { v ->
                                            rows = rows.toMutableList().also { it[index] = row.copy(otWage = v) }
                                        },
                                        label = { Text("OT Rate/hr") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1.5f)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { rows = rows + defaultRow(workerTypes) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Worker Type")
                }

                OutlinedTextField(
                    value = comments,
                    onValueChange = { comments = it },
                    label = { Text("Comments") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCustomTask && customTask.isNotEmpty()) {
                        onAddNewTask(customTask)
                    }
                    rows.forEach { row ->
                        onConfirm(
                            row.workerTypeId,
                            row.count.text.toIntOrNull() ?: 0,
                            row.wage.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                            row.otHours.toIntOrNull() ?: 0,
                            row.otWage.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                            taskName,
                            comments
                        )
                    }
                },
                enabled = canSave
            ) {
                Text(if (initialGroup == null) "Add" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Other expense dialog
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    expenseTypes: List<ExpenseType>,
    expenseSubtypes: List<ExpenseSubtype>,
    onDismiss: () -> Unit,
    onConfirm: (Int, String?, String?, BigDecimal, Double, String, String?) -> Unit,
    initialExpense: OtherExpenseEntry? = null,
    onSaveImage: (Uri) -> String? = { null }
) {
    var selectedTypeId by remember {
        mutableIntStateOf(initialExpense?.expenseTypeId ?: (expenseTypes.firstOrNull()?.id ?: 0))
    }
    var selectedSubtypeName by remember { mutableStateOf(initialExpense?.subtypeName ?: "") }
    var amount by remember { mutableStateOf(initialExpense?.amount?.toString() ?: "") }
    var quantity by remember { mutableStateOf(initialExpense?.quantity?.toString() ?: "1") }
    var notes by remember { mutableStateOf(initialExpense?.notes ?: "") }
    var customTypeName by remember { mutableStateOf("") }
    var customSubtypeName by remember { mutableStateOf("") }
    var isCustomType by remember { mutableStateOf(false) }
    var isCustomSubtype by remember { mutableStateOf(false) }
    var receiptImagePath by remember { mutableStateOf(initialExpense?.receiptImagePath) }

    // Re-sync when editing an existing entry (subtype list may arrive after first composition).
    LaunchedEffect(initialExpense, expenseSubtypes) {
        if (initialExpense != null) {
            selectedTypeId = initialExpense.expenseTypeId
            selectedSubtypeName = initialExpense.subtypeName ?: ""
            amount = initialExpense.amount.toString()
            quantity = initialExpense.quantity.toString()
            notes = initialExpense.notes
            receiptImagePath = initialExpense.receiptImagePath
            isCustomSubtype = initialExpense.subtypeName != null && expenseSubtypes.none {
                it.typeName == initialExpense.subtypeName && it.parentTypeName.id == initialExpense.expenseTypeId
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) receiptImagePath = onSaveImage(uri)
    }

    val subtypesForType = remember(selectedTypeId, expenseSubtypes, expenseTypes) {
        val type = expenseTypes.find { it.id == selectedTypeId }
        if (type == null) emptyList()
        else expenseSubtypes.filter { it.parentTypeName.typeName == type.typeName }
    }

    val canSave = ((isCustomType && customTypeName.isNotEmpty()) || (!isCustomType && selectedTypeId != 0)) &&
        amount.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialExpense == null) "Add Expense" else "Edit Expense") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Receipt image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val path = receiptImagePath
                    if (path != null) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = "Receipt",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { receiptImagePath = null },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove Image",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(48.dp))
                            Text("Add Receipt Image", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Expense type
                var typeExpanded by remember { mutableStateOf(false) }
                val selectedType = expenseTypes.find { it.id == selectedTypeId }
                val typeOptions = expenseTypes.map { it.typeName } + OTHER_OPTION
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (isCustomType) OTHER_OPTION else (selectedType?.typeName ?: ""),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Expense Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    if (option == OTHER_OPTION) {
                                        isCustomType = true
                                        selectedTypeId = 0
                                    } else {
                                        isCustomType = false
                                        selectedTypeId = expenseTypes.find { it.typeName == option }?.id ?: 0
                                    }
                                    selectedSubtypeName = ""
                                    isCustomSubtype = false
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                if (isCustomType) {
                    OutlinedTextField(
                        value = customTypeName,
                        onValueChange = { customTypeName = it },
                        label = { Text("Enter New Expense Type") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Expense subtype
                if (!isCustomType || customTypeName.isNotEmpty()) {
                    var subtypeExpanded by remember { mutableStateOf(false) }
                    val subtypeOptions = subtypesForType.map { it.typeName } + OTHER_OPTION
                    ExposedDropdownMenuBox(
                        expanded = subtypeExpanded,
                        onExpandedChange = { subtypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (isCustomSubtype) OTHER_OPTION else selectedSubtypeName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Expense Subtype (Optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subtypeExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = subtypeExpanded,
                            onDismissRequest = { subtypeExpanded = false }
                        ) {
                            subtypeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        if (option == OTHER_OPTION) {
                                            isCustomSubtype = true
                                        } else {
                                            isCustomSubtype = false
                                            selectedSubtypeName = option
                                        }
                                        subtypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (isCustomSubtype) {
                        OutlinedTextField(
                            value = customSubtypeName,
                            onValueChange = { customSubtypeName = it },
                            label = { Text("Enter New Expense Subtype") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val quantityValue = quantity.toDoubleOrNull() ?: 1.0
                    val subtype = when {
                        isCustomSubtype -> customSubtypeName
                        selectedSubtypeName.isNotEmpty() -> selectedSubtypeName
                        else -> null
                    }
                    if (isCustomType && customTypeName.isNotEmpty()) {
                        onConfirm(0, customTypeName, subtype, amountValue, quantityValue, notes, receiptImagePath)
                    } else {
                        onConfirm(selectedTypeId, null, subtype, amountValue, quantityValue, notes, receiptImagePath)
                    }
                },
                enabled = canSave
            ) {
                Text(if (initialExpense == null) "Add" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Income dialog
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIncomeDialog(
    incomeTypes: List<IncomeType>,
    onDismiss: () -> Unit,
    onConfirm: (Int, String?, BigDecimal, Double, BigDecimal, BigDecimal, String) -> Unit,
    initialIncome: IncomeEntry? = null
) {
    var selectedTypeId by remember {
        mutableIntStateOf(initialIncome?.incomeTypeId ?: (incomeTypes.firstOrNull()?.id ?: 0))
    }
    var amount by remember { mutableStateOf(initialIncome?.amount?.toString() ?: "") }
    var weight by remember { mutableStateOf(initialIncome?.weight?.toString() ?: "") }
    var transportCharge by remember { mutableStateOf(initialIncome?.transportationCharge?.toString() ?: "0") }
    var notes by remember { mutableStateOf(initialIncome?.notes ?: "") }
    var customTypeName by remember { mutableStateOf("") }
    var isCustomType by remember { mutableStateOf(false) }

    val netIncome = remember(amount, transportCharge) {
        (amount.toBigDecimalOrNull() ?: BigDecimal.ZERO)
            .subtract(transportCharge.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    }
    val pricePerKilo = remember(amount, weight) {
        val amountValue = amount.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val weightValue = weight.toDoubleOrNull() ?: 0.0
        if (weightValue > 0.0) amountValue.divide(BigDecimal(weightValue.toString()), 2, RoundingMode.HALF_UP)
        else BigDecimal.ZERO
    }

    val canSave = ((isCustomType && customTypeName.isNotEmpty()) || (!isCustomType && selectedTypeId != 0)) &&
        amount.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialIncome == null) "Add Income" else "Edit Income") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var typeExpanded by remember { mutableStateOf(false) }
                val selectedType = incomeTypes.find { it.id == selectedTypeId }
                val typeOptions = incomeTypes.map { it.typeName } + OTHER_OPTION
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (isCustomType) OTHER_OPTION else (selectedType?.typeName ?: ""),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Income Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        typeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    if (option == OTHER_OPTION) {
                                        isCustomType = true
                                    } else {
                                        isCustomType = false
                                        selectedTypeId = incomeTypes.find { it.typeName == option }?.id ?: 0
                                    }
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                if (isCustomType) {
                    OutlinedTextField(
                        value = customTypeName,
                        onValueChange = { customTypeName = it },
                        label = { Text("Enter New Income Type") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = transportCharge,
                    onValueChange = { transportCharge = it },
                    label = { Text("Transportation Charge (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Net Income:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "₹$netIncome",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val transport = transportCharge.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val weightValue = weight.toDoubleOrNull() ?: 0.0
                    if (isCustomType && customTypeName.isNotEmpty()) {
                        onConfirm(0, customTypeName, netIncome, weightValue, pricePerKilo, transport, notes)
                    } else {
                        onConfirm(selectedTypeId, null, netIncome, weightValue, pricePerKilo, transport, notes)
                    }
                },
                enabled = canSave
            ) {
                Text(if (initialIncome == null) "Add" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
