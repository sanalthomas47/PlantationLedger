package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.AdvanceEntry
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.IncomeEntry
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WorkerGroupEntry
import com.santhomach.plantationledger.data.model.balanceBroughtForward
import com.santhomach.plantationledger.data.model.isVendorPurchase
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.data.repository.ExpenseSummary
import com.santhomach.plantationledger.data.repository.WeeklyExpenseSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

sealed class DateRange {
    object CurrentWeek : DateRange()
    object Last7Days : DateRange()
    object Last30Days : DateRange()
    object Last90Days : DateRange()
    object LastYear : DateRange()
    object ThisMonth : DateRange()
    object LastMonth : DateRange()
    object ThisYear : DateRange()
    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : DateRange()
}

data class ReportsUiState(
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val cloneSuccessDate: LocalDate? = null
)

data class CategoryExpense(
    val categoryName: String,
    val totalAmount: BigDecimal
)

data class DailyTrend(
    val date: String,
    val amount: BigDecimal
)

data class IncomeBreakdown(
    val commodityName: String,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val totalWeight: Double = 0.0
)

data class WorkerTypeSpecific(
    val workerTypeName: String,
    val totalCount: Int = 0,
    val totalBaseCost: BigDecimal = BigDecimal.ZERO,
    val totalOvertimeCost: BigDecimal = BigDecimal.ZERO
)

data class OtherExpenseSpecific(
    val typeName: String,
    val subtypeName: String?,
    val totalQuantity: Double = 0.0,
    val totalAmount: BigDecimal = BigDecimal.ZERO
)

data class AdvanceSpecific(
    val recipientName: String,
    val reason: String,
    val totalAmount: BigDecimal = BigDecimal.ZERO
)

data class WorkerTypeSummary(
    val workerType: String,
    val comment: String,
    val totalCount: Int = 0,
    val totalCost: BigDecimal = BigDecimal.ZERO
)

/**
 * One Pesticide / Fertilizer purchase line shown in the vendor ledger.
 */
data class VendorExpenseItem(
    val date: String,
    val typeName: String,
    val subtypeName: String? = null,
    val amount: BigDecimal,
    val notes: String = "",
    val isPaid: Boolean = false
)

data class VendorLedger(
    val totalExpenses: BigDecimal = BigDecimal.ZERO,
    val totalPaid: BigDecimal = BigDecimal.ZERO,
    val outstanding: BigDecimal = BigDecimal.ZERO,
    val items: List<VendorExpenseItem> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private val _dateRange = MutableStateFlow<DateRange>(DateRange.CurrentWeek)
    val dateRange: StateFlow<DateRange> = _dateRange.asStateFlow()

    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    /** Summary for the currently selected [dateRange]. */
    val dailySummary: StateFlow<ExpenseSummary> = _dateRange
        .flatMapLatest { range ->
            repository.getDailyExpenseSummaryFlow(
                getStartDate(range).format(iso),
                getEndDate(range).format(iso)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseSummary())

    val weeklySummary: StateFlow<WeeklyExpenseSummary> = _dateRange
        .flatMapLatest { range ->
            repository.getWeeklyExpenseSummaryFlow(
                getStartDate(range).format(iso),
                getEndDate(range).format(iso)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyExpenseSummary())

    val yearSummary: StateFlow<ExpenseSummary> = repository.getDailyExpenseSummaryFlow(
        LocalDate.now().withDayOfYear(1).format(iso),
        LocalDate.now().format(iso)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseSummary())

    val allTimeSummary: StateFlow<ExpenseSummary> = repository.getDailyExpenseSummaryFlow(
        "1900-01-01",
        "2100-12-31"
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseSummary())

    /** Current week (Monday to Sunday). */
    val weekSummary: StateFlow<ExpenseSummary> = repository.getDailyExpenseSummaryFlow(
        LocalDate.now().with(DayOfWeek.MONDAY).format(iso),
        LocalDate.now().with(DayOfWeek.SUNDAY).format(iso)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExpenseSummary())

    private val allExpenses = repository.getDailyExpensesByDateRangeFlow("1900-01-01", "2100-12-31")

    /**
     * Cash balance brought forward into the current week: the result of every earlier day
     * (see CashBalance.kt). Positive = excess held by the manager, negative = short.
     */
    val broughtForward: StateFlow<BigDecimal> = allExpenses
        .map { it.balanceBroughtForward(LocalDate.now().with(DayOfWeek.MONDAY)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    /** Cash balance brought forward into the start of the selected Reports [dateRange]. */
    val periodBroughtForward: StateFlow<BigDecimal> = combine(_dateRange, allExpenses) { range, expenses ->
        expenses.balanceBroughtForward(getStartDate(range))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    /** All-time pesticide / fertilizer purchases with paid status. */
    val vendorLedger: StateFlow<VendorLedger> = combine(
        repository.getDailyExpensesByDateRangeFlow("1900-01-01", "2100-12-31"),
        repository.getAllVendorPaymentsFlow()
    ) { expenses, vendorPayments ->
        val items = mutableListOf<VendorExpenseItem>()
        var totalExpenses = BigDecimal.ZERO
        var totalPaid = BigDecimal.ZERO

        expenses.sortedBy { it.date }.forEach { expense ->
            try {
                Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                    .filter { it.isVendorPurchase() }
                    .forEach { entry ->
                        items.add(
                            VendorExpenseItem(
                                date = expense.date,
                                typeName = entry.typeName,
                                subtypeName = entry.subtypeName,
                                amount = entry.amount,
                                notes = entry.notes,
                                isPaid = entry.isPaid
                            )
                        )
                        totalExpenses = totalExpenses.add(entry.amount)
                        if (entry.isPaid) totalPaid = totalPaid.add(entry.amount)
                    }
            } catch (e: Exception) {
                // skip malformed row
            }
        }

        // Legacy payments not linked to an expense line
        val legacyPaid = vendorPayments
            .filter { it.sourceExpenseDate == null }
            .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }

        VendorLedger(
            totalExpenses = totalExpenses,
            totalPaid = totalPaid.add(legacyPaid),
            outstanding = totalExpenses.subtract(totalPaid),
            items = items
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, VendorLedger())

    private val _selectedExpenseDate = MutableStateFlow<String?>(null)

    /** Vendor payments linked to the expense currently opened in the detail dialog. */
    val selectedExpenseVendorPayments: StateFlow<List<VendorPayment>> = _selectedExpenseDate
        .flatMapLatest { date ->
            if (date != null) repository.getVendorPaymentsBySourceExpenseDateFlow(date)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectExpenseDate(date: String?) {
        _selectedExpenseDate.value = date
    }

    val recentExpenses: StateFlow<List<DailyExpense>> = repository.getRecentExpensesFlow(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yearlyExpenses: StateFlow<List<DailyExpense>> = repository.getDailyExpensesByDateRangeFlow(
        LocalDate.now().withDayOfYear(1).format(iso),
        LocalDate.now().plusYears(10).format(iso)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredExpenses: StateFlow<List<DailyExpense>> = _dateRange
        .flatMapLatest { range ->
            repository.getDailyExpensesByDateRangeFlow(
                getStartDate(range).format(iso),
                getEndDate(range).format(iso)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------------------------------------------------------------------------------------
    // Vendor bill payments
    // ---------------------------------------------------------------------------------------------

    /**
     * Marks the matching purchase line as paid and records a linked [VendorPayment].
     */
    fun markVendorBillPaid(item: VendorExpenseItem, notes: String, paymentDate: LocalDate) {
        viewModelScope.launch {
            val expenses = repository.getDailyExpensesByDate(item.date)
            if (expenses.isEmpty()) return@launch

            var targetExpense: DailyExpense? = null
            var updatedEntries: List<OtherExpenseEntry>? = null

            for (expense in expenses) {
                try {
                    val entries = Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                    var changed = false
                    val updated = entries.map { entry ->
                        if (!changed && !entry.isPaid &&
                            entry.typeName == item.typeName &&
                            entry.subtypeName == item.subtypeName &&
                            entry.amount.compareTo(item.amount) == 0
                        ) {
                            changed = true
                            entry.copy(isPaid = true)
                        } else {
                            entry
                        }
                    }
                    if (changed) {
                        targetExpense = expense
                        updatedEntries = updated
                        break
                    }
                } catch (e: Exception) {
                    // skip malformed row
                }
            }

            val expense = targetExpense ?: return@launch
            val updated = updatedEntries ?: return@launch

            repository.updateDailyExpense(
                expense.copy(
                    otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), updated)
                )
            )
            repository.insertVendorPayment(
                VendorPayment(
                    date = paymentDate.format(iso),
                    amount = item.amount,
                    vendorName = "",
                    notes = notes,
                    createdAt = LocalDateTime.now().toString(),
                    sourceExpenseDate = item.date,
                    sourceTypeName = item.typeName,
                    sourceSubtypeName = item.subtypeName
                )
            )
        }
    }

    /**
     * Deletes a vendor payment and, if it was linked to a purchase line, marks that line unpaid again.
     */
    fun deleteVendorPayment(payment: VendorPayment) {
        viewModelScope.launch {
            val sourceDate = payment.sourceExpenseDate ?: run {
                repository.deleteVendorPayment(payment)
                return@launch
            }

            val expenses = repository.getDailyExpensesByDate(sourceDate)
            if (expenses.isEmpty()) {
                repository.deleteVendorPayment(payment)
                return@launch
            }

            var targetExpense: DailyExpense? = null
            var updatedEntries: List<OtherExpenseEntry>? = null

            for (expense in expenses) {
                try {
                    val entries = Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                    var changed = false
                    val updated = entries.map { entry ->
                        if (!changed && entry.isPaid &&
                            entry.typeName == payment.sourceTypeName &&
                            entry.subtypeName == payment.sourceSubtypeName &&
                            entry.amount.compareTo(payment.amount) == 0
                        ) {
                            changed = true
                            entry.copy(isPaid = false)
                        } else {
                            entry
                        }
                    }
                    if (changed) {
                        targetExpense = expense
                        updatedEntries = updated
                        break
                    }
                } catch (e: Exception) {
                    // skip malformed row
                }
            }

            val expense = targetExpense
            val updated = updatedEntries
            if (expense != null && updated != null) {
                repository.updateDailyExpense(
                    expense.copy(
                        otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), updated)
                    )
                )
            }
            repository.deleteVendorPayment(payment)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Range selection
    // ---------------------------------------------------------------------------------------------

    fun setDateRange(range: DateRange) {
        _dateRange.value = range
    }

    fun setCustomDateRange(startDate: LocalDate, endDate: LocalDate) {
        _dateRange.value = DateRange.Custom(startDate, endDate)
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true) }
                // Flows are live; nothing to reload explicitly.
                _uiState.update { it.copy(isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ---------------------------------------------------------------------------------------------
    // Clone a day's labour to another date
    // ---------------------------------------------------------------------------------------------

    /**
     * Creates a new day entry on [targetDate] with the same worker groups as [source]
     * (overtime cleared). Other expenses, income and advances are not copied.
     */
    fun cloneExpense(source: DailyExpense, targetDate: LocalDate) {
        viewModelScope.launch {
            try {
                val strippedGroups = try {
                    Json.decodeFromString(ListSerializer(WorkerGroupEntry.serializer()), source.workerGroups)
                        .map { it.copy(overtimeHours = 0, overtimeWagePerHour = BigDecimal.ZERO) }
                } catch (e: Exception) {
                    emptyList()
                }
                val strippedGroupsJson = Json.encodeToString(ListSerializer(WorkerGroupEntry.serializer()), strippedGroups)
                val newLaborCost = strippedGroups.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.calculateTotalGroupCost()) }

                val cloned = source.copy(
                    id = 0,
                    date = targetDate.format(iso),
                    totalLaborCost = newLaborCost,
                    totalOvertimeCost = BigDecimal.ZERO,
                    otherExpenses = "[]",
                    totalOtherExpensesCost = BigDecimal.ZERO,
                    incomeEntries = "[]",
                    totalIncome = BigDecimal.ZERO,
                    excessBalance = BigDecimal.ZERO,
                    advanceAmount = BigDecimal.ZERO,
                    advanceReason = "",
                    workerGroups = strippedGroupsJson,
                    advanceEntries = "[]",
                    weeklyPaymentDone = BigDecimal.ZERO,
                    createdAt = LocalDateTime.now().toString(),
                    updatedAt = LocalDateTime.now().toString(),
                    expenseAdditionType = "clone"
                )
                repository.insertDailyExpense(cloned)
                _uiState.update { it.copy(cloneSuccessDate = targetDate) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Clone failed: ${e.message}") }
            }
        }
    }

    fun clearCloneSuccess() {
        _uiState.update { it.copy(cloneSuccessDate = null) }
    }

    /** Next day, skipping Sunday. */
    fun nextWorkday(from: LocalDate): LocalDate {
        var next = from.plusDays(1)
        if (next.dayOfWeek == DayOfWeek.SUNDAY) next = next.plusDays(1)
        return next
    }

    // ---------------------------------------------------------------------------------------------
    // Date range helpers
    // ---------------------------------------------------------------------------------------------

    fun getStartDate(range: DateRange): LocalDate {
        val today = LocalDate.now()
        return when (range) {
            is DateRange.CurrentWeek -> today.with(DayOfWeek.MONDAY)
            is DateRange.Last7Days -> today.minusDays(7)
            is DateRange.Last30Days -> today.minusDays(30)
            is DateRange.Last90Days -> today.minusDays(90)
            is DateRange.LastYear -> today.minusYears(1)
            is DateRange.ThisMonth -> today.withDayOfMonth(1)
            is DateRange.LastMonth -> today.minusMonths(1).withDayOfMonth(1)
            is DateRange.ThisYear -> today.withDayOfYear(1)
            is DateRange.Custom -> range.startDate
        }
    }

    fun getEndDate(range: DateRange): LocalDate {
        val today = LocalDate.now()
        return when (range) {
            is DateRange.CurrentWeek -> today.with(DayOfWeek.SATURDAY)
            is DateRange.Last7Days -> today
            is DateRange.Last30Days -> today
            is DateRange.Last90Days -> today
            is DateRange.LastYear -> today
            is DateRange.ThisMonth -> today
            is DateRange.LastMonth -> {
                val lastMonth = today.minusMonths(1)
                lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
            }
            is DateRange.ThisYear -> today
            is DateRange.Custom -> range.endDate
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Derived report data
    // ---------------------------------------------------------------------------------------------

    fun getTopExpenseCategories(expenses: List<DailyExpense>): List<CategoryExpense> {
        val totals = LinkedHashMap<String, BigDecimal>()
        expenses.forEach { expense ->
            try {
                Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                    .forEach { entry ->
                        totals[entry.typeName] = totals.getOrDefault(entry.typeName, BigDecimal.ZERO).add(entry.amount)
                    }
            } catch (e: Exception) {
                // skip
            }
        }
        return totals.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { CategoryExpense(it.key, it.value) }
    }

    fun getIncomeTrend(expenses: List<DailyExpense>): List<DailyTrend> =
        expenses.sortedBy { it.date }.map { DailyTrend(it.date, it.totalIncome) }

    fun getExpenseTrend(expenses: List<DailyExpense>): List<DailyTrend> =
        expenses.sortedBy { it.date }.map {
            DailyTrend(
                it.date,
                it.totalLaborCost.add(it.totalOvertimeCost).add(it.totalOtherExpensesCost).add(it.advanceAmount)
            )
        }

    fun getProfitLossTrend(expenses: List<DailyExpense>): List<DailyTrend> =
        expenses.sortedBy { it.date }.map { DailyTrend(it.date, it.calculateNetAmount()) }

    fun getIncomeBreakdown(expenses: List<DailyExpense>): List<IncomeBreakdown> {
        val map = LinkedHashMap<String, IncomeBreakdown>()
        expenses.forEach { expense ->
            try {
                Json.decodeFromString(ListSerializer(IncomeEntry.serializer()), expense.incomeEntries)
                    .forEach { entry ->
                        val current = map.getOrDefault(entry.typeName, IncomeBreakdown(entry.typeName))
                        map[entry.typeName] = current.copy(
                            totalAmount = current.totalAmount.add(entry.amount),
                            totalWeight = current.totalWeight + entry.weight
                        )
                    }
            } catch (e: Exception) {
                // skip
            }
        }
        return map.values.sortedByDescending { it.totalAmount }
    }

    fun getWeeklySpecificWorkers(expenses: List<DailyExpense>): List<WorkerTypeSpecific> {
        val map = LinkedHashMap<String, WorkerTypeSpecific>()
        expenses.forEach { expense ->
            try {
                Json.decodeFromString(ListSerializer(WorkerGroupEntry.serializer()), expense.workerGroups)
                    .forEach { group ->
                        val current = map.getOrDefault(group.workerTypeName, WorkerTypeSpecific(group.workerTypeName))
                        val baseCost = group.wagePerDay.multiply(BigDecimal.valueOf(group.count.toLong()))
                        val overtimeCost = group.overtimeWagePerHour
                            .multiply(BigDecimal.valueOf(group.overtimeHours.toLong()))
                            .multiply(BigDecimal.valueOf(group.count.toLong()))
                        map[group.workerTypeName] = current.copy(
                            totalCount = current.totalCount + group.count,
                            totalBaseCost = current.totalBaseCost.add(baseCost),
                            totalOvertimeCost = current.totalOvertimeCost.add(overtimeCost)
                        )
                    }
            } catch (e: Exception) {
                // skip
            }
        }
        return map.values.sortedBy { it.workerTypeName }
    }

    fun getWeeklySpecificOtherExpenses(expenses: List<DailyExpense>): List<OtherExpenseSpecific> {
        val map = LinkedHashMap<String, OtherExpenseSpecific>()
        expenses.forEach { expense ->
            try {
                Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                    .forEach { entry ->
                        val key = "${entry.typeName}||${entry.subtypeName}"
                        val current = map.getOrDefault(key, OtherExpenseSpecific(entry.typeName, entry.subtypeName))
                        map[key] = current.copy(
                            totalQuantity = current.totalQuantity + entry.quantity,
                            totalAmount = current.totalAmount.add(entry.amount)
                        )
                    }
            } catch (e: Exception) {
                // skip
            }
        }
        return map.values.sortedBy { it.typeName }
    }

    fun getIndividualOtherExpenses(expenses: List<DailyExpense>): List<Pair<String, OtherExpenseEntry>> {
        return expenses.sortedBy { it.date }.flatMap { expense ->
            try {
                Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                    .map { expense.date to it }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun getWeeklySpecificSettlement(expenses: List<DailyExpense>): BigDecimal =
        expenses.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.weeklyPaymentDone) }

    fun getWeeklySpecificAdvances(expenses: List<DailyExpense>): List<AdvanceSpecific> {
        val map = LinkedHashMap<Pair<String, String>, AdvanceSpecific>()
        expenses.forEach { expense ->
            try {
                Json.decodeFromString(ListSerializer(AdvanceEntry.serializer()), expense.advanceEntries)
                    .forEach { entry ->
                        val recipient = entry.recipientName.ifBlank { "General" }
                        val key = recipient to entry.reason
                        val current = map.getOrDefault(key, AdvanceSpecific(recipient, entry.reason))
                        map[key] = current.copy(totalAmount = current.totalAmount.add(entry.amount))
                    }
            } catch (e: Exception) {
                // skip
            }
        }
        return map.values.sortedWith(compareBy({ it.recipientName }, { it.reason }))
    }

    fun getWeeklyWorkerSummary(expenses: List<DailyExpense>): List<WorkerTypeSummary> {
        val map = LinkedHashMap<Pair<String, String>, WorkerTypeSummary>()
        expenses.forEach { expense ->
            try {
                Json.decodeFromString(ListSerializer(WorkerGroupEntry.serializer()), expense.workerGroups)
                    .forEach { group ->
                        val key = group.workerTypeName to group.comments
                        val current = map.getOrDefault(key, WorkerTypeSummary(group.workerTypeName, group.comments))
                        map[key] = current.copy(
                            totalCount = current.totalCount + group.count,
                            totalCost = current.totalCost.add(group.calculateTotalGroupCost())
                        )
                    }
            } catch (e: Exception) {
                // skip
            }
        }
        return map.values.sortedWith(compareBy({ it.workerType }, { it.comment }))
    }
}
