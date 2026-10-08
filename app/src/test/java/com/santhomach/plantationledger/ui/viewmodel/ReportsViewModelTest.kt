package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.advance
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.groupsOf
import com.santhomach.plantationledger.testutil.income
import com.santhomach.plantationledger.testutil.money
import com.santhomach.plantationledger.testutil.other
import com.santhomach.plantationledger.testutil.otherEntriesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Reports, Home and Vendor Ledger logic: vendor bills and payments, cloning a day, the
 * report breakdowns, date ranges and the balance brought forward.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository: ExpenseRepository = mockk(relaxed = true)

    private fun viewModel(
        days: List<DailyExpense> = emptyList(),
        payments: List<VendorPayment> = emptyList()
    ): ReportsViewModel {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(days)
        every { repository.getAllVendorPaymentsFlow() } returns flowOf(payments)
        return ReportsViewModel(repository)
    }

    // ---- Vendor ledger ------------------------------------------------------------------------

    @Test
    fun vendorLedgerListsOnlyPesticideAndFertilizerBills() = runTest(main.dispatcher) {
        val vm = viewModel(
            days = listOf(
                dailyExpense("2026-08-12", others = listOf(other("Pesticides", 23_000, paid = true), other("Fuel", 625))),
                dailyExpense("2026-08-14", others = listOf(other("Fertilizers", 15_300), other("Fertilizers", 5_400, paid = true)))
            )
        )

        val ledger = vm.vendorLedger.value
        assertEquals(3, ledger.items.size)
        assertMoney(43_700, ledger.totalExpenses)
        assertMoney(28_400, ledger.totalPaid)
        assertMoney(15_300, ledger.outstanding)
        assertFalse(ledger.items.any { it.typeName == "Fuel" })
    }

    @Test
    fun markingABillPaidFlagsOnlyThatLineAndRecordsThePayment() = runTest(main.dispatcher) {
        // Two identical unpaid lines on the same day: only one may be marked by one payment.
        val day = dailyExpense("2026-08-14", id = 3, others = listOf(other("Fertilizers", 5_400), other("Fertilizers", 5_400)))
        val vm = viewModel(days = listOf(day))
        coEvery { repository.getDailyExpensesByDate("2026-08-14") } returns listOf(day)
        val updated = slot<DailyExpense>()
        val payment = slot<VendorPayment>()
        coEvery { repository.updateDailyExpense(capture(updated)) } returns Unit
        coEvery { repository.insertVendorPayment(capture(payment)) } returns 1L

        vm.markVendorBillPaid(vm.vendorLedger.value.items.first(), "Cheque 1123", LocalDate.parse("2026-08-24"))

        assertEquals(listOf(true, false), otherEntriesOf(updated.captured).map { it.isPaid })
        assertEquals("2026-08-24", payment.captured.date)
        assertMoney(5_400, payment.captured.amount)
        assertEquals("2026-08-14", payment.captured.sourceExpenseDate)
        assertEquals("Fertilizers", payment.captured.sourceTypeName)
        assertEquals("Cheque 1123", payment.captured.notes)
    }

    @Test
    fun deletingAVendorPaymentMarksTheBillUnpaidAgain() = runTest(main.dispatcher) {
        val day = dailyExpense("2026-08-14", id = 3, others = listOf(other("Fertilizers", 5_400, paid = true)))
        val vm = viewModel(days = listOf(day))
        coEvery { repository.getDailyExpensesByDate("2026-08-14") } returns listOf(day)
        val updated = slot<DailyExpense>()
        coEvery { repository.updateDailyExpense(capture(updated)) } returns Unit
        val payment = VendorPayment(id = 8, date = "2026-08-24", amount = money(5_400),
            sourceExpenseDate = "2026-08-14", sourceTypeName = "Fertilizers")

        vm.deleteVendorPayment(payment)

        assertFalse(otherEntriesOf(updated.captured).single().isPaid)
        coVerify { repository.deleteVendorPayment(payment) }
    }

    // ---- Clone a day --------------------------------------------------------------------------

    @Test
    fun cloningCopiesLabourOnlyWithoutOvertimeExpensesIncomeOrPayments() = runTest(main.dispatcher) {
        val vm = viewModel()
        val source = dailyExpense(
            "2026-10-07", id = 7,
            groups = listOf(group("Malayali Male", 8, 575, task = "Harvesting", otHours = 1, otRate = 70)),
            others = listOf(other("Cardamom Drying", 3_350)),
            incomes = listOf(income("Cardamom Sales", 10_000)),
            advances = listOf(advance(3_350)),
            settlement = 37_310,
            comments = "kept"
        )
        val cloned = slot<DailyExpense>()
        coEvery { repository.insertDailyExpense(capture(cloned)) } returns 99L

        vm.cloneExpense(source, LocalDate.parse("2026-10-08"))

        with(cloned.captured) {
            assertEquals(0, id)
            assertEquals("2026-10-08", date)
            assertEquals("clone", expenseAdditionType)
            assertMoney(4_600, totalLaborCost)
            assertMoney(0, totalOvertimeCost)
            assertEquals(0, groupsOf(this).single().overtimeHours)
            assertEquals("Harvesting", groupsOf(this).single().taskPerformed)
            assertEquals("[]", otherExpenses); assertMoney(0, totalOtherExpensesCost)
            assertEquals("[]", incomeEntries); assertMoney(0, totalIncome)
            assertEquals("[]", advanceEntries); assertMoney(0, advanceAmount)
            assertMoney(0, weeklyPaymentDone)
            assertEquals("kept", comments)
        }
        assertEquals(LocalDate.parse("2026-10-08"), vm.uiState.value.cloneSuccessDate)
    }

    @Test
    fun nextWorkdaySkipsSunday() {
        val vm = viewModel()
        assertEquals(LocalDate.parse("2026-10-08"), vm.nextWorkday(LocalDate.parse("2026-10-07"))) // Wed -> Thu
        assertEquals(LocalDate.parse("2026-10-12"), vm.nextWorkday(LocalDate.parse("2026-10-10"))) // Sat -> Mon
    }

    // ---- Date ranges --------------------------------------------------------------------------

    @Test
    fun dateRangesResolveToTheRightDays() {
        val vm = viewModel()
        val custom = DateRange.Custom(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04"))
        assertEquals(LocalDate.parse("2026-09-28"), vm.getStartDate(custom))
        assertEquals(LocalDate.parse("2026-10-04"), vm.getEndDate(custom))

        val today = LocalDate.now()
        assertEquals(today.with(DayOfWeek.MONDAY), vm.getStartDate(DateRange.CurrentWeek))
        val lastMonth = today.minusMonths(1)
        assertEquals(lastMonth.withDayOfMonth(1), vm.getStartDate(DateRange.LastMonth))
        assertEquals(lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()), vm.getEndDate(DateRange.LastMonth))
    }

    // ---- Report breakdowns --------------------------------------------------------------------

    private val week = listOf(
        dailyExpense(
            "2026-09-28",
            groups = listOf(group("Malayali Male", 8, 575, otHours = 1, otRate = 70), group("Bengali Male", 2, 500)),
            others = listOf(other("Transportation", 200), other("Pesticides", 7_660)),
            incomes = listOf(income("Cardamom Sales", 15_000, weight = 30.0)),
            advances = listOf(advance(650, recipient = ""))
        ),
        dailyExpense(
            "2026-10-01",
            groups = listOf(group("Malayali Male", 8, 575)),
            others = listOf(other("Transportation", 200)),
            incomes = listOf(income("Cardamom Sales", 5_000, weight = 10.0), income("Pepper Sales", 9_000)),
            advances = listOf(advance(3_350, recipient = "Kaa onakku")),
            settlement = 37_310
        )
    )

    @Test
    fun topExpenseCategoriesAreTotalledAndSortedLargestFirst() {
        val top = viewModel().getTopExpenseCategories(week)
        assertEquals(listOf("Pesticides", "Transportation"), top.map { it.categoryName })
        assertMoney(400, top[1].totalAmount)
    }

    @Test
    fun incomeBreakdownAddsAmountsAndWeightPerCommodity() {
        val breakdown = viewModel().getIncomeBreakdown(week)
        assertEquals("Cardamom Sales", breakdown.first().commodityName)
        assertMoney(20_000, breakdown.first().totalAmount)
        assertEquals(40.0, breakdown.first().totalWeight, 0.001)
        assertMoney(9_000, breakdown.last().totalAmount)
    }

    @Test
    fun weeklyWorkerSpecificsSeparateBaseWagesFromOvertime() {
        val workers = viewModel().getWeeklySpecificWorkers(week)
        val malayali = workers.first { it.workerTypeName == "Malayali Male" }
        assertEquals(16, malayali.totalCount)
        assertMoney(9_200, malayali.totalBaseCost)
        assertMoney(560, malayali.totalOvertimeCost)
        assertMoney(37_310, viewModel().getWeeklySpecificSettlement(week))
    }

    @Test
    fun advancesWithoutARecipientAreGroupedAsGeneral() {
        val advances = viewModel().getWeeklySpecificAdvances(week)
        assertEquals(listOf("General", "Kaa onakku"), advances.map { it.recipientName })
        assertMoney(3_350, advances.last().totalAmount)
    }

    // ---- Balance brought forward --------------------------------------------------------------

    @Test
    fun homeBringsForwardEveryEarlierWeekButNotTheCurrentOne() = runTest(main.dispatcher) {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val vm = viewModel(
            days = listOf(
                // Two weeks ago: overpaid by 1,000
                dailyExpense(monday.minusWeeks(2).toString(), groups = listOf(group("Malayali Male", 2, 1_000)), settlement = 3_000),
                // Last week: short by 605, plus a fertilizer bill that must not count
                dailyExpense(
                    monday.minusWeeks(1).toString(),
                    groups = listOf(group("Malayali Male", 1, 1_605)),
                    others = listOf(other("Fertilizers", 12_000)),
                    settlement = 1_000
                ),
                // This week: not part of the amount brought forward
                dailyExpense(monday.toString(), groups = listOf(group("Malayali Male", 1, 4_000)))
            )
        )
        backgroundScope.launch { vm.broughtForward.collect {} }

        assertMoney(395, vm.broughtForward.value)   // +1,000 - 605
    }

    @Test
    fun reportsBroughtForwardFollowsTheSelectedRange() = runTest(main.dispatcher) {
        val vm = viewModel(
            days = listOf(
                dailyExpense("2026-07-06", groups = listOf(group("Malayali Male", 1, 4_600))),               // short 4,600
                dailyExpense("2026-07-09", settlement = 4_600),                                             // settled
                dailyExpense("2026-09-21", groups = listOf(group("Malayali Male", 1, 1_000)), settlement = 2_010) // +1,010
            )
        )
        backgroundScope.launch { vm.periodBroughtForward.collect {} }

        vm.setDateRange(DateRange.Custom(LocalDate.parse("2026-07-08"), LocalDate.parse("2026-07-12")))
        assertMoney(-4_600, vm.periodBroughtForward.value)

        vm.setDateRange(DateRange.Custom(LocalDate.parse("2026-09-28"), LocalDate.parse("2026-10-04")))
        assertMoney(1_010, vm.periodBroughtForward.value)
    }
}
