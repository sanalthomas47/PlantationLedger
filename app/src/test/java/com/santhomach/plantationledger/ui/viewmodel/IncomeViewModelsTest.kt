package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.income
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
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository: ExpenseRepository = mockk()
    private val today = LocalDate.now().toString()
    private val longAgo = "2001-06-01"

    private val days = listOf(
        dailyExpense(today,
            groups = listOf(group("Bengali Male", 1, 500, task = "Weeding")),
            incomes = listOf(income("Cardamom Sales", 20_000, weight = 10.0))),
        dailyExpense(longAgo,
            groups = listOf(group("Bengali Male", 2, 400, task = "Pepper Drying")),
            incomes = listOf(income("Cardamom Sales", 50_000), income("Pepper Sales", 9_000)))
    )

    private fun incomeTypes(vararg names: String) =
        flowOf(names.mapIndexed { i, name -> IncomeType(id = i + 1, typeName = name) })

    // ---- Income Breakdown ----

    @Test
    fun breakdownTotalsEachPeriodAcrossAllTypes() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(days)
        val vm = IncomeBreakdownViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Cardamom Sales", "Pepper Sales"), state.types.map { it.typeName })
        assertMoney(20_000, state.thisWeekTotal)
        assertMoney(20_000, state.thisYearTotal)
        assertMoney(79_000, state.allTimeTotal)
        assertMoney(2_000, state.types.first().thisYear.pricePerKg)
    }

    @Test
    fun breakdownRowsOpenAndClose() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(days)
        val vm = IncomeBreakdownViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.toggle("Pepper Sales")
        assertEquals(setOf("Pepper Sales"), vm.uiState.value.expanded)
        vm.toggle("Cardamom Sales")
        vm.toggle("Pepper Sales")
        assertEquals(setOf("Cardamom Sales"), vm.uiState.value.expanded)
    }

    @Test
    fun breakdownReportsALoadFailure() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flow { throw IllegalStateException("disk error") }
        val vm = IncomeBreakdownViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("disk error", vm.uiState.value.error)
        assertTrue(vm.uiState.value.types.isEmpty())
    }

    // ---- Income vs Expense ----

    @Test
    fun efficiencyStartsOnThisYearAndSwitchesToAllTime() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(days)
        every { repository.getAllActiveIncomeTypesFlow() } returns incomeTypes("Cardamom Sales", "Pepper Sales")
        val vm = EfficiencyViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        var crops = vm.uiState.value.report.crops.associateBy { it.crop }
        assertFalse(vm.uiState.value.allTime)
        assertEquals(setOf("Cardamom"), crops.keys)                      // Pepper had nothing this year
        assertMoney(20_000, crops.getValue("Cardamom").income)
        assertMoney(500, crops.getValue("Cardamom").expense)

        vm.setAllTime(true)
        crops = vm.uiState.value.report.crops.associateBy { it.crop }
        assertTrue(vm.uiState.value.allTime)
        assertMoney(70_000, crops.getValue("Cardamom").income)
        assertMoney(9_000, crops.getValue("Pepper").income)
        assertMoney(800, crops.getValue("Pepper").expense)               // the drying labour

        vm.setAllTime(false)
        assertMoney(20_000, vm.uiState.value.report.crops.single().income)
    }

    @Test
    fun efficiencyUsesTheIncomeTypeListForCropsWithNoSales() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(listOf(
            dailyExpense(today, groups = listOf(group("Bengali Male", 1, 600, task = "Coffee picking")))))
        every { repository.getAllActiveIncomeTypesFlow() } returns incomeTypes("Coffee Sales")
        val vm = EfficiencyViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val coffee = vm.uiState.value.report.crops.single()
        assertEquals("Coffee", coffee.crop)
        assertMoney(600, coffee.expense)
        assertMoney(0, coffee.income)
    }

    @Test
    fun efficiencyRowsOpenAndClose() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(days)
        every { repository.getAllActiveIncomeTypesFlow() } returns incomeTypes()
        val vm = EfficiencyViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.toggle("Pepper")
        vm.toggle("Cardamom")
        vm.toggle("Pepper")
        assertEquals(setOf("Cardamom"), vm.uiState.value.expanded)
    }

    @Test
    fun efficiencyReportsALoadFailure() = runTest(main.dispatcher) {
        every { repository.getDailyExpensesByDateRangeFlow(any(), any()) } returns flowOf(days)
        every { repository.getAllActiveIncomeTypesFlow() } returns flow { throw IllegalStateException("no types") }
        val vm = EfficiencyViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("no types", vm.uiState.value.error)
        assertTrue(vm.uiState.value.report.crops.isEmpty())
    }
}
