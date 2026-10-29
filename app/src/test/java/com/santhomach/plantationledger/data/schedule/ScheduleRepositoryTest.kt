package com.santhomach.plantationledger.data.schedule

import com.santhomach.plantationledger.testutil.FakeScheduleDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleRepositoryTest {

    private val dao = FakeScheduleDao()
    private val repository = ScheduleRepository(dao)

    @Test
    fun newTasksAreTrimmedAndAddedAtTheEndOfTheirMonth() = runTest {
        repository.addTask(5, "  Weeding ")
        repository.addTask(5, "Drench")
        repository.addTask(6, "Trashing")

        val tasks = dao.allTasks()
        assertEquals(listOf("Weeding", "Drench", "Trashing"), tasks.map { it.title })
        assertEquals(listOf(0, 1, 0), tasks.map { it.sortOrder })
    }

    @Test
    fun aTaskNeedsARealMonth() = runTest {
        assertTrue(runCatching { repository.addTask(0, "x") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.addTask(13, "x") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(dao.allTasks().isEmpty())
    }

    @Test
    fun tickingTwiceKeepsOneTickAndUntickingRemovesIt() = runTest {
        val id = repository.addTask(1, "Mulching").toInt()

        repository.setDone(id, 2026, true)
        repository.setDone(id, 2026, true)
        repository.setDone(id, 2025, true)
        assertEquals(2, dao.allCompletions().size)

        repository.setDone(id, 2026, false)
        assertEquals(listOf(2025), dao.allCompletions().map { it.year })

        repository.deleteTask(dao.allTasks().single())
        assertTrue("ticks go with their task", dao.allCompletions().isEmpty())
    }

    @Test
    fun cardsAreOrderedWithinTheirCategoryAndSavedTrimmed() = runTest {
        repository.addCard(CombinationCategory.FOLIAR, "Micronutrient mix")
        val id = repository.addCard(CombinationCategory.PESTICIDE, " Thrips spray ").toInt()
        repository.addCard(CombinationCategory.FOLIAR, "Boron")

        assertEquals(listOf(0, 1), dao.allCards().filter { it.category == "FOLIAR" }.map { it.sortOrder })
        val card = repository.card(id)!!
        assertEquals("Thrips spray", card.name)
        assertEquals(0, card.sortOrder)

        repository.saveCard(card.copy(name = " Thrips spray (May) ", purpose = "Thrips", updatedAt = "2000-01-01T00:00"))
        val saved = repository.card(id)!!
        assertEquals("Thrips spray (May)", saved.name)
        assertEquals("Thrips", saved.purpose)
        assertNotEquals("2000-01-01T00:00", saved.updatedAt)
    }

    @Test
    fun aSnapshotRestoresIntoAnotherScheduleExactly() = runTest {
        val id = repository.addTask(8, "Harvest round 1").toInt()
        repository.setDone(id, 2026, true)
        repository.addCard(CombinationCategory.FERTILIZER, "NPK 19:19:19")

        val other = FakeScheduleDao(tasks = listOf(ScheduleTask(id = 7, month = 2, title = "Old task")))
        ScheduleRepository(other).replaceAll(repository.snapshot())

        assertEquals(dao.allTasks(), other.allTasks())
        assertEquals(dao.allCompletions(), other.allCompletions())
        assertEquals(dao.allCards(), other.allCards())
    }
}
