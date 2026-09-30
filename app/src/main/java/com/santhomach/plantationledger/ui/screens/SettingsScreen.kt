package com.santhomach.plantationledger.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.ui.viewmodel.SettingsViewModel
import com.santhomach.plantationledger.ui.viewmodel.ThemeMode
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Master-data management (staff, worker types, tasks, categories), theme selection and
 * JSON export / import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToCsvImport: () -> Unit = {},
    viewModelArg: SettingsViewModel? = null
) {
    // Keep @Preview usable without Hilt.
    if (LocalInspectionMode.current && viewModelArg == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Settings Screen Preview")
        }
        return
    }
    val viewModel: SettingsViewModel = viewModelArg ?: hiltViewModel()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch { viewModel.importData(uri, context) }
        }
    }

    val themeMode by viewModel.themeMode.collectAsState()
    val permanentWorkers by viewModel.permanentWorkers.collectAsState()
    val workerTypes by viewModel.workerTypes.collectAsState()
    val expenseTypes by viewModel.expenseTypes.collectAsState()
    val expenseSubtypes by viewModel.expenseSubtypes.collectAsState()
    val incomeTypes by viewModel.incomeTypes.collectAsState()
    val workTasks by viewModel.workTasks.collectAsState()

    var showAddWorkerDialog by remember { mutableStateOf(false) }
    var showAddWorkerTypeDialog by remember { mutableStateOf(false) }
    var showAddExpenseTypeDialog by remember { mutableStateOf(false) }
    var showAddIncomeTypeDialog by remember { mutableStateOf(false) }
    var showAddWorkTaskDialog by remember { mutableStateOf(false) }

    var editingWorker by remember { mutableStateOf<PermanentWorker?>(null) }
    var editingWorkerType by remember { mutableStateOf<WorkerType?>(null) }
    var editingExpenseType by remember { mutableStateOf<ExpenseType?>(null) }
    var editingIncomeType by remember { mutableStateOf<IncomeType?>(null) }
    var editingWorkTask by remember { mutableStateOf<WorkTask?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Reserved */ }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
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
            item {
                SettingsCategoryCard(
                    title = "Manage Managers / Staff",
                    items = permanentWorkers.map { "${it.name} (${it.role})" },
                    onAddClick = { showAddWorkerDialog = true },
                    onEditClick = { index -> editingWorker = permanentWorkers[index] },
                    onDeleteClick = { index -> viewModel.deletePermanentWorker(permanentWorkers[index]) }
                )
            }

            item {
                SettingsCategoryCard(
                    title = "Worker Types & Wages",
                    items = workerTypes.map { "${it.workerTypeName}: ₹${it.dailyBasicWage}" },
                    onAddClick = { showAddWorkerTypeDialog = true },
                    onEditClick = { index -> editingWorkerType = workerTypes[index] },
                    onDeleteClick = { index -> viewModel.deleteWorkerType(workerTypes[index]) }
                )
            }

            item {
                SettingsCategoryCard(
                    title = "Work Tasks",
                    items = workTasks.map { it.taskName },
                    onAddClick = { showAddWorkTaskDialog = true },
                    onEditClick = { index -> editingWorkTask = workTasks[index] },
                    onDeleteClick = { index -> viewModel.deleteWorkTask(workTasks[index]) }
                )
            }

            item {
                SettingsCategoryCard(
                    title = "Expense Categories",
                    items = expenseTypes.map { it.typeName },
                    onAddClick = { showAddExpenseTypeDialog = true },
                    onEditClick = { index -> editingExpenseType = expenseTypes[index] },
                    onDeleteClick = { index -> viewModel.deleteExpenseType(expenseTypes[index]) }
                )
            }

            item {
                SettingsCategoryCard(
                    title = "Expense Sub-categories",
                    items = expenseSubtypes.map { "${it.typeName} (${it.parentTypeName.typeName})" },
                    onAddClick = { /* Sub-categories are created from the expense dialog */ },
                    onDeleteClick = { index -> viewModel.deleteExpenseSubtype(expenseSubtypes[index]) }
                )
            }

            item {
                SettingsCategoryCard(
                    title = "Income Categories",
                    items = incomeTypes.map { it.typeName },
                    onAddClick = { showAddIncomeTypeDialog = true },
                    onEditClick = { index -> editingIncomeType = incomeTypes[index] },
                    onDeleteClick = { index -> viewModel.deleteIncomeType(incomeTypes[index]) }
                )
            }

            // Appearance
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Appearance",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "App theme",
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size
                                    ),
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                    }
                }
            }

            // Data management
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Data Management",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedButton(
                            onClick = { scope.launch { viewModel.exportData(context) } },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isExporting
                        ) {
                            Text(if (uiState.isExporting) "Exporting..." else "Export Data to JSON")
                        }

                        uiState.exportMessage?.let { message ->
                            Text(
                                text = message,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { importLauncher.launch("application/json") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isExporting
                        ) {
                            Text(if (uiState.isExporting) "Importing..." else "Import Data from JSON")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onNavigateToCsvImport,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import from CSV (Bulk Backlog)")
                        }
                    }
                }
            }

            // About
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "About",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Plantation Ledger v1.0",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Track daily expenses and income for your cardamom/pepper plantation operations.",
                            modifier = Modifier.padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            uiState.error?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
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
    }

    // ---- Dialogs ----

    if (showAddWorkerDialog || editingWorker != null) {
        AddWorkerDialog(
            initialWorker = editingWorker,
            onDismiss = {
                showAddWorkerDialog = false
                editingWorker = null
            },
            onConfirm = { name, role, wage ->
                val existing = editingWorker
                if (existing != null) {
                    viewModel.updatePermanentWorker(existing.copy(name = name, role = role, dailyBasicWage = wage))
                } else {
                    viewModel.addPermanentWorker(name, role, wage)
                }
                showAddWorkerDialog = false
                editingWorker = null
            }
        )
    }

    if (showAddWorkerTypeDialog || editingWorkerType != null) {
        AddSimpleItemDialog(
            title = if (editingWorkerType == null) "Add Worker Type" else "Edit Worker Type",
            label = "Worker Type Name",
            hasWage = true,
            initialName = editingWorkerType?.workerTypeName ?: "",
            initialWage = editingWorkerType?.dailyBasicWage?.toString() ?: "0",
            onDismiss = {
                showAddWorkerTypeDialog = false
                editingWorkerType = null
            },
            onConfirm = { name, wage ->
                val existing = editingWorkerType
                if (existing != null) {
                    viewModel.updateWorkerType(existing.copy(workerTypeName = name, dailyBasicWage = wage))
                } else {
                    viewModel.addWorkerType(name, wage)
                }
                showAddWorkerTypeDialog = false
                editingWorkerType = null
            }
        )
    }

    if (showAddExpenseTypeDialog || editingExpenseType != null) {
        AddSimpleItemDialog(
            title = if (editingExpenseType == null) "Add Expense Category" else "Edit Expense Category",
            label = "Category Name",
            initialName = editingExpenseType?.typeName ?: "",
            onDismiss = {
                showAddExpenseTypeDialog = false
                editingExpenseType = null
            },
            onConfirm = { name, _ ->
                val existing = editingExpenseType
                if (existing != null) {
                    viewModel.updateExpenseType(existing.copy(typeName = name))
                } else {
                    viewModel.addExpenseType(name)
                }
                showAddExpenseTypeDialog = false
                editingExpenseType = null
            }
        )
    }

    if (showAddIncomeTypeDialog || editingIncomeType != null) {
        AddSimpleItemDialog(
            title = if (editingIncomeType == null) "Add Income Category" else "Edit Income Category",
            label = "Category Name",
            initialName = editingIncomeType?.typeName ?: "",
            onDismiss = {
                showAddIncomeTypeDialog = false
                editingIncomeType = null
            },
            onConfirm = { name, _ ->
                val existing = editingIncomeType
                if (existing != null) {
                    viewModel.updateIncomeType(existing.copy(typeName = name))
                } else {
                    viewModel.addIncomeType(name)
                }
                showAddIncomeTypeDialog = false
                editingIncomeType = null
            }
        )
    }

    if (showAddWorkTaskDialog || editingWorkTask != null) {
        AddSimpleItemDialog(
            title = if (editingWorkTask == null) "Add Work Task" else "Edit Work Task",
            label = "Task Name",
            initialName = editingWorkTask?.taskName ?: "",
            onDismiss = {
                showAddWorkTaskDialog = false
                editingWorkTask = null
            },
            onConfirm = { name, _ ->
                val existing = editingWorkTask
                if (existing != null) {
                    viewModel.updateWorkTask(existing.copy(taskName = name))
                } else {
                    viewModel.addWorkTask(name)
                }
                showAddWorkTaskDialog = false
                editingWorkTask = null
            }
        )
    }
}

/**
 * Collapsible card listing master-data items with add / edit / delete actions.
 */
@Composable
fun SettingsCategoryCard(
    title: String,
    items: List<String>,
    onAddClick: () -> Unit,
    onEditClick: (Int) -> Unit = {},
    onDeleteClick: (Int) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                        else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (items.isEmpty()) {
                        Text(
                            text = "None configured",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row {
                                    TextButton(onClick = { onEditClick(index) }) {
                                        Text("Edit", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                    }
                                    TextButton(
                                        onClick = { onDeleteClick(index) },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Delete", fontSize = MaterialTheme.typography.labelSmall.fontSize)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkerDialog(
    initialWorker: PermanentWorker? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, BigDecimal) -> Unit
) {
    var name by remember { mutableStateOf(initialWorker?.name ?: "") }
    var role by remember { mutableStateOf(initialWorker?.role ?: "Manager") }
    var wage by remember { mutableStateOf(initialWorker?.dailyBasicWage?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialWorker == null) "Add Manager / Permanent Worker" else "Edit Manager / Permanent Worker")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                var roleExpanded by remember { mutableStateOf(false) }
                val roles = listOf("Manager", "Permanent Labor", "Specialist")
                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = it }
                ) {
                    OutlinedTextField(
                        value = role,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roles.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    role = option
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = wage,
                    onValueChange = { wage = it },
                    label = { Text("Daily/Basic Wage (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, role, wage.toBigDecimalOrNull() ?: BigDecimal.ZERO) },
                enabled = name.isNotEmpty()
            ) {
                Text(if (initialWorker == null) "Add" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddSimpleItemDialog(
    title: String,
    label: String,
    hasWage: Boolean = false,
    initialName: String = "",
    initialWage: String = "0",
    onDismiss: () -> Unit,
    onConfirm: (String, BigDecimal) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var wage by remember { mutableStateOf(initialWage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasWage) {
                    OutlinedTextField(
                        value = wage,
                        onValueChange = { wage = it },
                        label = { Text("Basic Daily Wage (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, wage.toBigDecimalOrNull() ?: BigDecimal.ZERO) },
                enabled = name.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
