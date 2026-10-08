package com.santhomach.plantationledger.testutil

import com.santhomach.plantationledger.data.model.AdvanceEntry
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.IncomeEntry
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.WorkerGroupEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.math.BigDecimal

/** Runs viewModelScope coroutines on a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

fun money(value: Number): BigDecimal = BigDecimal(value.toString())

/** Compares amounts by value, so 4600 and 4600.0 are equal. */
fun assertMoney(expected: Number, actual: BigDecimal?, message: String = "") {
    org.junit.Assert.assertNotNull(message, actual)
    org.junit.Assert.assertTrue(
        "$message expected <$expected> but was <$actual>",
        money(expected).compareTo(actual) == 0
    )
}

fun group(
    type: String,
    count: Int,
    wage: Number,
    task: String = "Weeding",
    otHours: Int = 0,
    otRate: Number = 0,
    typeId: Int = 1,
    comments: String = ""
) = WorkerGroupEntry(
    workerTypeId = typeId,
    workerTypeName = type,
    count = count,
    wagePerDay = money(wage),
    overtimeHours = otHours,
    overtimeWagePerHour = money(otRate),
    taskPerformed = task,
    comments = comments
)

fun other(type: String, amount: Number, subtype: String? = null, paid: Boolean = false, quantity: Double = 1.0, typeId: Int = 1) =
    OtherExpenseEntry(
        expenseTypeId = typeId,
        typeName = type,
        subtypeName = subtype,
        amount = money(amount),
        quantity = quantity,
        isPaid = paid
    )

fun income(type: String, amount: Number, weight: Double = 0.0, typeId: Int = 1) =
    IncomeEntry(incomeTypeId = typeId, typeName = type, amount = money(amount), weight = weight)

fun advance(amount: Number, recipient: String = "", reason: String = "") =
    AdvanceEntry(amount = money(amount), recipientName = recipient, reason = reason)

/** A daily record whose stored totals match its entries, the way the app saves it. */
fun dailyExpense(
    date: String,
    id: Int = 0,
    groups: List<WorkerGroupEntry> = emptyList(),
    others: List<OtherExpenseEntry> = emptyList(),
    incomes: List<IncomeEntry> = emptyList(),
    advances: List<AdvanceEntry> = emptyList(),
    settlement: Number = 0,
    comments: String = ""
) = DailyExpense(
    id = id,
    date = date,
    workerGroups = Json.encodeToString(ListSerializer(WorkerGroupEntry.serializer()), groups),
    totalLaborCost = groups.fold(BigDecimal.ZERO) { a, g -> a.add(g.wagePerDay.multiply(BigDecimal(g.count))) },
    totalOvertimeCost = groups.fold(BigDecimal.ZERO) { a, g ->
        a.add(g.overtimeWagePerHour.multiply(BigDecimal(g.overtimeHours)).multiply(BigDecimal(g.count)))
    },
    otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), others),
    totalOtherExpensesCost = others.fold(BigDecimal.ZERO) { a, o -> a.add(o.amount) },
    incomeEntries = Json.encodeToString(ListSerializer(IncomeEntry.serializer()), incomes),
    totalIncome = incomes.fold(BigDecimal.ZERO) { a, i -> a.add(i.amount) },
    advanceEntries = Json.encodeToString(ListSerializer(AdvanceEntry.serializer()), advances),
    advanceAmount = advances.fold(BigDecimal.ZERO) { a, i -> a.add(i.amount) },
    weeklyPaymentDone = money(settlement),
    comments = comments
)

fun otherEntriesOf(expense: DailyExpense): List<OtherExpenseEntry> =
    Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), expense.otherExpenses)

fun groupsOf(expense: DailyExpense): List<WorkerGroupEntry> =
    Json.decodeFromString(ListSerializer(WorkerGroupEntry.serializer()), expense.workerGroups)
