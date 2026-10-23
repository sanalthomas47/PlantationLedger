package com.santhomach.plantationledger.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.ui.viewmodel.CombinationCardViewModel

/**
 * One fertilizer / foliar / pesticide combination: name, purpose, combination, usage and comments.
 * Saved to the database on Save; leaving with unsaved changes asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombinationCardScreen(
    onNavigateBack: () -> Unit,
    viewModel: CombinationCardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.isDone) { if (state.isDone) onNavigateBack() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }

    fun leave() {
        if (state.hasChanges) confirmDiscard = true else onNavigateBack()
    }
    BackHandler(enabled = state.hasChanges) { confirmDiscard = true }

    val title = state.category?.singular?.replaceFirstChar { it.uppercase() } ?: "Combination"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.card != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete card")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.notFound -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("This card no longer exists.", color = MaterialTheme.colorScheme.onSurfaceVariant) }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val form = state.form
                Field("Combination group name *", form.name, singleLine = true,
                    isError = form.name.isBlank()) { v -> viewModel.updateForm { it.copy(name = v) } }
                Field("Purpose", form.purpose, singleLine = false, minLines = 2,
                    placeholder = state.category?.purposeHint) { v -> viewModel.updateForm { it.copy(purpose = v) } }
                Field("Combination", form.combination, minLines = 4,
                    placeholder = "e.g. MOP 1 kg + Urea 500 g per 200 L water") { v -> viewModel.updateForm { it.copy(combination = v) } }
                Field("Usage", form.usage, minLines = 3,
                    placeholder = "e.g. Dose per plant, method, interval") { v -> viewModel.updateForm { it.copy(usage = v) } }
                Field("Comments", form.comments, minLines = 3) { v -> viewModel.updateForm { it.copy(comments = v) } }

                Button(
                    onClick = viewModel::save,
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.isSaving) "Saving..." else "Save") }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this card have not been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onNavigateBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                Button(onClick = {
                    confirmDiscard = false
                    viewModel.save()
                }, enabled = state.canSave) { Text("Save") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this card?") },
            text = { Text("\"${state.card?.name}\" will be deleted. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    placeholder: String? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        isError = isError,
        modifier = Modifier.fillMaxWidth()
    )
}
