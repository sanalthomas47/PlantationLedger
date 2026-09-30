package com.santhomach.plantationledger.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.backup.DailyBackupManager
import com.santhomach.plantationledger.data.model.AdvanceEntry
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeEntry
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerGroupEntry
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class DailyExpenseUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isEditing: Boolean = false,
    val error: String? = null
)

/**
 * Edits one day's [DailyExpense]. The entry being edited lives in [currentExpense]; every change
 * produces a new copy and totals are recalculated, nothing is persisted until [saveExpense].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val application: Application,
    private val dailyBackupManager: DailyBackupManager,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyExpenseUiState())
    val uiState: StateFlow<DailyExpenseUiState> = _uiState.asStateFlow()

    private val _currentExpense = MutableStateFlow<DailyExpense?>(null)
    val currentExpense: StateFlow<DailyExpense?> = _currentExpense.asStateFlow()

    val expenseTypes: StateFlow<List<ExpenseType>> = repository.getAllActiveExpenseTypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseSubtypes: StateFlow<List<ExpenseSubtype>> = repository.getAllActiveExpenseSubtypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeTypes: StateFlow<List<IncomeType>> = repository.getAllActiveIncomeTypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workerTypes: StateFlow<List<WorkerType>> = repository.getAllActiveWorkerTypesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val permanentWorkers: StateFlow<List<PermanentWorker>> = repository.getAllActivePermanentWorkersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val workTasks: StateFlow<List<WorkTask>> = repository.getAllActiveWorkTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Money carried over from all days before the Monday of the current expense's week:
     * (advances + weekly payments) - (labour + overtime + other expenses).
     */
    val previousExcessBalance: StateFlow<BigDecimal> = _currentExpense
        .flatMapLatest { expense ->
            if (expense == null) {
                flowOf(BigDecimal.ZERO)
            } else {
                val expenseDate = try {
                    LocalDate.parse(expense.date)
                } catch (e: Exception) {
                    LocalDate.now()
                }
                val startStr = expenseDate.with(DayOfWeek.MONDAY).format(DateTimeFormatter.ISO_LOCAL_DATE)
                flow {
                    val previousRecords = repository.getDailyExpensesBeforeDate(startStr)
                    val totalPaid = previousRecords.fold(BigDecimal.ZERO) { acc, e ->
                        acc.add(e.advanceAmount).add(e.weeklyPaymentDone)
                    }
                    val totalExpenses = previousRecords.fold(BigDecimal.ZERO) { acc, e ->
                        acc.add(e.totalLaborCost).add(e.totalOvertimeCost).add(e.totalOtherExpensesCost)
                    }
                    emit(totalPaid.subtract(totalExpenses))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    /** Vendor payments that settle purchase lines of the current expense. */
    val vendorPaymentsForCurrentExpense: StateFlow<List<VendorPayment>> = _currentExpense
        .flatMapLatest { expense ->
            val date = expense?.date
            if (date != null) repository.getVendorPaymentsBySourceExpenseDateFlow(date)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Vendor payments physically made on the current expense's date. */
    val vendorPaymentsMadeOnDate: StateFlow<List<VendorPayment>> = _currentExpense
        .flatMapLatest { expense ->
            val date = expense?.date
            if (date != null) repository.getVendorPaymentsByDateFlow(date)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            try {
                repository.initializeDefaultData()
                val prefs = dataStore.data.first()
                val alreadyMigrated = prefs[LEGACY_VENDOR_MIGRATION_KEY] == true
                if (!alreadyMigrated) {
                    repository.migrateVendorLegacyPayments()
                    dataStore.edit { it[LEGACY_VENDOR_MIGRATION_KEY] = true }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------------

    fun loadOrCreateExpense(date: LocalDate) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val dateString = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val existing = repository.getDailyExpensesByDate(dateString)
                if (existing.isNotEmpty()) {
                    _currentExpense.value = existing.first()
                    _uiState.update { it.copy(isLoading = false, isEditing = true) }
                } else {
                    _currentExpense.value = DailyExpense(date = dateString, createdBy = "user")
                    _uiState.update { it.copy(isLoading = false, isEditing = true, error = null) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadExpense(id: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val expense = repository.getDailyExpenseById(id)
                _currentExpense.value = expense
                _uiState.update { it.copy(isLoading = false, isEditing = expense != null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Overtime
    // ---------------------------------------------------------------------------------------------

    fun updateOvertime(hours: Int) {
        _currentExpense.update { it?.copy(overtimeHours = hours) }
        recalculateTotals()
    }

    fun updateExtraOvertimeAmount(amount: BigDecimal) {
        _currentExpense.update { it?.copy(extraOvertimeAmount = amount) }
        recalculateTotals()
    }

    // ---------------------------------------------------------------------------------------------
    // Worker groups
    // ---------------------------------------------------------------------------------------------

    fun addWorkerGroup(
        workerTypeId: Int,
        count: Int,
        wage: BigDecimal,
        overtimeHours: Int,
        overtimeWagePerHour: BigDecimal,
        task: String,
        comments: String
    ) {
        _currentExpense.update { expense ->
            expense?.let {
                val groups = parseWorkerGroups(it.workerGroups)
                val workerTypeName = workerTypes.value.find { t -> t.id == workerTypeId }?.workerTypeName ?: "Unknown"
                val updated = groups + WorkerGroupEntry(
                    workerTypeId = workerTypeId,
                    workerTypeName = workerTypeName,
                    count = count,
                    wagePerDay = wage,
                    overtimeHours = overtimeHours,
                    overtimeWagePerHour = overtimeWagePerHour,
                    taskPerformed = task,
                    comments = comments
                )
                it.copy(workerGroups = Json.encodeToString(ListSerializer(WorkerGroupEntry.serializer()), updated))
            }
        }
        recalculateTotals()
    }

    fun removeWorkerGroup(index: Int) {
        _currentExpense.update { expense ->
            expense?.let {
                val groups = parseWorkerGroups(it.workerGroups)
                if (index in groups.indices) {
                    val updated = groups.toMutableList().apply { removeAt(index) }
                    it.copy(workerGroups = Json.encodeToString(ListSerializer(WorkerGroupEntry.serializer()), updated))
                } else {
                    it
                }
            }
        }
        recalculateTotals()
    }

    fun updateWorkerGroup(
        index: Int,
        workerTypeId: Int,
        count: Int,
        wage: BigDecimal,
        overtimeHours: Int,
        overtimeWagePerHour: BigDecimal,
        task: String,
        comments: String
    ) {
        _currentExpense.update { expense ->
            expense?.let {
                val groups = parseWorkerGroups(it.workerGroups).toMutableList()
                if (index in groups.indices) {
                    val workerTypeName = workerTypes.value.find { t -> t.id == workerTypeId }?.workerTypeName ?: "Unknown"
                    groups[index] = WorkerGroupEntry(
                        workerTypeId = workerTypeId,
                        workerTypeName = workerTypeName,
                        count = count,
                        wagePerDay = wage,
                        overtimeHours = overtimeHours,
                        overtimeWagePerHour = overtimeWagePerHour,
                        taskPerformed = task,
                        comments = comments
                    )
                    it.copy(workerGroups = Json.encodeToString(ListSerializer(WorkerGroupEntry.serializer()), groups))
                } else {
                    it
                }
            }
        }
        recalculateTotals()
    }

    fun addNewWorkTask(taskName: String) {
        viewModelScope.launch {
            repository.insertWorkTask(WorkTask(taskName = taskName))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Other expenses
    // ---------------------------------------------------------------------------------------------

    /**
     * Resolves the expense type (creating a custom one if requested) and makes sure a custom
     * subtype exists under it. Returns (typeId, typeName).
     */
    private suspend fun resolveExpenseType(
        expenseTypeId: Int,
        customTypeName: String?,
        customSubtypeName: String?
    ): Pair<Int, String> {
        val finalTypeId: Int
        val finalTypeName: String
        if (customTypeName != null) {
            finalTypeId = repository.insertExpenseType(ExpenseType(typeName = customTypeName)).toInt()
            finalTypeName = customTypeName
        } else {
            finalTypeId = expenseTypeId
            finalTypeName = expenseTypes.value.find { it.id == expenseTypeId }?.typeName ?: "Unknown"
        }

        if (customSubtypeName != null) {
            val existing = repository.getExpenseSubtypeByName(customSubtypeName)
            if (existing == null || existing.parentTypeName.id != finalTypeId) {
                val parentType = expenseTypes.value.find { it.id == finalTypeId }
                    ?: ExpenseType(id = finalTypeId, typeName = finalTypeName)
                repository.insertExpenseSubtype(
                    ExpenseSubtype(typeName = customSubtypeName, parentTypeName = parentType)
                )
            }
        }
        return finalTypeId to finalTypeName
    }

    fun addOtherExpense(
        expenseTypeId: Int,
        amount: BigDecimal,
        quantity: Double = 1.0,
        notes: String = "",
        customTypeName: String? = null,
        customSubtypeName: String? = null,
        receiptImagePath: String? = null
    ) {
        viewModelScope.launch {
            val (finalTypeId, finalTypeName) = resolveExpenseType(expenseTypeId, customTypeName, customSubtypeName)
            _currentExpense.update { expense ->
                expense?.let {
                    val updated = parseOtherExpenses(it.otherExpenses) + OtherExpenseEntry(
                        expenseTypeId = finalTypeId,
                        typeName = finalTypeName,
                        subtypeName = customSubtypeName,
                        amount = amount,
                        quantity = quantity,
                        notes = notes,
                        receiptImagePath = receiptImagePath
                    )
                    it.copy(
                        otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), updated),
                        totalOtherExpensesCost = updated.sumOf { e -> e.amount }
                    )
                }
            }
            recalculateTotals()
        }
    }

    fun removeOtherExpense(index: Int) {
        _currentExpense.update { expense ->
            expense?.let {
                val entries = parseOtherExpenses(it.otherExpenses)
                if (index in entries.indices) {
                    val updated = entries.toMutableList().apply { removeAt(index) }
                    it.copy(
                        otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), updated),
                        totalOtherExpensesCost = updated.sumOf { e -> e.amount }
                    )
                } else {
                    it
                }
            }
        }
        recalculateTotals()
    }

    fun updateOtherExpense(
        index: Int,
        expenseTypeId: Int,
        amount: BigDecimal,
        quantity: Double,
        notes: String,
        customTypeName: String?,
        customSubtypeName: String?,
        receiptImagePath: String?
    ) {
        viewModelScope.launch {
            val (finalTypeId, finalTypeName) = resolveExpenseType(expenseTypeId, customTypeName, customSubtypeName)
            _currentExpense.update { expense ->
                expense?.let {
                    val entries = parseOtherExpenses(it.otherExpenses).toMutableList()
                    if (index in entries.indices) {
                        entries[index] = OtherExpenseEntry(
                            expenseTypeId = finalTypeId,
                            typeName = finalTypeName,
                            subtypeName = customSubtypeName,
                            amount = amount,
                            quantity = quantity,
                            notes = notes,
                            receiptImagePath = receiptImagePath ?: entries[index].receiptImagePath
                        )
                        it.copy(
                            otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), entries),
                            totalOtherExpensesCost = entries.sumOf { e -> e.amount }
                        )
                    } else {
                        it
                    }
                }
            }
            recalculateTotals()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Income
    // ---------------------------------------------------------------------------------------------

    private suspend fun resolveIncomeType(incomeTypeId: Int, customTypeName: String?): Pair<Int, String> {
        return if (customTypeName != null) {
            repository.insertIncomeType(IncomeType(typeName = customTypeName)).toInt() to customTypeName
        } else {
            incomeTypeId to (incomeTypes.value.find { it.id == incomeTypeId }?.typeName ?: "Unknown")
        }
    }

    fun addIncome(
        incomeTypeId: Int,
        amount: BigDecimal,
        weight: Double = 0.0,
        pricePerKilo: BigDecimal = BigDecimal.ZERO,
        transportationCharge: BigDecimal = BigDecimal.ZERO,
        notes: String = "",
        customTypeName: String? = null
    ) {
        viewModelScope.launch {
            val (finalTypeId, finalTypeName) = resolveIncomeType(incomeTypeId, customTypeName)
            _currentExpense.update { expense ->
                expense?.let {
                    val updated = parseIncomeEntries(it.incomeEntries) + IncomeEntry(
                        incomeTypeId = finalTypeId,
                        typeName = finalTypeName,
                        weight = weight,
                        pricePerKilo = pricePerKilo,
                        transportationCharge = transportationCharge,
                        amount = amount,
                        notes = notes
                    )
                    it.copy(
                        incomeEntries = Json.encodeToString(ListSerializer(IncomeEntry.serializer()), updated),
                        totalIncome = updated.sumOf { e -> e.amount }
                    )
                }
            }
            recalculateTotals()
        }
    }

    fun removeIncome(index: Int) {
        _currentExpense.update { expense ->
            expense?.let {
                val entries = parseIncomeEntries(it.incomeEntries)
                if (index in entries.indices) {
                    val updated = entries.toMutableList().apply { removeAt(index) }
                    it.copy(
                        incomeEntries = Json.encodeToString(ListSerializer(IncomeEntry.serializer()), updated),
                        totalIncome = updated.sumOf { e -> e.amount }
                    )
                } else {
                    it
                }
            }
        }
        recalculateTotals()
    }

    fun updateIncome(
        index: Int,
        incomeTypeId: Int,
        amount: BigDecimal,
        weight: Double = 0.0,
        pricePerKilo: BigDecimal = BigDecimal.ZERO,
        transportationCharge: BigDecimal = BigDecimal.ZERO,
        notes: String = "",
        customTypeName: String? = null
    ) {
        viewModelScope.launch {
            val (finalTypeId, finalTypeName) = resolveIncomeType(incomeTypeId, customTypeName)
            _currentExpense.update { expense ->
                expense?.let {
                    val entries = parseIncomeEntries(it.incomeEntries).toMutableList()
                    if (index in entries.indices) {
                        entries[index] = IncomeEntry(
                            incomeTypeId = finalTypeId,
                            typeName = finalTypeName,
                            weight = weight,
                            pricePerKilo = pricePerKilo,
                            transportationCharge = transportationCharge,
                            amount = amount,
                            notes = notes
                        )
                        it.copy(
                            incomeEntries = Json.encodeToString(ListSerializer(IncomeEntry.serializer()), entries),
                            totalIncome = entries.sumOf { e -> e.amount }
                        )
                    } else {
                        it
                    }
                }
            }
            recalculateTotals()
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Simple field updates
    // ---------------------------------------------------------------------------------------------

    fun updateExcessBalance(amount: BigDecimal) {
        _currentExpense.update { it?.copy(excessBalance = amount) }
    }

    fun updateAdvanceAmount(amount: BigDecimal) {
        _currentExpense.update { it?.copy(advanceAmount = amount) }
    }

    fun updateAdvanceReason(reason: String) {
        _currentExpense.update { it?.copy(advanceReason = reason) }
    }

    fun updateComments(comments: String) {
        _currentExpense.update { it?.copy(comments = comments) }
    }

    fun updateManager(managerId: Int?) {
        _currentExpense.update { it?.copy(managerId = managerId) }
    }

    fun updateWeeklyPaymentDone(amount: BigDecimal) {
        _currentExpense.update { it?.copy(weeklyPaymentDone = amount) }
    }

    // ---------------------------------------------------------------------------------------------
    // Advances
    // ---------------------------------------------------------------------------------------------

    fun addAdvanceEntry(amount: BigDecimal, reason: String, recipient: String) {
        _currentExpense.update { expense ->
            expense?.let {
                val updated = parseAdvanceEntries(it.advanceEntries) +
                    AdvanceEntry(amount = amount, reason = reason, recipientName = recipient)
                it.copy(
                    advanceAmount = updated.sumOf { e -> e.amount },
                    advanceEntries = Json.encodeToString(ListSerializer(AdvanceEntry.serializer()), updated)
                )
            }
        }
    }

    fun removeAdvanceEntry(index: Int) {
        _currentExpense.update { expense ->
            expense?.let {
                val entries = parseAdvanceEntries(it.advanceEntries)
                if (index in entries.indices) {
                    val updated = entries.toMutableList().apply { removeAt(index) }
                    it.copy(
                        advanceAmount = updated.sumOf { e -> e.amount },
                        advanceEntries = Json.encodeToString(ListSerializer(AdvanceEntry.serializer()), updated)
                    )
                } else {
                    it
                }
            }
        }
    }

    fun updateAdvanceEntry(index: Int, amount: BigDecimal, reason: String, recipient: String) {
        _currentExpense.update { expense ->
            expense?.let {
                val entries = parseAdvanceEntries(it.advanceEntries).toMutableList()
                if (index in entries.indices) {
                    entries[index] = AdvanceEntry(amount = amount, reason = reason, recipientName = recipient)
                    it.copy(
                        advanceAmount = entries.sumOf { e -> e.amount },
                        advanceEntries = Json.encodeToString(ListSerializer(AdvanceEntry.serializer()), entries)
                    )
                } else {
                    it
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Totals
    // ---------------------------------------------------------------------------------------------

    fun recalculateTotals() {
        _currentExpense.update { expense ->
            expense?.let {
                it.copy(
                    totalLaborCost = calculateLaborCost(it),
                    totalOvertimeCost = calculateOvertimeCost(it),
                    advanceAmount = parseAdvanceEntries(it.advanceEntries).sumOf { e -> e.amount }
                )
            }
        }
    }

    private fun calculateTotalLaborFromGroups(groups: List<WorkerGroupEntry>): BigDecimal =
        groups.fold(BigDecimal.ZERO) { acc, g ->
            acc.add(g.wagePerDay.multiply(BigDecimal.valueOf(g.count.toLong())))
        }

    private fun calculateTotalOvertimeFromGroups(groups: List<WorkerGroupEntry>): BigDecimal =
        groups.fold(BigDecimal.ZERO) { acc, g ->
            acc.add(
                g.overtimeWagePerHour
                    .multiply(BigDecimal.valueOf(g.overtimeHours.toLong()))
                    .multiply(BigDecimal.valueOf(g.count.toLong()))
            )
        }

    private fun calculateLaborCost(expense: DailyExpense): BigDecimal {
        val legacy = expense.malayaliMaleWagePerDay.multiply(BigDecimal.valueOf(expense.malayaliMaleCount.toLong()))
            .add(expense.bengaliMaleWagePerDay.multiply(BigDecimal.valueOf(expense.bengaliMaleCount.toLong())))
            .add(expense.malayaliFemaleWagePerDay.multiply(BigDecimal.valueOf(expense.malayaliFemaleCount.toLong())))
            .add(expense.bengaliFemaleWagePerDay.multiply(BigDecimal.valueOf(expense.bengaliFemaleCount.toLong())))
        return legacy.add(calculateTotalLaborFromGroups(parseWorkerGroups(expense.workerGroups)))
    }

    private fun calculateOvertimeCost(expense: DailyExpense): BigDecimal {
        val groupOvertime = calculateTotalOvertimeFromGroups(parseWorkerGroups(expense.workerGroups))

        // Legacy overtime: 1.5x the average daily wage per hour
        val totalWorkers = BigDecimal.valueOf(
            (expense.malayaliMaleCount + expense.bengaliMaleCount +
                expense.malayaliFemaleCount + expense.bengaliFemaleCount).toLong()
        )
        val avgWage = if (totalWorkers > BigDecimal.ZERO) {
            expense.malayaliMaleWagePerDay
                .add(expense.bengaliMaleWagePerDay)
                .add(expense.malayaliFemaleWagePerDay)
                .add(expense.bengaliFemaleWagePerDay)
                .divide(BigDecimal("4"), RoundingMode.HALF_EVEN)
        } else {
            BigDecimal.ZERO
        }
        val legacyOvertime = avgWage
            .multiply(BigDecimal("1.5"))
            .multiply(BigDecimal.valueOf(expense.overtimeHours.toLong()))

        return legacyOvertime.add(groupOvertime).add(expense.extraOvertimeAmount)
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    fun saveExpense() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSaving = true, error = null) }
                val expense = _currentExpense.value ?: throw IllegalStateException("No expense to save")

                val id: Long = if (expense.id == 0) {
                    val newId = repository.insertDailyExpense(expense)
                    dailyBackupManager.maybeBackup()
                    newId
                } else {
                    repository.updateDailyExpense(expense)
                    expense.id.toLong()
                }

                // Make sure a weekly funds row exists for this week
                val expenseDate = LocalDate.parse(expense.date)
                val monday = expenseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val mondayStr = monday.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val allWeeklyFunds = repository.getAllWeeklyFundsFlow().first()
                if (allWeeklyFunds.none { it.weekStartDate == mondayStr }) {
                    repository.insertWeeklyFunds(
                        WeeklyFunds(
                            weekStartDate = mondayStr,
                            amountReceived = BigDecimal.ZERO,
                            notes = "Auto-created from first expense of the week"
                        )
                    )
                }

                _uiState.update { it.copy(isSaving = false, isEditing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteExpense() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isDeleting = true, error = null) }
                val expense = _currentExpense.value ?: throw IllegalStateException("No expense to delete")

                // Remove vendor payments linked to this day's purchases first
                repository.getVendorPaymentsBySourceExpenseDate(expense.date).forEach { payment ->
                    repository.deleteVendorPayment(payment)
                }
                repository.deleteDailyExpense(expense)

                _currentExpense.value = null
                _uiState.update { it.copy(isDeleting = false, isEditing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeleting = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun cancelEditing() {
        _currentExpense.value = null
        _uiState.update { it.copy(isEditing = false, error = null) }
    }

    // ---------------------------------------------------------------------------------------------
    // JSON helpers
    // ---------------------------------------------------------------------------------------------

    fun parseOtherExpenses(json: String): List<OtherExpenseEntry> = try {
        if (json.isBlank() || json == "[]") emptyList()
        else Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), json)
    } catch (e: Exception) {
        emptyList()
    }

    fun parseIncomeEntries(json: String): List<IncomeEntry> = try {
        if (json.isBlank() || json == "[]") emptyList()
        else Json.decodeFromString(ListSerializer(IncomeEntry.serializer()), json)
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseWorkerGroups(json: String): List<WorkerGroupEntry> = try {
        if (json.isBlank() || json == "[]") emptyList()
        else Json.decodeFromString(ListSerializer(WorkerGroupEntry.serializer()), json)
    } catch (e: Exception) {
        emptyList()
    }

    private fun parseAdvanceEntries(json: String): List<AdvanceEntry> = try {
        if (json.isBlank() || json == "[]") emptyList()
        else Json.decodeFromString(ListSerializer(AdvanceEntry.serializer()), json)
    } catch (e: Exception) {
        emptyList()
    }

    fun getOtherExpenses(): List<OtherExpenseEntry> =
        _currentExpense.value?.let { parseOtherExpenses(it.otherExpenses) } ?: emptyList()

    fun getIncomeEntries(): List<IncomeEntry> =
        _currentExpense.value?.let { parseIncomeEntries(it.incomeEntries) } ?: emptyList()

    fun getWorkerGroups(): List<WorkerGroupEntry> =
        _currentExpense.value?.let { parseWorkerGroups(it.workerGroups) } ?: emptyList()

    fun getAdvanceEntries(): List<AdvanceEntry> =
        _currentExpense.value?.let { parseAdvanceEntries(it.advanceEntries) } ?: emptyList()

    // ---------------------------------------------------------------------------------------------
    // Derived amounts for the summary card
    // ---------------------------------------------------------------------------------------------

    /** Labour + overtime + other expenses of the current day. */
    fun getTotalActualExpenses(): BigDecimal =
        _currentExpense.value?.let {
            it.totalLaborCost.add(it.totalOvertimeCost).add(it.totalOtherExpensesCost)
        } ?: BigDecimal.ZERO

    /** Advances + weekly payment + vendor payments made on this date. */
    fun getTotalPaymentsMade(): BigDecimal {
        val fromExpense = _currentExpense.value?.let { it.advanceAmount.add(it.weeklyPaymentDone) } ?: BigDecimal.ZERO
        val vendorPaid = vendorPaymentsMadeOnDate.value.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
        return fromExpense.add(vendorPaid)
    }

    /** Payments made + carry-over from earlier weeks - actual expenses. */
    fun getNetAmount(): BigDecimal {
        val totalActualExpenses = getTotalActualExpenses()
        val totalPaymentsMade = getTotalPaymentsMade()
        val carryover = previousExcessBalance.value
        return totalPaymentsMade.add(carryover).subtract(totalActualExpenses)
    }

    /**
     * Copies a picked image into the app's private receipts folder and returns its absolute path.
     */
    fun saveReceiptImage(uri: Uri): String? {
        return try {
            val input = application.contentResolver.openInputStream(uri) ?: return null
            val fileName = "receipt_${System.currentTimeMillis()}.jpg"
            val receiptsDir = File(application.filesDir, "receipts")
            if (!receiptsDir.exists()) receiptsDir.mkdirs()
            val outFile = File(receiptsDir, fileName)
            input.use { inStream ->
                FileOutputStream(outFile).use { outStream -> inStream.copyTo(outStream) }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val LEGACY_VENDOR_MIGRATION_KEY = booleanPreferencesKey("legacy_vendor_migration_done")
    }
}
