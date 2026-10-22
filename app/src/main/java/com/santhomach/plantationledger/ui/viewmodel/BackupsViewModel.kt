package com.santhomach.plantationledger.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.backup.BackupFile
import com.santhomach.plantationledger.data.backup.BackupKind
import com.santhomach.plantationledger.data.backup.BackupStore
import com.santhomach.plantationledger.data.export.ExportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupsUiState(
    /** Newest first. */
    val exports: List<BackupFile> = emptyList(),
    /** Newest first. */
    val autoBackups: List<BackupFile> = emptyList(),
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val message: String? = null,
    val error: String? = null
) {
    /** The newest backup of each kind is never deleted, so at least one always remains. */
    fun isProtected(backup: BackupFile): Boolean = when (backup.kind) {
        BackupKind.EXPORT -> exports.firstOrNull()?.id == backup.id
        BackupKind.AUTO -> autoBackups.firstOrNull()?.id == backup.id
    }

    fun backupsOf(kind: BackupKind) = if (kind == BackupKind.EXPORT) exports else autoBackups
}

/**
 * The Backups screen: lists exported (Downloads) and automatic daily backups, deletes older ones,
 * and restores a backup after taking a fresh safety export.
 */
@HiltViewModel
class BackupsViewModel @Inject constructor(
    private val store: BackupStore,
    private val exportManager: ExportManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupsUiState())
    val uiState: StateFlow<BackupsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        try {
            val all = store.list().sortedByDescending { it.createdAtMillis }
            _uiState.update { state ->
                val exports = all.filter { it.kind == BackupKind.EXPORT }
                val autos = all.filter { it.kind == BackupKind.AUTO }
                val ids = all.map { it.id }.toSet()
                state.copy(
                    exports = exports,
                    autoBackups = autos,
                    selected = state.selected intersect ids,
                    isLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = "Could not list backups: ${e.message}") }
        }
    }

    fun toggleSelected(backup: BackupFile) {
        if (_uiState.value.isProtected(backup)) return
        _uiState.update { state ->
            val selected = if (backup.id in state.selected) state.selected - backup.id else state.selected + backup.id
            state.copy(selected = selected)
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selected = emptySet()) }

    /** The selected backups that may be deleted (never the newest of a kind). */
    fun selectedBackups(): List<BackupFile> = with(_uiState.value) {
        (exports + autoBackups).filter { it.id in selected && !isProtected(it) }
    }

    /** Backups that "keep newest [keep]" would delete for [kind] (at least one is always kept). */
    fun olderThanNewest(kind: BackupKind, keep: Int): List<BackupFile> =
        _uiState.value.backupsOf(kind).drop(keep.coerceAtLeast(1))

    fun deleteSelected() = delete(selectedBackups())

    fun keepNewest(kind: BackupKind, keep: Int) = delete(olderThanNewest(kind, keep))

    fun delete(backups: List<BackupFile>) {
        val state = _uiState.value
        val deletable = backups.filterNot { state.isProtected(it) }
        if (deletable.isEmpty() || state.isWorking) return
        _uiState.update { it.copy(isWorking = true, message = null, error = null) }
        viewModelScope.launch {
            var deleted = 0
            val failed = mutableListOf<String>()
            deletable.forEach { backup ->
                val ok = try {
                    store.delete(backup)
                } catch (e: Exception) {
                    false
                }
                if (ok) deleted++ else failed += backup.name
            }
            reload()
            _uiState.update {
                it.copy(
                    isWorking = false,
                    selected = emptySet(),
                    message = "Deleted $deleted backup${if (deleted == 1) "" else "s"}.",
                    error = if (failed.isEmpty()) null else "Could not delete: ${failed.joinToString()}"
                )
            }
        }
    }

    /**
     * Saves a fresh export of the current data first, then imports [backup]. If the safety export
     * fails, nothing is restored.
     */
    fun restore(backup: BackupFile) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, message = null, error = null) }
        viewModelScope.launch {
            val safetyCopy = try {
                exportManager.exportToFile()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isWorking = false, error = "Restore cancelled: could not save a safety export first (${e.message}).")
                }
                return@launch
            }
            try {
                val note = exportManager.importFromJson(store.readText(backup))
                reload()
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        message = "Restored ${backup.name}. Your data before the restore was saved to $safetyCopy." +
                            (note?.let { n -> " $n" } ?: "")
                    )
                }
            } catch (e: Exception) {
                reload()
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        error = "Restore failed: ${e.message}. Your data before the restore was saved to $safetyCopy."
                    )
                }
            }
        }
    }

    fun shareUri(backup: BackupFile): Uri = store.shareUri(backup)

    fun clearMessages() = _uiState.update { it.copy(message = null, error = null) }
}
