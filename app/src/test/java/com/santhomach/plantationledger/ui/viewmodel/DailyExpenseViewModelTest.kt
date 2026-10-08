package com.santhomach.plantationledger.ui.viewmodel

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.santhomach.plantationledger.data.backup.DailyBackupManager
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.advance
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.groupsOf
import com.santhomach.plantationledger.testutil.money
import com.santhomach.plantationledger.testutil.other
import com.santhomach.plantationledger.testutil.otherEntriesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * The Daily Expense screen's arithmetic: labour, overtime, other expenses, income, advances,
 * the remaining balance, and what gets written to the database on save / delete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyExpenseViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository: ExpenseRepository = mockk(relaxed = true)
    private val dataStore: DataStore<Preferences> = mockk(relaxed = true)

    private val malayaliMale = WorkerType(id = 1, workerTypeName = "Malayali Male", dailyBasicWage = money(575))
    private val bengaliMale = WorkerType(id = 2, workerTypeName = "Bengali Male", dailyBasicWage = money(500))

    private fun viewModel(): DailyExpenseViewModel {
        every { dataStore.data } returns flowOf(emptyPreferences())
        every { repository.getAllActiveWorkerTypesFlow() } returns flowOf(listOf(malayaliMale, bengaliMale))
        every { repository.getAllActiveExpenseTypesFlow() } returns flowOf(listOf(ExpenseType(id = 3, typeName = "Fuel")))
        every { repository.getAllActiveIncomeTypesFlow() } returns flowOf(listOf(IncomeType(id = 4, typeName = "Cardamom Sales")))
        every { repository.getAllWeeklyFundsFlow() } returns flowOf(emptyList())
        return DailyExpenseViewModel(repository, mockk<Application>(relaxed = true), mockk<DailyBackupManager>(relaxed = true), dataStore)
    }

    /** Opens an existing record in the view model, as the screen does. */
    private fun DailyExpenseViewModel.open(expense: DailyExpense) {
        coEvery { repository.getDailyExpenseById(expense.id) } returns expense
        loadExpense(expense.id)
    }

    private val current get() = vm.currentExpense.value!!
    private lateinit var vm: DailyExpenseViewModel

    @Test
    fun newDateStartsAnEmptyRecordForThatDate() = runTest(main.dispatcher) {
        vm = viewModel()
        coEvery { repository.getDailyExpensesByDate("2026-10-08") } returns emptyList()

        vm.loadOrCreateExpense(LocalDate.parse("2026-10-08"))

        assertEquals("2026-10-08", current.date)
        assertEquals(0, current.id)
        assertTrue(vm.uiState.value.isEditing)
    }

    @Test
    fun workerGroupsDriveLabourAndOvertimeTotals() = runTest(main.dispatcher) {
        vm = viewModel()
        backgroundScope.launch { vm.workerTypes.collect {} }
        vm.open(dailyExpense("2026-10-07", id = 7))

        vm.addWorkerGroup(1, 8, money(575), 1, money(70), "Harvesting", "")
        vm.addWorkerGroup(2, 2, money(500), 0, money(0), "Harvesting", "")

        assertMoney(5_600, current.totalLaborCost)       // 8 x 575 + 2 x 500
        assertMoney(560, current.totalOvertimeCost)      // 8 workers x 1 hr x 70
        assertEquals(listOf("Malayali Male", "Bengali Male"), groupsOf(current).map { it.workerTypeName })

        vm.updateWorkerGroup(0, 1, 6, money(575), 0, money(0), "Harvesting", "")
        assertMoney(4_450, current.totalLaborCost)       // 6 x 575 + 2 x 500
        assertMoney(0, current.totalOvertimeCost)

        vm.removeWorkerGroup(1)
        assertMoney(3_450, current.totalLaborCost)
        assertEquals(1, groupsOf(current).size)
    }

    @Test
    fun extraOvertimeIsAddedOnTopOfWorkerOvertime() = runTest(main.dispatcher) {
        vm = viewModel()
        vm.open(dailyExpense("2026-10-07", id = 7, groups = listOf(group("Malayali Male", 5, 575, otHours = 2, otRate = 70))))

        vm.updateExtraOvertimeAmount(money(300))

        assertMoney(1_000, current.totalOvertimeCost)    // 5 x 2 x 70 + 300
    }

    @Test
    fun otherExpensesKeepTheirTotalInStep() = runTest(main.dispatcher) {
        vm = viewModel()
        backgroundScope.launch { vm.expenseTypes.collect {} }
        vm.open(dailyExpense("2026-10-07", id = 7))

        vm.addOtherExpense(3, money(625), notes = "Petrol")
        vm.addOtherExpense(3, money(200))
        assertMoney(825, current.totalOtherExpensesCost)
        assertEquals("Fuel", otherEntriesOf(current).first().typeName)

        vm.removeOtherExpense(0)
        assertMoney(200, current.totalOtherExpensesCost)
    }

    @Test
    fun newExpenseCategoryIsCreatedWhenEnteredByHand() = runTest(main.dispatcher) {
        vm = viewModel()
        coEvery { repository.insertExpenseType(any()) } returns 42L
        vm.open(dailyExpense("2026-10-07", id = 7))

        vm.addOtherExpense(0, money(3_350), customTypeName = "Cardamom Drying")

        coVerify { repository.insertExpenseType(match { it.typeName == "Cardamom Drying" }) }
        val entry = otherEntriesOf(current).single()
        assertEquals(42, entry.expenseTypeId)
        assertEquals("Cardamom Drying", entry.typeName)
    }

    @Test
    fun incomeTotalFollowsIncomeEntries() = runTest(main.dispatcher) {
        vm = viewModel()
        backgroundScope.launch { vm.incomeTypes.collect {} }
        vm.open(dailyExpense("2026-10-07", id = 7))

        vm.addIncome(4, money(15_000), weight = 30.0, pricePerKilo = money(500))
        vm.addIncome(4, money(2_000))
        assertMoney(17_000, current.totalIncome)

        vm.removeIncome(1)
        assertMoney(15_000, current.totalIncome)
    }

    @Test
    fun advanceAmountIsTheSumOfAdvanceEntries() = runTest(main.dispatcher) {
        vm = viewModel()
        vm.open(dailyExpense("2026-10-07", id = 7))

        vm.addAdvanceEntry(money(3_350), "", "Kaa onakku")
        vm.addAdvanceEntry(money(650), "petrol", "Kalanashini")
        assertMoney(4_000, current.advanceAmount)

        vm.updateAdvanceEntry(1, money(1_000), "petrol", "Kalanashini")
        assertMoney(4_350, current.advanceAmount)

        vm.removeAdvanceEntry(0)
        assertMoney(1_000, current.advanceAmount)

        // Recalculating for any other change must not lose the advances.
        vm.updateExtraOvertimeAmount(money(0))
        assertMoney(1_000, current.advanceAmount)
    }

    @Test
    fun remainingBalanceExcludesVendorBillsAndIncludesEarlierWeeks() = runTest(main.dispatcher) {
        vm = viewModel()
        // Earlier weeks leave +255 brought forward.
        coEvery { repository.getDailyExpensesBeforeDate("2026-10-05") } returns listOf(
            dailyExpense("2026-10-01", groups = listOf(group("Malayali Male", 1, 1_000)), settlement = 1_255)
        )
        backgroundScope.launch { vm.previousExcessBalance.collect {} }
        vm.open(
            dailyExpense(
                "2026-10-07", id = 7,
                groups = listOf(group("Malayali Male", 8, 575)),                              // 4,600
                others = listOf(other("Cardamom Drying", 3_350), other("Fertilizers", 12_000)),
                advances = listOf(advance(3_350))
            )
        )

        assertMoney(255, vm.previousExcessBalance.value)
        assertMoney(19_950, vm.getTotalActualExpenses())   // real cost still includes the fertilizer bill
        assertMoney(12_000, vm.getVendorPurchases())
        assertMoney(3_350, vm.getTotalPaymentsMade())
        // 3,350 paid + 255 brought forward - (19,950 - 12,000)
        assertMoney(-4_345, vm.getNetAmount())
    }

    @Test
    fun savingANewDayInsertsItAndOpensTheWeekInWeeklyFunds() = runTest(main.dispatcher) {
        vm = viewModel()
        coEvery { repository.getDailyExpensesByDate("2026-10-07") } returns emptyList()
        val saved = slot<DailyExpense>()
        val funds = slot<WeeklyFunds>()
        coEvery { repository.insertDailyExpense(capture(saved)) } returns 1L
        coEvery { repository.insertWeeklyFunds(capture(funds)) } returns 1L

        vm.loadOrCreateExpense(LocalDate.parse("2026-10-07"))
        vm.addAdvanceEntry(money(500), "", "Raja")
        vm.saveExpense()

        assertMoney(500, saved.captured.advanceAmount)
        assertEquals("2026-10-05", funds.captured.weekStartDate)   // Monday of that week
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun savingAnExistingDayUpdatesItAndDoesNotDuplicateWeeklyFunds() = runTest(main.dispatcher) {
        vm = viewModel()
        every { repository.getAllWeeklyFundsFlow() } returns flowOf(
            listOf(WeeklyFunds(weekStartDate = "2026-10-05", amountReceived = money(0)))
        )
        vm.open(dailyExpense("2026-10-07", id = 7))

        vm.saveExpense()

        coVerify(exactly = 1) { repository.updateDailyExpense(match { it.id == 7 }) }
        coVerify(exactly = 0) { repository.insertDailyExpense(any()) }
        coVerify(exactly = 0) { repository.insertWeeklyFunds(any()) }
    }

    @Test
    fun deletingADayAlsoRemovesItsVendorPayments() = runTest(main.dispatcher) {
        vm = viewModel()
        val day = dailyExpense("2026-08-14", id = 9, others = listOf(other("Fertilizers", 4_680, paid = true)))
        val payment = VendorPayment(id = 5, date = "2026-08-24", amount = money(4_680), sourceExpenseDate = "2026-08-14")
        coEvery { repository.getVendorPaymentsBySourceExpenseDate("2026-08-14") } returns listOf(payment)
        vm.open(day)

        vm.deleteExpense()

        coVerifyOrder {
            repository.deleteVendorPayment(payment)
            repository.deleteDailyExpense(day)
        }
        assertNull(vm.currentExpense.value)
    }

    @Test
    fun savingANewDayTwiceUpdatesTheSameRecord() = runTest(main.dispatcher) {
        vm = viewModel()
        coEvery { repository.getDailyExpensesByDate("2026-10-07") } returns emptyList()
        coEvery { repository.insertDailyExpense(any()) } returns 41L

        vm.loadOrCreateExpense(LocalDate.parse("2026-10-07"))
        vm.saveExpense()
        vm.addAdvanceEntry(money(500), "", "Raja")
        vm.saveExpense()

        coVerify(exactly = 1) { repository.insertDailyExpense(any()) }
        coVerify(exactly = 1) { repository.updateDailyExpense(match { it.id == 41 && it.advanceAmount.compareTo(money(500)) == 0 }) }
        assertEquals(41, current.id)
    }

    @Test
    fun aSecondTapWhileSavingIsIgnored() = runTest(main.dispatcher) {
        vm = viewModel()
        coEvery { repository.getDailyExpensesByDate("2026-10-07") } returns emptyList()
        // A slow database write, so the second tap arrives while the first save is still running.
        coEvery { repository.insertDailyExpense(any()) } coAnswers { delay(1_000); 41L }

        vm.loadOrCreateExpense(LocalDate.parse("2026-10-07"))
        vm.saveExpense()
        vm.saveExpense()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.insertDailyExpense(any()) }
        assertFalse(vm.uiState.value.isSaving)
    }
}
