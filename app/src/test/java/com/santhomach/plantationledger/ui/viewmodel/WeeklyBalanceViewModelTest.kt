package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The Weekly Balance screen: headline balances, newest-first order and the "don't balance" filter. */
@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyBalanceViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun showsBalancesNewestFirstAndFiltersToWeeksThatDontBalance() = runTest(main.dispatcher) {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        val repository: ExpenseRepository = mockk(relaxed = true)
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(
            listOf(
                dailyExpense(monday.minusWeeks(3).toString(), groups = listOf(group("M", 1, 1_000)), settlement = 1_000), // balanced
                dailyExpense(monday.minusWeeks(2).toString(), groups = listOf(group("M", 1, 1_000)), settlement = 1_800), // +800
                dailyExpense(monday.minusWeeks(1).toString(), groups = listOf(group("M", 1, 1_605)), settlement = 1_000), // -605
                dailyExpense(monday.toString(), groups = listOf(group("M", 1, 4_000)))                                     // this week
            )
        )
        val vm = WeeklyBalanceViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        with(vm.uiState.value) {
            assertEquals(4, weeks.size)
            assertEquals(monday, weeks.first().weekStart)      // newest first
            assertMoney(195, broughtForward)                    // +800 - 605
            assertMoney(-3_805, currentBalance)                 // 195 - 4,000
            assertEquals(2, unbalancedCount)                    // the current week is not counted
        }

        vm.setOnlyUnbalanced(true)
        assertEquals(
            listOf(monday.minusWeeks(1), monday.minusWeeks(2)),
            vm.uiState.value.weeks.map { it.weekStart }
        )
    }
}
