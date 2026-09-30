package com.santhomach.plantationledger.data.repository

import com.santhomach.plantationledger.data.AppDatabase
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExcessBalance
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.model.WeeklySettlement
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerPayment
import com.santhomach.plantationledger.data.model.WorkerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for all database access used by the view models.
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val database: AppDatabase
) {
    private val expenseDao = database.dailyExpenseDao()
    private val expenseTypeDao = database.expenseTypeDao()
    private val expenseSubtypeDao = database.expenseSubtypeDao()
    private val incomeTypeDao = database.incomeTypeDao()
    private val workerTypeDao = database.workerTypeDao()
    private val permanentWorkerDao = database.permanentWorkerDao()
    private val settlementDao = database.weeklySettlementDao()
    private val balanceDao = database.excessBalanceDao()
    private val taskDao = database.workTaskDao()
    private val paymentDao = database.workerPaymentDao()
    private val fundsDao = database.weeklyFundsDao()
    private val vendorPaymentDao = database.vendorPaymentDao()

    // ---------------------------------------------------------------------------------------------
    // Daily expenses
    // ---------------------------------------------------------------------------------------------

    suspend fun insertDailyExpense(expense: DailyExpense): Long {
        return expenseDao.insert(expense.copy(updatedAt = LocalDateTime.now().toString()))
    }

    suspend fun updateDailyExpense(expense: DailyExpense) {
        expenseDao.update(expense.copy(updatedAt = LocalDateTime.now().toString()))
    }

    suspend fun deleteDailyExpense(expense: DailyExpense) {
        expenseDao.delete(expense)
    }

    suspend fun getDailyExpenseById(id: Int): DailyExpense? = expenseDao.getById(id)

    suspend fun getDailyExpensesByDate(date: String): List<DailyExpense> = expenseDao.getByDate(date)

    fun getDailyExpensesByDateFlow(date: String): Flow<List<DailyExpense>> = expenseDao.getByDateFlow(date)

    suspend fun getDailyExpensesByDateRange(startDate: String, endDate: String): List<DailyExpense> =
        expenseDao.getByDateRange(startDate, endDate)

    fun getDailyExpensesByDateRangeFlow(startDate: String, endDate: String): Flow<List<DailyExpense>> =
        expenseDao.getByDateRangeFlow(startDate, endDate)

    suspend fun getRecentExpenses(limit: Int = 50): List<DailyExpense> = expenseDao.getRecent(limit)

    suspend fun getDailyExpensesBeforeDate(date: String): List<DailyExpense> = expenseDao.getBeforeDate(date)

    suspend fun searchDailyExpenses(query: String): List<DailyExpense> = expenseDao.searchExpenses(query)

    fun getRecentExpensesFlow(limit: Int = 50): Flow<List<DailyExpense>> = expenseDao.getRecentFlow(limit)

    // ---------------------------------------------------------------------------------------------
    // Expense types / subtypes
    // ---------------------------------------------------------------------------------------------

    suspend fun insertExpenseType(type: ExpenseType): Long = expenseTypeDao.insert(type)
    suspend fun updateExpenseType(type: ExpenseType) = expenseTypeDao.update(type)
    suspend fun deleteExpenseType(type: ExpenseType) = expenseTypeDao.delete(type)
    suspend fun getAllActiveExpenseTypes(): List<ExpenseType> = expenseTypeDao.getAllActive()
    fun getAllActiveExpenseTypesFlow(): Flow<List<ExpenseType>> = expenseTypeDao.getAllActiveFlow()

    suspend fun insertExpenseSubtype(subtype: ExpenseSubtype): Long = expenseSubtypeDao.insert(subtype)
    suspend fun updateExpenseSubtype(subtype: ExpenseSubtype) = expenseSubtypeDao.update(subtype)
    suspend fun deleteExpenseSubtype(subtype: ExpenseSubtype) = expenseSubtypeDao.delete(subtype)
    suspend fun getAllActiveExpenseSubtypes(): List<ExpenseSubtype> = expenseSubtypeDao.getAllActive()
    suspend fun getExpenseSubtypeByName(name: String): ExpenseSubtype? = expenseSubtypeDao.getByName(name)
    fun getAllActiveExpenseSubtypesFlow(): Flow<List<ExpenseSubtype>> = expenseSubtypeDao.getAllActiveFlow()

    // ---------------------------------------------------------------------------------------------
    // Income types
    // ---------------------------------------------------------------------------------------------

    suspend fun insertIncomeType(type: IncomeType): Long = incomeTypeDao.insert(type)
    suspend fun updateIncomeType(type: IncomeType) = incomeTypeDao.update(type)
    suspend fun deleteIncomeType(type: IncomeType) = incomeTypeDao.delete(type)
    suspend fun getAllActiveIncomeTypes(): List<IncomeType> = incomeTypeDao.getAllActive()
    fun getAllActiveIncomeTypesFlow(): Flow<List<IncomeType>> = incomeTypeDao.getAllActiveFlow()

    // ---------------------------------------------------------------------------------------------
    // Worker types
    // ---------------------------------------------------------------------------------------------

    suspend fun insertWorkerType(type: WorkerType): Long = workerTypeDao.insert(type)
    suspend fun updateWorkerType(type: WorkerType) = workerTypeDao.update(type)
    suspend fun deleteWorkerType(type: WorkerType) = workerTypeDao.delete(type)
    suspend fun getAllActiveWorkerTypes(): List<WorkerType> = workerTypeDao.getAllActive()
    fun getAllActiveWorkerTypesFlow(): Flow<List<WorkerType>> = workerTypeDao.getAllActiveFlow()

    // ---------------------------------------------------------------------------------------------
    // Permanent workers & their payments
    // ---------------------------------------------------------------------------------------------

    suspend fun insertPermanentWorker(worker: PermanentWorker): Long = permanentWorkerDao.insert(worker)
    suspend fun updatePermanentWorker(worker: PermanentWorker) = permanentWorkerDao.update(worker)
    suspend fun deletePermanentWorker(worker: PermanentWorker) = permanentWorkerDao.delete(worker)
    suspend fun getAllActivePermanentWorkers(): List<PermanentWorker> = permanentWorkerDao.getAllActive()
    fun getAllActivePermanentWorkersFlow(): Flow<List<PermanentWorker>> = permanentWorkerDao.getAllActiveFlow()

    suspend fun insertWorkerPayment(payment: WorkerPayment): Long = paymentDao.insert(payment)
    suspend fun updateWorkerPayment(payment: WorkerPayment) = paymentDao.update(payment)
    suspend fun deleteWorkerPayment(payment: WorkerPayment) = paymentDao.delete(payment)
    fun getAllPaymentsFlow(): Flow<List<WorkerPayment>> = paymentDao.getAllPaymentsFlow()
    suspend fun getPaymentByWorkerAndDate(workerId: Int, paymentDate: String): WorkerPayment? =
        paymentDao.getByWorkerAndDate(workerId, paymentDate)

    // ---------------------------------------------------------------------------------------------
    // Weekly settlements
    // ---------------------------------------------------------------------------------------------

    suspend fun insertWeeklySettlement(settlement: WeeklySettlement): Long {
        return settlementDao.insert(settlement.copy(updatedAt = LocalDateTime.now().toString()))
    }

    suspend fun updateWeeklySettlement(settlement: WeeklySettlement) {
        settlementDao.update(settlement.copy(updatedAt = LocalDateTime.now().toString()))
    }

    suspend fun getWeeklySettlementById(id: Int): WeeklySettlement? = settlementDao.getById(id)
    suspend fun getWeeklySettlementByDate(date: String): WeeklySettlement? = settlementDao.getBySettlementDate(date)
    suspend fun getWeeklySettlementsByDateRange(startDate: String, endDate: String): List<WeeklySettlement> =
        settlementDao.getByDateRange(startDate, endDate)
    fun getAllWeeklySettlementsFlow(): Flow<List<WeeklySettlement>> = settlementDao.getAllFlow()

    // ---------------------------------------------------------------------------------------------
    // Excess balances
    // ---------------------------------------------------------------------------------------------

    suspend fun insertExcessBalance(balance: ExcessBalance): Long = balanceDao.insert(balance)
    suspend fun updateExcessBalance(balance: ExcessBalance) = balanceDao.update(balance)
    suspend fun getUnsettledBalances(): List<ExcessBalance> = balanceDao.getUnsettled()
    fun getUnsettledBalancesFlow(): Flow<List<ExcessBalance>> = balanceDao.getUnsettledFlow()

    suspend fun getTotalUnsettledBalance(): BigDecimal {
        return balanceDao.getTotalUnsettledBalance()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
    }

    // ---------------------------------------------------------------------------------------------
    // Work tasks
    // ---------------------------------------------------------------------------------------------

    suspend fun insertWorkTask(task: WorkTask): Long = taskDao.insert(task)
    suspend fun updateWorkTask(task: WorkTask) = taskDao.update(task)
    suspend fun deleteWorkTask(task: WorkTask) = taskDao.delete(task)
    suspend fun getAllActiveWorkTasks(): List<WorkTask> = taskDao.getAllActive()
    fun getAllActiveWorkTasksFlow(): Flow<List<WorkTask>> = taskDao.getAllActiveFlow()

    // ---------------------------------------------------------------------------------------------
    // Weekly funds
    // ---------------------------------------------------------------------------------------------

    suspend fun insertWeeklyFunds(funds: WeeklyFunds): Long = fundsDao.insert(funds)
    suspend fun updateWeeklyFunds(funds: WeeklyFunds) = fundsDao.update(funds)
    suspend fun deleteWeeklyFunds(funds: WeeklyFunds) = fundsDao.delete(funds)
    fun getAllWeeklyFundsFlow(): Flow<List<WeeklyFunds>> = fundsDao.getAllFlow()

    // ---------------------------------------------------------------------------------------------
    // Vendor payments
    // ---------------------------------------------------------------------------------------------

    suspend fun insertVendorPayment(payment: VendorPayment): Long = vendorPaymentDao.insert(payment)
    suspend fun deleteVendorPayment(payment: VendorPayment) = vendorPaymentDao.delete(payment)
    fun getAllVendorPaymentsFlow(): Flow<List<VendorPayment>> = vendorPaymentDao.getAllFlow()
    suspend fun getAllVendorPayments(): List<VendorPayment> = vendorPaymentDao.getAll()
    fun getVendorPaymentsBySourceExpenseDateFlow(date: String): Flow<List<VendorPayment>> =
        vendorPaymentDao.getBySourceExpenseDateFlow(date)
    suspend fun getVendorPaymentsBySourceExpenseDate(date: String): List<VendorPayment> =
        vendorPaymentDao.getAllBySourceExpenseDate(date)
    fun getVendorPaymentsByDateFlow(date: String): Flow<List<VendorPayment>> =
        vendorPaymentDao.getByPaymentDateFlow(date)
    fun getVendorPaymentsByDateRangeFlow(startDate: String, endDate: String): Flow<List<VendorPayment>> =
        vendorPaymentDao.getByPaymentDateRangeFlow(startDate, endDate)

    /**
     * One-time migration for vendor payments recorded before payments were linked to a specific
     * expense line. The legacy (unlinked) total is applied, oldest expense first, to unpaid
     * Pesticide / Fertilizer entries by marking them paid, after which the legacy rows are removed.
     */
    suspend fun migrateVendorLegacyPayments() {
        val legacyPayments = vendorPaymentDao.getAll().filter { it.sourceExpenseDate == null }
        if (legacyPayments.isEmpty()) return

        val legacyTotal = legacyPayments.fold(BigDecimal.ZERO) { acc, payment -> acc.add(payment.amount) }
        if (legacyTotal.signum() <= 0) return

        val allExpenses = expenseDao.getByDateRange("1900-01-01", "2100-12-31").sortedBy { it.date }

        // Amount that has already been marked as paid on vendor-type entries
        val alreadyMarked = allExpenses.fold(BigDecimal.ZERO) { acc, expense ->
            val paidOnThisDay = try {
                Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)
                    .filter { entry ->
                        entry.isPaid && (
                            entry.typeName.contains("Pesticide", ignoreCase = true) ||
                                entry.typeName.contains("Fertilizer", ignoreCase = true)
                            )
                    }
                    .fold(BigDecimal.ZERO) { sum, entry -> sum.add(entry.amount) }
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
            acc.add(paidOnThisDay)
        }

        var remaining = legacyTotal.subtract(alreadyMarked).max(BigDecimal.ZERO)

        if (remaining.signum() > 0) {
            for (expense in allExpenses) {
                if (remaining.signum() <= 0) break
                try {
                    val entries = Json.decodeFromString(
                        ListSerializer(OtherExpenseEntry.serializer()),
                        expense.otherExpenses
                    )
                    var changed = false
                    val updated = entries.map { entry ->
                        if (remaining.signum() > 0 && !entry.isPaid &&
                            (entry.typeName.contains("Pesticide", ignoreCase = true) ||
                                entry.typeName.contains("Fertilizer", ignoreCase = true))
                        ) {
                            remaining = remaining.subtract(entry.amount)
                            changed = true
                            entry.copy(isPaid = true)
                        } else {
                            entry
                        }
                    }
                    if (changed) {
                        updateDailyExpense(
                            expense.copy(
                                otherExpenses = Json.encodeToString(
                                    ListSerializer(OtherExpenseEntry.serializer()),
                                    updated
                                )
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip malformed entries
                }
            }
        }

        legacyPayments.forEach { vendorPaymentDao.delete(it) }
    }

    // ---------------------------------------------------------------------------------------------
    // Summaries
    // ---------------------------------------------------------------------------------------------

    fun getDailyExpenseSummaryFlow(startDate: String, endDate: String): Flow<ExpenseSummary> {
        return getDailyExpensesByDateRangeFlow(startDate, endDate).map { expenses ->
            val totalIncome = expenses.sumOf { it.totalIncome }
            val totalLaborCost = expenses.sumOf { it.totalLaborCost }
            val totalOvertimeCost = expenses.sumOf { it.totalOvertimeCost }
            val totalOtherExpenses = expenses.sumOf { it.totalOtherExpensesCost }
            val totalAdvanceAmount = expenses.sumOf { it.advanceAmount }
            val totalExcessBalance = expenses.sumOf { it.excessBalance }
            val totalWeeklyPayment = expenses.sumOf { it.weeklyPaymentDone }
            val netAmount = expenses.sumOf { it.calculateNetAmount() }
            val totalDays = expenses.size

            val averageDailyIncome = if (expenses.isEmpty()) {
                BigDecimal.ZERO
            } else {
                expenses.sumOf { it.totalIncome }
                    .divide(BigDecimal.valueOf(expenses.size.toLong()), RoundingMode.HALF_EVEN)
            }

            val averageDailyExpense = if (expenses.isEmpty()) {
                BigDecimal.ZERO
            } else {
                expenses.sumOf { expense ->
                    val dayExpense = expense.totalLaborCost
                        .add(expense.totalOvertimeCost)
                        .add(expense.totalOtherExpensesCost)
                        .add(expense.advanceAmount)
                        .add(expense.weeklyPaymentDone)
                        .subtract(expense.excessBalance)
                    dayExpense.coerceAtLeast(BigDecimal.ZERO)
                }.divide(BigDecimal.valueOf(expenses.size.toLong()), RoundingMode.HALF_EVEN)
            }

            ExpenseSummary(
                totalIncome = totalIncome,
                totalLaborCost = totalLaborCost,
                totalOvertimeCost = totalOvertimeCost,
                totalOtherExpenses = totalOtherExpenses,
                totalAdvanceAmount = totalAdvanceAmount,
                totalWeeklyPayment = totalWeeklyPayment,
                totalExcessBalance = totalExcessBalance,
                netAmount = netAmount,
                totalDays = totalDays,
                averageDailyIncome = averageDailyIncome,
                averageDailyExpense = averageDailyExpense
            )
        }
    }

    fun getWeeklyExpenseSummaryFlow(startDate: String, endDate: String): Flow<WeeklyExpenseSummary> {
        val weeks = try {
            val days = ChronoUnit.DAYS.between(LocalDate.parse(startDate), LocalDate.parse(endDate).plusDays(1))
            ((days + 6) / 7).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }

        return getDailyExpenseSummaryFlow(startDate, endDate).map { summary ->
            val weekCount = BigDecimal.valueOf(weeks.toLong())
            WeeklyExpenseSummary(
                totalIncome = summary.totalIncome,
                totalLaborCost = summary.totalLaborCost,
                totalOvertimeCost = summary.totalOvertimeCost,
                totalOtherExpenses = summary.totalOtherExpenses,
                totalAdvanceAmount = summary.totalAdvanceAmount,
                totalWeeklyPayment = summary.totalWeeklyPayment,
                totalExcessBalance = summary.totalExcessBalance,
                netAmount = summary.netAmount,
                totalWeeks = weeks,
                averageWeeklyIncome = summary.totalIncome.divide(weekCount, RoundingMode.HALF_EVEN),
                averageWeeklyExpense = summary.totalLaborCost
                    .add(summary.totalOvertimeCost)
                    .add(summary.totalOtherExpenses)
                    .divide(weekCount, RoundingMode.HALF_EVEN)
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Default master data
    // ---------------------------------------------------------------------------------------------

    /**
     * Seeds the master tables with sensible defaults for a cardamom plantation.
     * Existing entries (matched by name) are never duplicated.
     */
    suspend fun initializeDefaultData() {
        val defaultExpenseTypes = listOf(
            ExpenseType(typeName = "Pesticides", description = "Pesticide purchases"),
            ExpenseType(typeName = "Fuel", description = "Fuel purchases"),
            ExpenseType(typeName = "Fertilizers", description = "Fertilizer purchases"),
            ExpenseType(typeName = "Capital Expenses", description = "Capital Expenses"),
            ExpenseType(typeName = "Cardamom Drying", description = "Drying Cost"),
            ExpenseType(typeName = "Equipment", description = "Equipment maintenance and purchases"),
            ExpenseType(typeName = "Transportation", description = "Transport costs"),
            ExpenseType(typeName = "Utilities", description = "Electricity, water, etc."),
            ExpenseType(typeName = "Maintenance", description = "General maintenance"),
            ExpenseType(typeName = "PropertyTax", description = "Property Tax"),
            ExpenseType(typeName = "Other", description = "Miscellaneous expenses")
        )

        val defaultExpenseSubtypes = listOf(
            ExpenseSubtype(
                typeName = "Diesel",
                parentTypeName = ExpenseType(typeName = "Fuel", description = "Fuel purchases"),
                description = "Pesticide purchases"
            ),
            ExpenseSubtype(
                typeName = "Petrol",
                parentTypeName = ExpenseType(typeName = "Fuel", description = "Fuel purchases"),
                description = "Fertilizer purchases"
            )
        )

        for (type in defaultExpenseTypes) {
            val exists = expenseTypeDao.getAllActive().any { it.typeName == type.typeName }
            if (!exists) expenseTypeDao.insert(type)
        }

        for (subtype in defaultExpenseSubtypes) {
            val exists = expenseSubtypeDao.getAllActive().any { it.typeName == subtype.typeName }
            if (!exists) expenseSubtypeDao.insert(subtype)
        }

        val defaultIncomeTypes = listOf(
            IncomeType(typeName = "Cardamom Sales", description = "Cardamom harvest sales"),
            IncomeType(typeName = "Pepper Sales", description = "Pepper harvest sales"),
            IncomeType(typeName = "Other Income", description = "Miscellaneous income")
        )

        for (type in defaultIncomeTypes) {
            val exists = incomeTypeDao.getAllActive().any { it.typeName == type.typeName }
            if (!exists) incomeTypeDao.insert(type)
        }

        val defaultWorkerTypes = listOf(
            WorkerType(workerTypeName = "Malayali Male", dailyBasicWage = BigDecimal("500")),
            WorkerType(workerTypeName = "Bengali Male", dailyBasicWage = BigDecimal("500")),
            WorkerType(workerTypeName = "Malayali Female", dailyBasicWage = BigDecimal("450")),
            WorkerType(workerTypeName = "Bengali Female", dailyBasicWage = BigDecimal("450"))
        )

        for (type in defaultWorkerTypes) {
            val exists = workerTypeDao.getAllActive().any { it.workerTypeName == type.workerTypeName }
            if (!exists) workerTypeDao.insert(type)
        }

        val defaultTasks = listOf(
            WorkTask(taskName = "Spraying", description = "Spraying pesticides/fertilizers"),
            WorkTask(taskName = "Weeding", description = "Removing weeds"),
            WorkTask(taskName = "Harvesting", description = "Harvesting crops"),
            WorkTask(taskName = "Pruning", description = "Pruning plants"),
            WorkTask(taskName = "Planting", description = "Planting new saplings"),
            WorkTask(taskName = "Drying", description = "Drying cardamom/pepper"),
            WorkTask(taskName = "Other", description = "Other farm work")
        )

        for (task in defaultTasks) {
            val exists = taskDao.getAllActive().any { it.taskName == task.taskName }
            if (!exists) taskDao.insert(task)
        }
    }
}

/**
 * Aggregated totals for a date range.
 */
data class ExpenseSummary(
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalLaborCost: BigDecimal = BigDecimal.ZERO,
    val totalOvertimeCost: BigDecimal = BigDecimal.ZERO,
    val totalOtherExpenses: BigDecimal = BigDecimal.ZERO,
    val totalAdvanceAmount: BigDecimal = BigDecimal.ZERO,
    val totalWeeklyPayment: BigDecimal = BigDecimal.ZERO,
    val totalExcessBalance: BigDecimal = BigDecimal.ZERO,
    val netAmount: BigDecimal = BigDecimal.ZERO,
    val totalDays: Int = 0,
    val averageDailyIncome: BigDecimal = BigDecimal.ZERO,
    val averageDailyExpense: BigDecimal = BigDecimal.ZERO
)

/**
 * Aggregated totals for a date range expressed per week.
 */
data class WeeklyExpenseSummary(
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalLaborCost: BigDecimal = BigDecimal.ZERO,
    val totalOvertimeCost: BigDecimal = BigDecimal.ZERO,
    val totalOtherExpenses: BigDecimal = BigDecimal.ZERO,
    val totalAdvanceAmount: BigDecimal = BigDecimal.ZERO,
    val totalWeeklyPayment: BigDecimal = BigDecimal.ZERO,
    val totalExcessBalance: BigDecimal = BigDecimal.ZERO,
    val netAmount: BigDecimal = BigDecimal.ZERO,
    val totalWeeks: Int = 0,
    val averageWeeklyIncome: BigDecimal = BigDecimal.ZERO,
    val averageWeeklyExpense: BigDecimal = BigDecimal.ZERO
)
