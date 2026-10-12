package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.data.repository.ExpenseSummary
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyFundsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val monday = LocalDate.of(2024, 3, 4)
    private val repository: ExpenseRepository = mockk(relaxed = true)

    private val summary = ExpenseSummary(
        totalLaborCost = money(10_000),
        totalOvertimeCost = money(500),
        totalOtherExpenses = money(4_000),
        totalVendorPurchases = money(3_000),
        totalIncome = money(25_000),
        totalAdvanceAmount = money(2_000),
        totalWeeklyPayment = money(8_000)
    )

    @Test
    fun weekComparisonLeavesOutVendorBills() = runTest(main.dispatcher) {
        every { repository.getDailyExpenseSummaryFlow("2024-03-04", "2024-03-09") } returns flowOf(summary)

        val comparison = WeeklyFundsViewModel(repository).getComparisonFlow(monday).first()

        assertMoney(11_500, comparison.totalExpenses)                   // 10,000 + 500 + 4,000 - 3,000
        assertMoney(25_000, comparison.totalIncome)
        assertMoney(10_000, comparison.totalPayments)
    }

    @Test
    fun excessIsThePaymentMadeLessTheWeeksExpenses() = runTest(main.dispatcher) {
        every { repository.getDailyExpenseSummaryFlow(any(), any()) } returns flowOf(summary)
        every { repository.getAllWeeklyFundsFlow() } returns flowOf(listOf(
            WeeklyFunds(id = 1, weekStartDate = "2024-03-04", amountReceived = money(15_000), paymentMade = money(12_000)),
            WeeklyFunds(id = 2, weekStartDate = "2024-03-11", amountReceived = money(1), paymentMade = money(99_999))
        ))
        val vm = WeeklyFundsViewModel(repository)
        backgroundScope.launch { vm.weeklyFunds.collect {} }

        assertMoney(500, vm.getExcessBalanceFlow(monday).first())                     // 12,000 - 11,500
        // A week with no funds record counts nothing as paid.
        assertMoney(-11_500, vm.getExcessBalanceFlow(LocalDate.of(2024, 2, 26)).first())
        coVerify { repository.getDailyExpenseSummaryFlow("2024-03-04", "2024-03-10") }   // Monday to Sunday
    }

    @Test
    fun fundsAreFiledUnderTheMondayOfTheWeek() = runTest(main.dispatcher) {
        val saved = slot<WeeklyFunds>()
        coEvery { repository.insertWeeklyFunds(capture(saved)) } returns 1L
        val vm = WeeklyFundsViewModel(repository)

        vm.addFunds(money(20_000), money(18_000), LocalDate.of(2024, 3, 7), "Thursday cash")   // a Thursday

        assertEquals("2024-03-04", saved.captured.weekStartDate)
        assertMoney(20_000, saved.captured.amountReceived)
        assertMoney(18_000, saved.captured.paymentMade)
        assertEquals("Thursday cash", saved.captured.notes)

        vm.addFunds(money(5_000), LocalDate.of(2024, 3, 10), "")                              // a Sunday
        assertEquals("2024-03-04", saved.captured.weekStartDate)
        assertMoney(0, saved.captured.paymentMade)
    }

    @Test
    fun editingKeepsTheWeekAndIgnoresUnknownRecords() = runTest(main.dispatcher) {
        val existing = WeeklyFunds(id = 7, weekStartDate = "2024-03-04", amountReceived = money(1_000),
            notes = "old", createdAt = "2024-03-04T09:00")
        every { repository.getAllWeeklyFundsFlow() } returns flowOf(listOf(existing))
        val vm = WeeklyFundsViewModel(repository)
        backgroundScope.launch { vm.weeklyFunds.collect {} }

        vm.updateFunds(7, money(2_000), money(1_500), "new")
        coVerify { repository.updateWeeklyFunds(existing.copy(amountReceived = money(2_000), paymentMade = money(1_500), notes = "new")) }

        vm.updatePaymentMade(7, money(900))
        coVerify { repository.updateWeeklyFunds(existing.copy(paymentMade = money(900))) }

        vm.updateFunds(99, money(1), money(1), "")
        vm.updatePaymentMade(99, money(1))
        coVerify(exactly = 2) { repository.updateWeeklyFunds(any()) }
    }

    @Test
    fun aFailedSaveIsShownAsAnError() = runTest(main.dispatcher) {
        coEvery { repository.insertWeeklyFunds(any()) } throws IllegalStateException("database is locked")
        val vm = WeeklyFundsViewModel(repository)

        vm.addFunds(money(1), monday, "")

        assertEquals("database is locked", vm.uiState.value.error)
    }
}
