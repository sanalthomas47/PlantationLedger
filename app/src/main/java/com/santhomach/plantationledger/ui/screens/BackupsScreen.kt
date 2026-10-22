package com.santhomach.plantationledger.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.santhomach.plantationledger.data.backup.BackupFile
import com.santhomach.plantationledger.data.backup.BackupKind
import com.santhomach.plantationledger.ui.viewmodel.BackupsUiState
import com.santhomach.plantationledger.ui.viewmodel.BackupsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exported backups (Downloads) and automatic daily backups: share, restore, and delete older ones.
 * The newest backup of each kind cannot be deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var confirmDelete by remember { mutableStateOf<List<BackupFile>?>(null) }
    var keepNewestFor by remember { mutableStateOf<BackupKind?>(null) }
    var confirmRestore by remember { mutableStateOf<BackupFile?>(null) }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.clearMessages()
        }
    }

    fun share(backup: BackupFile) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, viewModel.shareUri(backup))
            putExtra(Intent.EXTRA_SUBJECT, backup.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share backup"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.selected.isEmpty()) "Backups" else "${state.selected.size} selected")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.selected.isEmpty()) onNavigateBack() else viewModel.clearSelection()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.selected.isNotEmpty()) {
                        IconButton(
                            onClick = { confirmDelete = viewModel.selectedBackups() },
                            enabled = !state.isWorking
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding)) {
            if (state.isWorking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Tap backups to select them for deletion. The newest of each kind is always kept.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                listOf(BackupKind.EXPORT, BackupKind.AUTO).forEach { kind ->
                    item(key = "section_$kind") {
                        BackupSection(
                            kind = kind,
                            state = state,
                            onToggle = viewModel::toggleSelected,
                            onShare = ::share,
                            onRestore = { confirmRestore = it },
                            onDelete = { confirmDelete = listOf(it) },
                            onKeepNewest = { keepNewestFor = kind }
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { backups ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${backups.size} backup${if (backups.size == 1) "" else "s"}?") },
            text = {
                Text(
                    backups.take(6).joinToString("\n") { "• ${formatTime(it.createdAtMillis)}  (${kindLabel(it.kind)})" } +
                        (if (backups.size > 6) "\n… and ${backups.size - 6} more" else "") +
                        "\n\nDeleted backups cannot be recovered."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(backups)
                        confirmDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }

    keepNewestFor?.let { kind ->
        KeepNewestDialog(
            kind = kind,
            total = state.backupsOf(kind).size,
            toDelete = { keep -> viewModel.olderThanNewest(kind, keep).size },
            onDismiss = { keepNewestFor = null },
            onConfirm = { keep ->
                viewModel.keepNewest(kind, keep)
                keepNewestFor = null
            }
        )
    }

    confirmRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "${formatTime(backup.createdAtMillis)} (${kindLabel(backup.kind)})\n\n" +
                        "Your current data is exported to Downloads first, so nothing is lost.\n\n" +
                        "Every record in the backup replaces the same record in the app. " +
                        "Entries added after the backup was made are kept."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.restore(backup)
                    confirmRestore = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BackupSection(
    kind: BackupKind,
    state: BackupsUiState,
    onToggle: (BackupFile) -> Unit,
    onShare: (BackupFile) -> Unit,
    onRestore: (BackupFile) -> Unit,
    onDelete: (BackupFile) -> Unit,
    onKeepNewest: () -> Unit
) {
    val backups = state.backupsOf(kind)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${kindTitle(kind)} (${backups.size})",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (kind == BackupKind.EXPORT) "Saved in the Downloads folder"
                        else "Made automatically each day, private to this app; the last 7 are kept",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                TextButton(onClick = onKeepNewest, enabled = backups.size > 1 && !state.isWorking) {
                    Text("Keep newest…")
                }
            }
            if (backups.isEmpty()) {
                Text(
                    text = if (kind == BackupKind.EXPORT) "No exports yet. Use \"Export Data to JSON\" in Settings."
                    else "No automatic backups yet.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            backups.forEachIndexed { index, backup ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BackupRow(
                    backup = backup,
                    isNewest = state.isProtected(backup),
                    selected = backup.id in state.selected,
                    enabled = !state.isWorking,
                    onToggle = { onToggle(backup) },
                    onShare = { onShare(backup) },
                    onRestore = { onRestore(backup) },
                    onDelete = { onDelete(backup) }
                )
            }
        }
    }
}

@Composable
private fun BackupRow(
    backup: BackupFile,
    isNewest: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isNewest, onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle() },
            enabled = enabled && !isNewest
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(backup.createdAtMillis),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatSize(backup.sizeBytes) + if (isNewest) " · Newest (kept)" else "",
                color = if (isNewest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                Icon(Icons.Default.MoreVert, contentDescription = "Backup actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    onClick = { menuOpen = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text("Restore") },
                    leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                    onClick = { menuOpen = false; onRestore() }
                )
                DropdownMenuItem(
                    text = { Text(if (isNewest) "Delete (newest is kept)" else "Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    enabled = !isNewest,
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun KeepNewestDialog(
    kind: BackupKind,
    total: Int,
    toDelete: (Int) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var keep by remember { mutableIntStateOf(minOf(3, total).coerceAtLeast(1)) }
    val deleting = toDelete(keep)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep newest ${kindTitle(kind).lowercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = { keep-- }, enabled = keep > 1) { Text("−") }
                    Text("Keep $keep", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { keep++ }, enabled = keep < total) { Text("+") }
                }
                Text(
                    if (deleting == 0) "Nothing to delete."
                    else "$deleting older backup${if (deleting == 1) "" else "s"} will be deleted. This cannot be undone."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(keep) },
                enabled = deleting > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete $deleting") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private val timeFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormat)

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.ENGLISH, "%.0f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

private fun kindTitle(kind: BackupKind) = if (kind == BackupKind.EXPORT) "Exported backups" else "Automatic daily backups"

private fun kindLabel(kind: BackupKind) = if (kind == BackupKind.EXPORT) "export" else "daily backup"
