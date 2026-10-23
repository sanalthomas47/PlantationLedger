package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.money
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
    fun labourIsListedFirstByLabourTypeWithOvertime() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(listOf(
            dailyExpense(monday.toString(),
                groups = listOf(group("Bengali Male", 4, 500), group("Malayali Female", 2, 550)),
                others = listOf(other("Fuel", 600))
            ).copy(totalOvertimeCost = money(350)),                         // day-level extra overtime
            dailyExpense(lastMonday.toString(), groups = listOf(group("Bengali Male", 2, 500))),
            // An older record with labour but no worker groups.
            dailyExpense(longAgo.toString()).copy(totalLaborCost = money(1_000))
        ))
        val vm = ExpenseTypeSummaryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(listOf("Labour", "Fuel"), state.rows.map { it.typeName })
        val labour = state.rows.first()
        assertEquals(listOf("Bengali Male", "Malayali Female", "Other labour", "Overtime"),
            labour.subtypes.map { it.subtypeName })
        assertMoney(3_450, labour.thisWeekAmount)                          // 2,000 + 1,100 + 350 overtime
        assertMoney(5_450, labour.allTimeAmount)                           // + 1,000 last week + 1,000 older
        assertMoney(2_000, labour.subtypes.first { it.subtypeName == "Bengali Male" }.thisWeekAmount)
        assertMoney(3_000, labour.subtypes.first { it.subtypeName == "Bengali Male" }.allTimeAmount)
        assertMoney(350, labour.subtypes.first { it.subtypeName == "Overtime" }.thisWeekAmount)
        assertEquals(TrendDirection.UP, labour.weekTrend)                  // 3,450 vs 1,000
        assertMoney(4_050, state.thisWeekTotal)                            // labour + fuel
        assertMoney(6_050, state.allTimeTotal)
    }

    @Test
    fun labourByTaskIncludesOvertimeAndAddsUpToTheLabourTotal() = runTest(main.dispatcher) {
        val today = LocalDate.now()
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(listOf(
            dailyExpense(today.toString(), groups = listOf(
                group("Bengali Male", 4, 500, task = "Weeding", otHours = 2, otRate = 50),   // 2,000 + 400 OT
                group("Malayali Female", 2, 550, task = "Weeding"),                          // 1,100
                group("Bengali Male", 2, 500, task = "Spraying")                             // 1,000
            )).let { it.copy(totalOvertimeCost = it.totalOvertimeCost.add(money(150))) },    // + 150 day-level OT
            // Older day: 1,500 of weeding plus 700 of labour saved without worker groups.
            dailyExpense(longAgo.toString(), groups = listOf(group("Bengali Male", 3, 500, task = "Weeding")))
                .copy(totalLaborCost = money(2_200))
        ))
        val vm = ExpenseTypeSummaryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertEquals(listOf("Weeding", "Spraying", "No task recorded", "Overtime (no task)"),
            state.taskRows.map { it.taskName })                            // largest first, no-task rows last
        val weeding = state.taskRows.first()
        assertMoney(3_500, weeding.thisYearAmount)
        assertMoney(5_000, weeding.allTimeAmount)
        assertEquals(listOf("Bengali Male", "Malayali Female"), weeding.labourTypes.map { it.labourType })
        assertMoney(2_400, weeding.labourTypes[0].thisYearAmount)          // wages + overtime
        assertMoney(3_900, weeding.labourTypes[0].allTimeAmount)
        assertMoney(150, state.taskRows.last().allTimeAmount)
        assertTrue(state.taskRows.last().labourTypes.isEmpty())

        assertMoney(4_650, state.taskThisYearTotal)
        assertMoney(6_850, state.taskAllTimeTotal)
        // Same as the Labour row of the first table.
        assertMoney(state.rows.single { it.typeName == "Labour" }.allTimeAmount, state.taskAllTimeTotal)

        vm.toggleTaskExpanded("Weeding")
        assertTrue(vm.uiState.value.taskRows.first().isExpanded)
        assertFalse(vm.uiState.value.rows.single { it.typeName == "Labour" }.isExpanded)
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
