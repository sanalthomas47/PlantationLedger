package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.schedule.CombinationCard
import com.santhomach.plantationledger.data.schedule.CombinationCategory
import com.santhomach.plantationledger.data.schedule.DefaultSchedule
import com.santhomach.plantationledger.data.schedule.ScheduleRepository
import com.santhomach.plantationledger.data.schedule.ScheduleTask
import com.santhomach.plantationledger.testutil.FakeScheduleDao
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val thisYear = LocalDate.now().year
    private val dao = FakeScheduleDao(
        tasks = listOf(
            ScheduleTask(id = 1, month = 5, title = "1st fertilizer round", sortOrder = 0),
            ScheduleTask(id = 2, month = 5, title = "Weeding", sortOrder = 1),
            ScheduleTask(id = 3, month = 10, title = "Harvest rounds", sortOrder = 0)
        ),
        cards = listOf(
            CombinationCard(id = 1, category = "FERTILIZER", name = "Pre-monsoon mix", sortOrder = 0),
            CombinationCard(id = 2, category = "PESTICIDE", name = "Thrips", sortOrder = 0)
        )
    )

    private fun TestScope.viewModel() = ScheduleViewModel(ScheduleRepository(dao)).also { vm ->
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun groupsTasksIntoTwelveMonthsAndCardsByCategory() = runTest(main.dispatcher) {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals((1..12).toList(), state.months.map { it.month })
        assertEquals(listOf("1st fertilizer round", "Weeding"), state.months[4].tasks.map { it.title })
        assertTrue(state.months[0].tasks.isEmpty())
        assertEquals(listOf("Pre-monsoon mix"), state.cards.getValue(CombinationCategory.FERTILIZER).map { it.name })
        assertTrue(state.cards.getValue(CombinationCategory.FOLIAR).isEmpty())
        assertEquals(thisYear, state.year)
    }

    @Test
    fun ticksAreKeptPerYear() = runTest(main.dispatcher) {
        val vm = viewModel()
        val weeding = dao.tasks.value.single { it.id == 2 }

        vm.setDone(weeding, true)
        assertEquals(1, vm.uiState.value.months[4].doneCount)

        vm.changeYear(1)                                                   // next year starts unticked
        assertEquals(thisYear + 1, vm.uiState.value.year)
        assertEquals(0, vm.uiState.value.months[4].doneCount)

        vm.changeYear(-1)                                                  // and this year still has it
        assertEquals(setOf(2), vm.uiState.value.months[4].done)

        vm.setDone(weeding, false)
        assertEquals(0, vm.uiState.value.months[4].doneCount)
    }

    @Test
    fun tasksCanBeAddedRenamedAndDeleted() = runTest(main.dispatcher) {
        val vm = viewModel()

        vm.addTask(5, "  Drenching  ")
        vm.addTask(5, "   ")                                               // blank is ignored
        assertEquals(listOf("1st fertilizer round", "Weeding", "Drenching"), vm.uiState.value.months[4].tasks.map { it.title })

        val weeding = dao.tasks.value.single { it.id == 2 }
        vm.setDone(weeding, true)
        vm.renameTask(weeding, "Weeding (round 2)")
        assertEquals("Weeding (round 2)", vm.uiState.value.months[4].tasks[1].title)
        assertEquals(setOf(2), vm.uiState.value.months[4].done)           // renaming keeps the tick

        vm.deleteTask(dao.tasks.value.single { it.id == 2 })
        assertEquals(listOf("1st fertilizer round", "Drenching"), vm.uiState.value.months[4].tasks.map { it.title })
        assertTrue(dao.completions.value.isEmpty())                         // its ticks go with it
    }

    @Test
    fun aNewCardIsCreatedAndItsIdHandedBackToOpenIt() = runTest(main.dispatcher) {
        val vm = viewModel()
        var opened: Int? = null

        vm.addCard(CombinationCategory.FOLIAR, " Micronutrient spray ") { opened = it }
        vm.addCard(CombinationCategory.FOLIAR, "  ") { opened = -1 }      // blank is ignored

        val foliar = vm.uiState.value.cards.getValue(CombinationCategory.FOLIAR)
        assertEquals(listOf("Micronutrient spray"), foliar.map { it.name })
        assertEquals(foliar.single().id, opened)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun theStartingCalendarHasWorkForEveryMonth() {
        assertEquals((1..12).toSet(), DefaultSchedule.tasksByMonth.keys)
        assertTrue(DefaultSchedule.tasksByMonth.values.all { it.isNotEmpty() })
        val tasks = DefaultSchedule.tasks()
        assertEquals(DefaultSchedule.tasksByMonth.values.sumOf { it.size }, tasks.size)
        assertEquals(listOf(0, 1, 2, 3), tasks.filter { it.month == 1 }.map { it.sortOrder })
    }
}
