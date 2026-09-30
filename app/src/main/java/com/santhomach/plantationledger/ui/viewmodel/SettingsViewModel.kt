package com.santhomach.plantationledger.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.export.ExportManager
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class SettingsUiState(
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportManager: ExportManager,
    private val repository: ExpenseRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // ---------------------------------------------------------------------------------------------
    // Theme
    // ---------------------------------------------------------------------------------------------

    val themeMode: StateFlow<ThemeMode> = dataStore.data
        .map { prefs ->
            when (prefs[THEME_KEY]) {
                "LIGHT" -> ThemeMode.LIGHT
                "DARK" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { it[THEME_KEY] = mode.name }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Master data
    // ---------------------------------------------------------------------------------------------

    val permanentWorkers: StateFlow<List<PermanentWorker>> = repository.getAllActivePermanentWorkersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workerTypes: StateFlow<List<WorkerType>> = repository.getAllActiveWorkerTypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseTypes: StateFlow<List<ExpenseType>> = repository.getAllActiveExpenseTypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeTypes: StateFlow<List<IncomeType>> = repository.getAllActiveIncomeTypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workTasks: StateFlow<List<WorkTask>> = repository.getAllActiveWorkTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseSubtypes: StateFlow<List<ExpenseSubtype>> = repository.getAllActiveExpenseSubtypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun setError(message: String?) {
        _uiState.update { it.copy(error = message) }
    }

    // Permanent workers
    fun addPermanentWorker(name: String, role: String, dailyWage: BigDecimal) {
        viewModelScope.launch {
            try {
                val worker = PermanentWorker(name = name, role = role, dailyBasicWage = dailyWage)
                repository.insertPermanentWorker(worker)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun updatePermanentWorker(worker: PermanentWorker) {
        viewModelScope.launch {
            try {
                repository.updatePermanentWorker(worker)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun deletePermanentWorker(worker: PermanentWorker) {
        viewModelScope.launch {
            try {
                repository.deletePermanentWorker(worker)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    // Worker types
    fun addWorkerType(name: String, wage: BigDecimal) {
        viewModelScope.launch {
            try {
                repository.insertWorkerType(WorkerType(workerTypeName = name, dailyBasicWage = wage))
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun updateWorkerType(workerType: WorkerType) {
        viewModelScope.launch {
            try {
                repository.updateWorkerType(workerType)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun deleteWorkerType(workerType: WorkerType) {
        viewModelScope.launch {
            try {
                repository.deleteWorkerType(workerType)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    // Expense types / subtypes
    fun addExpenseType(name: String) {
        viewModelScope.launch {
            try {
                repository.insertExpenseType(ExpenseType(typeName = name))
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun updateExpenseType(expenseType: ExpenseType) {
        viewModelScope.launch {
            try {
                repository.updateExpenseType(expenseType)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun deleteExpenseType(expenseType: ExpenseType) {
        viewModelScope.launch {
            try {
                repository.deleteExpenseType(expenseType)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun deleteExpenseSubtype(expenseSubtype: ExpenseSubtype) {
        viewModelScope.launch {
            try {
                repository.deleteExpenseSubtype(expenseSubtype)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    // Income types
    fun addIncomeType(name: String) {
        viewModelScope.launch {
            try {
                repository.insertIncomeType(IncomeType(typeName = name))
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun updateIncomeType(incomeType: IncomeType) {
        viewModelScope.launch {
            try {
                repository.updateIncomeType(incomeType)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun deleteIncomeType(incomeType: IncomeType) {
        viewModelScope.launch {
            try {
                repository.deleteIncomeType(incomeType)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    // Work tasks
    fun addWorkTask(name: String) {
        viewModelScope.launch {
            try {
                repository.insertWorkTask(WorkTask(taskName = name))
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun updateWorkTask(task: WorkTask) {
        viewModelScope.launch {
            try {
                repository.updateWorkTask(task)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    fun deleteWorkTask(task: WorkTask) {
        viewModelScope.launch {
            try {
                repository.deleteWorkTask(task)
            } catch (e: Exception) {
                setError(e.message)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Backup / restore
    // ---------------------------------------------------------------------------------------------

    suspend fun exportData(context: Context) {
        try {
            _uiState.update { it.copy(isExporting = true) }
            val path = exportManager.exportToFile()
            _uiState.update {
                it.copy(isExporting = false, exportMessage = "Data exported successfully to: $path")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isExporting = false, error = "Export failed: ${e.message}") }
        }
    }

    suspend fun importData(uri: Uri, context: Context) {
        try {
            _uiState.update { it.copy(isExporting = true) }
            val jsonString = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw Exception("Could not read file")

            val note = exportManager.importFromJson(jsonString)
            val message = if (note != null) {
                "Data imported successfully. $note Please restart the app if changes don't appear immediately."
            } else {
                "Data imported successfully. Please restart the app if changes don't appear immediately."
            }
            _uiState.update { it.copy(isExporting = false, exportMessage = message) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isExporting = false, error = "Import failed: ${e.message}") }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(exportMessage = null, error = null) }
    }

    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}
