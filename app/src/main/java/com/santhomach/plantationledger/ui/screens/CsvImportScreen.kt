package com.santhomach.plantationledger.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.ui.viewmodel.CsvImportViewModel

/**
 * Imports historical records from a CSV file (see [CsvImportViewModel] for the format).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showErrors by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from CSV") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
            // ---- Instructions card ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("How to use", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Save the template below and open it in Google Sheets\n" +
                                "2. Enter your paper records — one row per entry per day\n" +
                                "3. Save as CSV (File → Download → CSV), then import here",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Column guide", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        CsvFormatRow("LABOR", "Worker type name, count, rate (daily wage)")
                        CsvFormatRow("EXPENSE", "Expense category name, amount")
                        CsvFormatRow("INCOME", "Income category name, amount")
                        CsvFormatRow("ADVANCE", "Recipient name, amount")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { viewModel.saveTemplate() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Template to Device")
                        }
                        AnimatedVisibility(visible = uiState.templateSaved) {
                            Column(modifier = Modifier.padding(top = 6.dp)) {
                                Text(
                                    text = "Template saved.",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = uiState.templatePath,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // ---- Import card ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Import", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (!uiState.isDone) {
                            Button(
                                onClick = { filePicker.launch("text/*") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isLoading
                            ) {
                                Text(if (uiState.isLoading) "Importing…" else "Choose CSV File")
                            }
                            AnimatedVisibility(visible = uiState.isLoading) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    LinearProgressIndicator(
                                        progress = {
                                            if (uiState.total > 0) uiState.progress.toFloat() / uiState.total else 0f
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${uiState.importedCount} days saved  •  ${uiState.progress}/${uiState.total} rows",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${uiState.importedCount} days imported successfully",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (uiState.skippedCount > 0) {
                                Row(
                                    modifier = Modifier.padding(top = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${uiState.skippedCount} days skipped due to errors",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.reset() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Import Another File")
                            }
                        }
                    }
                }
            }

            // ---- Errors card ----
            if (uiState.errors.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.errors.size} warnings / issues",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                TextButton(onClick = { showErrors = !showErrors }) {
                                    Text(if (showErrors) "Hide" else "Show")
                                }
                            }
                            AnimatedVisibility(visible = showErrors) {
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    uiState.errors.forEach { error ->
                                        Text(
                                            text = "• $error",
                                            modifier = Modifier.padding(top = 2.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.bodySmall
                                        )
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

@Composable
private fun CsvFormatRow(type: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = type,
            modifier = Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = description,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
