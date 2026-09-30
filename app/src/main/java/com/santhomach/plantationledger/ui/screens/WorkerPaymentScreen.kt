package com.santhomach.plantationledger.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.WorkerPayment
import com.santhomach.plantationledger.ui.viewmodel.WorkerPaymentViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Records monthly / weekly / advance / bonus payments made to managers and permanent workers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerPaymentScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: WorkerPaymentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val workers by viewModel.permanentWorkers.collectAsState()
    val payments by viewModel.allPayments.collectAsState()

    var showRecordDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var paymentToEdit by remember { mutableStateOf<WorkerPayment?>(null) }
    var paymentToDelete by remember { mutableStateOf<WorkerPayment?>(null) }

    LaunchedEffect(uiState.successMessage, uiState.error) {
        if (uiState.successMessage != null || uiState.error != null) {
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Worker Payments") },
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showRecordDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Record Payment")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Recent Payments",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            itemsIndexed(payments, key = { _, payment -> payment.id }) { index, payment ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(300)) +
                        slideInVertically(animationSpec = tween(300, delayMillis = index * 50)) { 50 },
                    exit = fadeOut(animationSpec = tween(200))
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = payment.workerName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "₹${payment.amount}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = payment.paymentType,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = LocalDate.parse(payment.paymentDate)
                                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    paymentToEdit = payment
                                    showEditDialog = true
                                }) {
                                    Text("Edit")
                                }
                                TextButton(
                                    onClick = {
                                        paymentToDelete = payment
                                        showDeleteDialog = true
                                    },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }

            if (payments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No payment records found")
                    }
                }
            }
        }
    }

    if (showRecordDialog) {
        RecordPaymentDialog(
            workers = workers,
            onDismiss = { showRecordDialog = false },
            onConfirm = { workerId, amount, date, type, notes, id ->
                if (id == 0) {
                    viewModel.recordPayment(workerId, amount, date, type, notes)
                } else {
                    viewModel.updatePayment(id, workerId, amount, date, type, notes)
                }
                showRecordDialog = false
            },
            onCheckExisting = { workerId, date -> viewModel.getExistingPayment(workerId, date) }
        )
    }

    if (showEditDialog && paymentToEdit != null) {
        val payment = paymentToEdit!!
        RecordPaymentDialog(
            workers = workers,
            onDismiss = {
                showEditDialog = false
                paymentToEdit = null
            },
            onConfirm = { workerId, amount, date, type, notes, _ ->
                viewModel.updatePayment(payment.id, workerId, amount, date, type, notes)
                showEditDialog = false
                paymentToEdit = null
            },
            initialWorkerId = payment.workerId,
            initialAmount = payment.amount.toString(),
            initialDate = LocalDate.parse(payment.paymentDate),
            initialType = payment.paymentType,
            initialNotes = payment.notes,
            initialId = payment.id
        )
    }

    if (showDeleteDialog && paymentToDelete != null) {
        val payment = paymentToDelete!!
        val dateLabel = LocalDate.parse(payment.paymentDate)
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                paymentToDelete = null
            },
            title = { Text("Delete Payment") },
            text = {
                Text("Are you sure you want to delete the payment record for ${payment.workerName} (${payment.amount}₹ on $dateLabel)?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(payment.id)
                        showDeleteDialog = false
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    paymentToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordPaymentDialog(
    workers: List<PermanentWorker>,
    onDismiss: () -> Unit,
    onConfirm: (Int, BigDecimal, LocalDate, String, String, Int) -> Unit,
    initialWorkerId: Int = 0,
    initialAmount: String = "",
    initialDate: LocalDate = LocalDate.now(),
    initialType: String = "MONTHLY",
    initialNotes: String = "",
    initialId: Int = 0,
    onCheckExisting: suspend (Int, LocalDate) -> WorkerPayment? = { _, _ -> null }
) {
    var paymentId by remember { mutableIntStateOf(initialId) }
    var selectedWorkerId by remember { mutableIntStateOf(initialWorkerId) }
    var amount by remember { mutableStateOf(initialAmount) }
    var date by remember { mutableStateOf(initialDate) }
    var paymentType by remember { mutableStateOf(initialType) }
    var notes by remember { mutableStateOf(initialNotes) }
    var showDatePicker by remember { mutableStateOf(false) }

    // When the worker or date changes, pre-fill from an existing payment on that day (if any).
    LaunchedEffect(selectedWorkerId, date) {
        if (selectedWorkerId > 0) {
            val existing = onCheckExisting(selectedWorkerId, date)
            if (existing != null && existing.id != paymentId) {
                paymentId = existing.id
                amount = existing.amount.toString()
                paymentType = existing.paymentType
                notes = existing.notes
            } else if (existing == null && paymentId != 0 && initialId == 0) {
                paymentId = 0
                amount = ""
                notes = ""
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (paymentId == 0) "Record Payment" else "Edit Payment") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Worker selector
                var workerExpanded by remember { mutableStateOf(false) }
                val selectedWorker = workers.find { it.id == selectedWorkerId }
                ExposedDropdownMenuBox(
                    expanded = workerExpanded,
                    onExpandedChange = { workerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedWorker?.name ?: "Select Worker",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Worker / Manager") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = workerExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = workerExpanded,
                        onDismissRequest = { workerExpanded = false }
                    ) {
                        workers.forEach { worker ->
                            DropdownMenuItem(
                                text = { Text("${worker.name} (${worker.role})") },
                                onClick = {
                                    selectedWorkerId = worker.id
                                    workerExpanded = false
                                }
                            )
                        }
                    }
                }

                // Date
                OutlinedTextField(
                    value = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = "Select Date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // Payment type
                var typeExpanded by remember { mutableStateOf(false) }
                val paymentTypes = listOf("MONTHLY", "WEEKLY", "ADVANCE", "BONUS")
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = paymentType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        paymentTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    paymentType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Notes
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
                    onConfirm(
                        selectedWorkerId,
                        amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        date,
                        paymentType,
                        notes,
                        paymentId
                    )
                },
                enabled = selectedWorkerId > 0 && amount.isNotEmpty()
            ) {
                Text(if (paymentId == 0) "Save" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
