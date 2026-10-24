package com.santhomach.plantationledger.testutil

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.withTransaction
import com.santhomach.plantationledger.data.AppDatabase
import com.santhomach.plantationledger.data.dao.DailyExpenseDao
import com.santhomach.plantationledger.data.dao.ExcessBalanceDao
import com.santhomach.plantationledger.data.dao.ExpenseSubtypeDao
import com.santhomach.plantationledger.data.dao.ExpenseTypeDao
import com.santhomach.plantationledger.data.dao.IncomeTypeDao
import com.santhomach.plantationledger.data.dao.PermanentWorkerDao
import com.santhomach.plantationledger.data.dao.VendorPaymentDao
import com.santhomach.plantationledger.data.dao.WeeklyFundsDao
import com.santhomach.plantationledger.data.dao.WeeklySettlementDao
import com.santhomach.plantationledger.data.dao.WorkTaskDao
import com.santhomach.plantationledger.data.dao.WorkerPaymentDao
import com.santhomach.plantationledger.data.dao.WorkerTypeDao
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExcessBalance
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.model.WeeklySettlement
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerPayment
import com.santhomach.plantationledger.data.model.WorkerType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/** One table with auto-generated ids and Room's REPLACE-on-conflict insert. */
class Table<T>(private val idOf: (T) -> Int, private val withId: (T, Int) -> T) {
    private val rows = MutableStateFlow<Map<Int, T>>(emptyMap())
    private var nextId = 1

    fun insert(row: T): Long {
        val id = idOf(row).takeIf { it != 0 } ?: nextId
        nextId = maxOf(nextId, id + 1)
        rows.value = rows.value + (id to withId(row, id))
        return id.toLong()
    }

    fun update(row: T) {
        if (rows.value.containsKey(idOf(row))) rows.value = rows.value + (idOf(row) to row)
    }

    fun delete(row: T) {
        rows.value = rows.value - idOf(row)
    }

    fun all(): List<T> = rows.value.values.toList()
    fun flow() = rows.map { it.values.toList() }
}

/**
 * An in-memory stand-in for [AppDatabase]: every DAO method the repository and the backup code use
 * reads and writes plain lists. [writesOutsideTransaction] records any write made while
 * [withTransaction] is not running (call [stubTransactions] first).
 */
class FakeDatabase {
    val expenses = Table<DailyExpense>({ it.id }, { r, id -> r.copy(id = id) })
    val expenseTypes = Table<ExpenseType>({ it.id }, { r, id -> r.copy(id = id) })
    val expenseSubtypes = Table<ExpenseSubtype>({ it.id }, { r, id -> r.copy(id = id) })
    val incomeTypes = Table<IncomeType>({ it.id }, { r, id -> r.copy(id = id) })
    val workerTypes = Table<WorkerType>({ it.id }, { r, id -> r.copy(id = id) })
    val permanentWorkers = Table<PermanentWorker>({ it.id }, { r, id -> r.copy(id = id) })
    val workTasks = Table<WorkTask>({ it.id }, { r, id -> r.copy(id = id) })
    val settlements = Table<WeeklySettlement>({ it.id }, { r, id -> r.copy(id = id) })
    val excessBalances = Table<ExcessBalance>({ it.id }, { r, id -> r.copy(id = id) })
    val workerPayments = Table<WorkerPayment>({ it.id }, { r, id -> r.copy(id = id) })
    val weeklyFunds = Table<WeeklyFunds>({ it.id }, { r, id -> r.copy(id = id) })
    val vendorPayments = Table<VendorPayment>({ it.id }, { r, id -> r.copy(id = id) })

    private var inTransaction = false
    val writesOutsideTransaction = mutableListOf<String>()

    private fun write(table: String) {
        if (!inTransaction) writesOutsideTransaction += table
    }

    val db: AppDatabase = mockk(relaxed = true)

    init {
        val dailyDao = mockk<DailyExpenseDao>(relaxed = true)
        coEvery { dailyDao.insert(any()) } answers { write("daily"); expenses.insert(firstArg()) }
        coEvery { dailyDao.update(any()) } answers { write("daily"); expenses.update(firstArg()) }
        coEvery { dailyDao.delete(any()) } answers { write("daily"); expenses.delete(firstArg()) }
        coEvery { dailyDao.getRecent(any()) } answers {
            expenses.all().sortedByDescending { it.date }.take(firstArg())
        }
        coEvery { dailyDao.getByDateRange(any(), any()) } answers {
            expenses.all()
                .filter { it.date >= firstArg<String>() && it.date <= secondArg<String>() }
                .sortedBy { it.date }
        }
        every { dailyDao.getByDateRangeFlow(any(), any()) } answers {
            val start = firstArg<String>()
            val end = secondArg<String>()
            expenses.flow()
                .map { list -> list.filter { it.date in start..end }.sortedBy { it.date } }
        }
        every { db.dailyExpenseDao() } returns dailyDao

        val typeDao = mockk<ExpenseTypeDao>(relaxed = true)
        coEvery { typeDao.insert(any()) } answers {
            write("expense_types"); expenseTypes.insert(
            firstArg()
        )
        }
        coEvery { typeDao.getAll() } answers { expenseTypes.all() }
        coEvery { typeDao.getAllActive() } answers { expenseTypes.all().filter { it.isActive } }
        every { db.expenseTypeDao() } returns typeDao

        val subtypeDao = mockk<ExpenseSubtypeDao>(relaxed = true)
        coEvery { subtypeDao.insert(any()) } answers {
            write("expense_subtypes"); expenseSubtypes.insert(
            firstArg()
        )
        }
        coEvery { subtypeDao.getAll() } answers { expenseSubtypes.all() }
        coEvery { subtypeDao.getAllActive() } answers {
            expenseSubtypes.all().filter { it.isActive }
        }
        every { db.expenseSubtypeDao() } returns subtypeDao

        val incomeDao = mockk<IncomeTypeDao>(relaxed = true)
        coEvery { incomeDao.insert(any()) } answers {
            write("income_types"); incomeTypes.insert(
            firstArg()
        )
        }
        coEvery { incomeDao.getAll() } answers { incomeTypes.all() }
        coEvery { incomeDao.getAllActive() } answers { incomeTypes.all().filter { it.isActive } }
        every { db.incomeTypeDao() } returns incomeDao

        val workerTypeDao = mockk<WorkerTypeDao>(relaxed = true)
        coEvery { workerTypeDao.insert(any()) } answers {
            write("worker_types"); workerTypes.insert(
            firstArg()
        )
        }
        coEvery { workerTypeDao.getAll() } answers { workerTypes.all() }
        coEvery { workerTypeDao.getAllActive() } answers {
            workerTypes.all().filter { it.isActive }
        }
        every { db.workerTypeDao() } returns workerTypeDao

        val permanentDao = mockk<PermanentWorkerDao>(relaxed = true)
        coEvery { permanentDao.insert(any()) } answers {
            write("permanent_workers"); permanentWorkers.insert(
            firstArg()
        )
        }
        coEvery { permanentDao.getAll() } answers { permanentWorkers.all() }
        coEvery { permanentDao.getAllActive() } answers {
            permanentWorkers.all().filter { it.isActive }
        }
        every { db.permanentWorkerDao() } returns permanentDao

        val taskDao = mockk<WorkTaskDao>(relaxed = true)
        coEvery { taskDao.insert(any()) } answers { write("work_tasks"); workTasks.insert(firstArg()) }
        coEvery { taskDao.getAll() } answers { workTasks.all() }
        coEvery { taskDao.getAllActive() } answers { workTasks.all().filter { it.isActive } }
        every { db.workTaskDao() } returns taskDao

        val settlementDao = mockk<WeeklySettlementDao>(relaxed = true)
        coEvery { settlementDao.insert(any()) } answers {
            write("weekly_settlements"); settlements.insert(
            firstArg()
        )
        }
        coEvery { settlementDao.getRecent(any()) } answers { settlements.all().take(firstArg()) }
        every { db.weeklySettlementDao() } returns settlementDao

        val balanceDao = mockk<ExcessBalanceDao>(relaxed = true)
        coEvery { balanceDao.insert(any()) } answers {
            write("excess_balances"); excessBalances.insert(
            firstArg()
        )
        }
        coEvery { balanceDao.getByDateRange(any(), any()) } answers { excessBalances.all() }
        every { db.excessBalanceDao() } returns balanceDao

        val paymentDao = mockk<WorkerPaymentDao>(relaxed = true)
        coEvery { paymentDao.insert(any()) } answers {
            write("worker_payments"); workerPayments.insert(
            firstArg()
        )
        }
        coEvery { paymentDao.getAll() } answers { workerPayments.all() }
        every { db.workerPaymentDao() } returns paymentDao

        val fundsDao = mockk<WeeklyFundsDao>(relaxed = true)
        coEvery { fundsDao.insert(any()) } answers {
            write("weekly_funds"); weeklyFunds.insert(
            firstArg()
        )
        }
        coEvery { fundsDao.getAll() } answers { weeklyFunds.all() }
        every { db.weeklyFundsDao() } returns fundsDao

        val vendorDao = mockk<VendorPaymentDao>(relaxed = true)
        coEvery { vendorDao.insert(any()) } answers {
            write("vendor_payments"); vendorPayments.insert(
            firstArg()
        )
        }
        coEvery { vendorDao.delete(any()) } answers {
            write("vendor_payments"); vendorPayments.delete(
            firstArg()
        )
        }
        coEvery { vendorDao.getAll() } answers { vendorPayments.all() }
        every { db.vendorPaymentDao() } returns vendorDao
    }

    /** Makes Room's `withTransaction` run its block (needs `unmockkAll()` after the test). */
    fun stubTransactions() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            inTransaction = true
            try {
                secondArg<suspend () -> Any?>().invoke()
            } finally {
                inTransaction = false
            }
        }
    }
}

/** A DataStore kept in memory. */
class FakeDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
