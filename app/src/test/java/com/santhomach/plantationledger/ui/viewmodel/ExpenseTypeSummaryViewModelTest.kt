package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.other
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseTypeSummaryViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository: ExpenseRepository = mockk()
    private val monday = LocalDate.now().with(DayOfWeek.MONDAY)
    private val lastMonday = monday.minusWeeks(1)
    private val longAgo = LocalDate.of(2001, 6, 1)

    @Test
    fun breaksOtherExpensesDownByTypeAndSubtype() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(listOf(
            dailyExpense(monday.toString(), others = listOf(
                other("Fuel", 600, subtype = "Diesel"), other("Fuel", 400, subtype = "Petrol"),
                other("Pesticides", 1_000))),
            dailyExpense(lastMonday.toString(), others = listOf(other("Fuel", 200, subtype = "Diesel"),
                other("Pesticides", 3_000))),
            dailyExpense(longAgo.toString(), others = listOf(other("Fuel", 800, subtype = ""))),
        ))
        val vm = ExpenseTypeSummaryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Fuel", "Pesticides"), state.rows.map { it.typeName })   // sorted by name
        assertMoney(2_000, state.thisWeekTotal)
        assertMoney(6_000, state.allTimeTotal)

        val fuel = state.rows.first()
        assertMoney(1_000, fuel.thisWeekAmount)
        assertMoney(2_000, fuel.allTimeAmount)
        assertEquals(50f, fuel.thisWeekPercent)
        assertEquals(TrendDirection.UP, fuel.weekTrend)                    // 1,000 vs 200 last week
        assertEquals(listOf("", "Diesel", "Petrol"), fuel.subtypes.map { it.subtypeName })
        assertMoney(800, fuel.subtypes.first { it.subtypeName == "Diesel" }.allTimeAmount)
        assertMoney(800, fuel.subtypes.first { it.subtypeName == "" }.allTimeAmount)  // no subtype

        val pesticides = state.rows.last()
        assertEquals(TrendDirection.DOWN, pesticides.weekTrend)            // 1,000 vs 3,000
        assertEquals(66.67f, pesticides.allTimePercent)

        if (lastMonday.year == monday.year && monday.year == LocalDate.now().year) {
            assertMoney(5_200, state.thisYearTotal)
        }
    }

    @Test
    fun rowsCanBeExpandedAndCollapsed() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns
            flowOf(listOf(dailyExpense(monday.toString(), others = listOf(other("Fuel", 600)))))
        val vm = ExpenseTypeSummaryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.toggleExpanded("Fuel")
        assertTrue(vm.uiState.value.rows.single().isExpanded)
        vm.toggleExpanded("Fuel")
        assertFalse(vm.uiState.value.rows.single().isExpanded)
    }

    @Test
    fun aLoadingErrorIsShownInsteadOfAnEndlessSpinner() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns
            flow { throw IllegalStateException("db closed") }
        val vm = ExpenseTypeSummaryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        assertEquals("db closed", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun noOtherExpensesMeansNoRowsAndZeroPercentages() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns
            flowOf(listOf(dailyExpense(monday.toString()).copy(otherExpenses = "not json")))
        val vm = ExpenseTypeSummaryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertMoney(0, vm.uiState.value.allTimeTotal)
    }
}
