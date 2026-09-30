package com.santhomach.plantationledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.ui.viewmodel.WeeklyFundsViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tracks the cash received each week (and Thursday payments made) against the
 * actual expenses recorded for that week.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyFundsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToReports: (LocalDate, LocalDate) -> Unit = { _, _ -> },
    viewModel: WeeklyFundsViewModel = hiltViewModel()
) {
    val weeklyFunds by viewModel.weeklyFunds.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedFundsForEdit by remember { mutableStateOf<WeeklyFunds?>(null) }
    var selectedFundsForDelete by remember { mutableStateOf<WeeklyFunds?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Funds Tracking") },
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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add Funds")
            }
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
                Text(
                    text = "Weekly Funds vs Actual Expenses",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(weeklyFunds, key = { it.id }) { funds ->
                val weekStart = LocalDate.parse(funds.weekStartDate)
                val comparison by viewModel.getComparisonFlow(weekStart).collectAsState(initial = null)

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Week of ${weekStart.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        SummaryRow("Funds Received", "₹${funds.amountReceived}")
                        if (funds.paymentMade > BigDecimal.ZERO) {
                            SummaryRow("Payment Made", "₹${funds.paymentMade}")
                        }

                        val comp = comparison
                        if (comp != null) {
                            SummaryRow("Total Expenses", "₹${comp.totalExpenses}")
                            val balance = comp.totalPayments.subtract(comp.totalExpenses)
                            if (comp.totalPayments > BigDecimal.ZERO) {
                                SummaryRow("Actual Payments Made", "₹${comp.totalPayments}")
                            }
                            SummaryRow(
                                label = if (balance >= BigDecimal.ZERO) "Excess Balance" else "Shortfall",
                                value = "₹${balance.abs()}",
                                isPositive = balance >= BigDecimal.ZERO
                            )
                            if (balance > BigDecimal.ZERO) {
                                Text(
                                    text = "This excess will carry over to next week",
                                    modifier = Modifier.padding(top = 4.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        }

                        if (funds.notes.isNotEmpty()) {
                            Text(
                                text = "Notes: ${funds.notes}",
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onNavigateToReports(weekStart, weekStart.plusDays(5)) }) {
                                Text("View Summary")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                selectedFundsForEdit = funds
                                showEditDialog = true
                            }) {
                                Text("Edit")
                            }
                            TextButton(
                                onClick = {
                                    selectedFundsForDelete = funds
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
    }

    if (showAddDialog) {
        AddFundsDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, paymentMade, date, notes ->
                viewModel.addFunds(amount, paymentMade, date, notes)
                showAddDialog = false
            }
        )
    }

    if (showEditDialog && selectedFundsForEdit != null) {
        val funds = selectedFundsForEdit!!
        AddFundsDialog(
            onDismiss = {
                showEditDialog = false
                selectedFundsForEdit = null
            },
            onConfirm = { amount, paymentMade, _, notes ->
                viewModel.updateFunds(funds.id, amount, paymentMade, notes)
                showEditDialog = false
                selectedFundsForEdit = null
            },
            initialAmount = funds.amountReceived.toString(),
            initialPaymentMade = funds.paymentMade.toString(),
            initialDate = LocalDate.parse(funds.weekStartDate),
            initialNotes = funds.notes
        )
    }

    if (showDeleteDialog && selectedFundsForDelete != null) {
        val funds = selectedFundsForDelete!!
        val weekLabel = LocalDate.parse(funds.weekStartDate)
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                selectedFundsForDelete = null
            },
            title = { Text("Delete Funds Record") },
            text = { Text("Are you sure you want to delete the funds record for the week of $weekLabel?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFunds(funds)
                        showDeleteDialog = false
                        selectedFundsForDelete = null
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
                    selectedFundsForDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddFundsDialog(
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, BigDecimal, LocalDate, String) -> Unit,
    initialAmount: String = "",
    initialPaymentMade: String = "",
    initialDate: LocalDate = LocalDate.now(),
    initialNotes: String = ""
) {
    var amount by remember { mutableStateOf(initialAmount) }
    var paymentMade by remember { mutableStateOf(initialPaymentMade) }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var notes by remember { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Funds Received") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Received (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = paymentMade,
                    onValueChange = { paymentMade = it },
                    label = { Text("Payment Made (₹) - Thursday") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Select any day in the week. App will automatically map it to the start of that week (Monday).",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall
                )
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
                        amount.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        paymentMade.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                        selectedDate,
                        notes
                    )
                },
                enabled = amount.isNotEmpty()
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

@Composable
private fun SummaryRow(label: String, value: String, isPositive: Boolean? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            color = when (isPositive) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
