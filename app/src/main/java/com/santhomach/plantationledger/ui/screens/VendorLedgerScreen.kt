package com.santhomach.plantationledger.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.ui.viewmodel.ReportsViewModel
import com.santhomach.plantationledger.ui.viewmodel.VendorExpenseItem
import com.santhomach.plantationledger.ui.viewmodel.VendorLedger
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val PaidGreen = Color(0xFF2E7D32)

/**
 * Pesticide / fertilizer purchases with their paid status, and a dialog to mark a bill as paid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorLedgerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val ledger by viewModel.vendorLedger.collectAsState()
    var itemToMarkPaid by remember { mutableStateOf<VendorExpenseItem?>(null) }

    itemToMarkPaid?.let { item ->
        MarkVendorPaidDialog(
            item = item,
            onDismiss = { itemToMarkPaid = null },
            onConfirm = { notes, date ->
                viewModel.markVendorBillPaid(item, notes, date)
                itemToMarkPaid = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vendor Ledger", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (ledger.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pesticide or fertilizer expenses recorded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { VendorLedgerSummaryCard(ledger) }
                items(ledger.items) { item ->
                    VendorLedgerItemRow(
                        item = item,
                        onMarkPaid = if (item.isPaid) null else ({ itemToMarkPaid = item })
                    )
                }
            }
        }
    }
}

@Composable
private fun VendorLedgerSummaryCard(ledger: VendorLedger) {
    val allPaid = ledger.outstanding.signum() <= 0 && ledger.totalExpenses.signum() > 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allPaid) PaidGreen.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryFigure("Total Bills", "₹${ledger.totalExpenses}")
            SummaryFigure("Paid", "₹${ledger.totalPaid}", PaidGreen)
            SummaryFigure(
                "Outstanding",
                "₹${ledger.outstanding}",
                if (allPaid) PaidGreen else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun VendorLedgerItemRow(item: VendorExpenseItem, onMarkPaid: (() -> Unit)?) {
    val paidColor = PaidGreen
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPaid) paidColor.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isPaid) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (item.isPaid) paidColor else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                val date = try {
                    LocalDate.parse(item.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                } catch (e: Exception) {
                    item.date
                }
                val typeLabel = if (item.subtypeName != null) "${item.typeName} (${item.subtypeName})" else item.typeName
                Text(
                    text = "$date · $typeLabel",
                    color = if (item.isPaid) paidColor else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (item.notes.isNotEmpty()) {
                    Text(
                        text = item.notes,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = "₹${item.amount}",
                color = if (item.isPaid) paidColor else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge
            )
            if (onMarkPaid != null) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onMarkPaid,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Mark Paid", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MarkVendorPaidDialog(
    item: VendorExpenseItem,
    onDismiss: () -> Unit,
    onConfirm: (String, LocalDate) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    val parsedDate = remember(dateText) {
        try {
            LocalDate.parse(dateText)
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { parsedDate?.let { onConfirm(notes.trim(), it) } },
                enabled = parsedDate != null
            ) {
                Text("Confirm Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Mark as Paid") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val typeLabel = if (item.subtypeName != null) "${item.typeName} (${item.subtypeName})" else item.typeName
                val date = try {
                    LocalDate.parse(item.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                } catch (e: Exception) {
                    item.date
                }
                Text(
                    text = "$date · $typeLabel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Amount: ₹${item.amount}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Payment Date (YYYY-MM-DD)") },
                    isError = parsedDate == null,
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes (optional)") },
                    singleLine = true
                )
            }
        }
    )
}
