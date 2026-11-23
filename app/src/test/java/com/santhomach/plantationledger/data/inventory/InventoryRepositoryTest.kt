package com.santhomach.plantationledger.data.inventory

import com.santhomach.plantationledger.testutil.FakeInventoryDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InventoryRepositoryTest {

    private val dao = FakeInventoryDao()
    private val repository = InventoryRepository(dao)

    @Test
    fun newItemsAreTidiedBeforeSaving() = runTest {
        val id = repository.addItem(
            InventoryItem(id = 55, name = "  Power sprayer ", category = "  ", count = 2,
                purchasePrice = " ", notes = " Honda engine ")
        ).toInt()

        val item = repository.item(id)!!
        assertNotEquals("a new item always gets a new id", 55, id)
        assertEquals("Power sprayer", item.name)
        assertEquals(DEFAULT_CATEGORY, item.category)
        assertNull(item.purchasePrice)
        assertEquals("Honda engine", item.notes)
    }

    @Test
    fun anItemNeedsANameAndANonNegativeCount() = runTest {
        assertTrue(runCatching { repository.addItem(InventoryItem(name = " ")) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.addItem(InventoryItem(name = "Knife", count = -1)) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(dao.allItems().isEmpty())
    }

    @Test
    fun theCountChangesByStepsButNeverBelowZero() = runTest {
        val id = repository.addItem(InventoryItem(name = "Sickle", count = 1)).toInt()

        repository.changeCount(id, +1)
        repository.changeCount(id, +1)
        assertEquals(3, repository.item(id)!!.count)
        repeat(5) { repository.changeCount(id, -1) }
        assertEquals(0, repository.item(id)!!.count)
    }

    @Test
    fun editsAreSavedAndDeletesRemoveTheItem() = runTest {
        val id = repository.addItem(InventoryItem(name = "Ladder", category = "Tools", count = 1)).toInt()
        val item = repository.item(id)!!.copy(updatedAt = "2000-01-01T00:00")

        repository.saveItem(item.copy(name = "Aluminium ladder ", count = 2, inUseSince = "2023-06-01", purchasePrice = "4500"))
        val saved = repository.item(id)!!
        assertEquals("Aluminium ladder", saved.name)
        assertEquals(2, saved.count)
        assertEquals("2023-06-01", saved.inUseSince)
        assertEquals("4500", saved.purchasePrice)
        assertNotEquals("2000-01-01T00:00", saved.updatedAt)

        repository.deleteItem(saved)
        assertNull(repository.item(id))
    }

    @Test
    fun aSnapshotRestoresIntoAnotherInventoryExactly() = runTest {
        repository.addItem(InventoryItem(name = "Weed cutter", category = "Equipment", count = 1, inUseSince = "2024-02-10"))
        repository.addItem(InventoryItem(name = "Gloves", category = "Safety", count = 12, notes = "Rubber"))

        val other = FakeInventoryDao(listOf(InventoryItem(id = 9, name = "Old item")))
        InventoryRepository(other).replaceAll(repository.snapshot())

        assertEquals(dao.allItems(), other.allItems())
    }

    @Test
    fun usageIsShownInYearsMonthsOrDays() {
        val today = LocalDate.of(2026, 11, 23)
        assertEquals("2 yrs 3 mo", inUseFor(LocalDate.of(2024, 8, 20), today))
        assertEquals("1 yr", inUseFor(LocalDate.of(2025, 11, 23), today))
        assertEquals("5 mo", inUseFor(LocalDate.of(2026, 6, 1), today))
        assertEquals("12 days", inUseFor(LocalDate.of(2026, 11, 11), today))
        assertEquals("1 day", inUseFor(LocalDate.of(2026, 11, 22), today))
        assertEquals("Since today", inUseFor(today, today))
        assertEquals("Not yet in use", inUseFor(LocalDate.of(2026, 12, 1), today))
    }
}
