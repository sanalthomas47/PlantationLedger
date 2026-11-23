package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.santhomach.plantationledger.data.inventory.InventoryItem
import com.santhomach.plantationledger.data.inventory.InventoryRepository
import com.santhomach.plantationledger.testutil.FakeInventoryDao
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelsTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val dao = FakeInventoryDao(listOf(
        InventoryItem(id = 1, name = "Power sprayer", category = "Spraying", count = 2, notes = "Honda"),
        InventoryItem(id = 2, name = "knife", category = "Tools", count = 10),
        InventoryItem(id = 3, name = "Axe", category = "tools", count = 3),
        InventoryItem(id = 4, name = "Drip pipe", category = "Irrigation", count = 0)
    ))
    private val repository = InventoryRepository(dao)

    private fun itemViewModel(id: Int) = InventoryItemViewModel(repository, SavedStateHandle(mapOf(InventoryItemViewModel.ITEM_ID to id)))

    // ---- The list ----

    @Test
    fun itemsAreGroupedByCategoryIgnoringCase() = runTest(main.dispatcher) {
        val vm = InventoryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Irrigation", "Spraying", "Tools"), state.groups.map { it.category })
        val tools = state.groups.last()
        assertEquals(listOf("Axe", "knife"), tools.items.map { it.name })
        assertEquals(13, tools.totalCount)
        assertEquals(4, state.shownItems)
        assertEquals(15, state.shownCount)
    }

    @Test
    fun searchMatchesNameCategoryAndNotes() = runTest(main.dispatcher) {
        val vm = InventoryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.setQuery("honda")
        assertEquals(listOf("Power sprayer"), vm.uiState.value.groups.flatMap { it.items }.map { it.name })
        vm.setQuery(" TOOLS ")
        assertEquals(2, vm.uiState.value.shownItems)
        vm.setQuery("tractor")
        assertTrue(vm.uiState.value.groups.isEmpty())
        assertEquals("the empty-search message, not the empty-inventory one", 4, vm.uiState.value.itemCount)
    }

    @Test
    fun plusAndMinusChangeTheCountButNotBelowZero() = runTest(main.dispatcher) {
        val vm = InventoryViewModel(repository)
        backgroundScope.launch { vm.uiState.collect {} }
        fun count(id: Int) = vm.uiState.value.groups.flatMap { it.items }.first { it.id == id }.count

        vm.increment(dao.item(4)!!)
        assertEquals(1, count(4))
        vm.decrement(dao.item(4)!!)
        vm.decrement(dao.item(4)!!)
        assertEquals(0, count(4))
        vm.decrement(dao.item(2)!!)
        assertEquals(9, count(2))
    }

    // ---- One item's page ----

    @Test
    fun aNewItemIsAddedWithEveryField() = runTest(main.dispatcher) {
        val vm = itemViewModel(0)
        assertTrue(vm.uiState.value.isNew)
        assertTrue("categories already in use are offered", "Spraying" in vm.uiState.value.categories)
        assertEquals("'tools' is not offered twice", 1, vm.uiState.value.categories.count { it.equals("tools", true) })

        vm.updateForm {
            it.copy(name = "Weed cutter", category = "Equipment", count = "2",
                inUseSince = LocalDate.of(2024, 3, 15), purchasePrice = "18,500.00", notes = "Stihl")
        }
        vm.stepCount(+1)
        vm.save()

        assertTrue(vm.uiState.value.isDone)
        val saved = dao.allItems().first { it.name == "Weed cutter" }
        assertEquals(3, saved.count)
        assertEquals("Equipment", saved.category)
        assertEquals("2024-03-15", saved.inUseSince)
        assertEquals("18500", saved.purchasePrice)
        assertEquals("Stihl", saved.notes)
    }

    @Test
    fun anExistingItemIsLoadedEditedAndSaved() = runTest(main.dispatcher) {
        val vm = itemViewModel(1)
        assertEquals("Power sprayer", vm.uiState.value.form.name)
        assertFalse(vm.uiState.value.hasChanges)

        vm.updateForm { it.copy(count = "4", purchasePrice = "") }
        assertTrue(vm.uiState.value.hasChanges)
        vm.save()

        val saved = dao.item(1)!!
        assertEquals(4, saved.count)
        assertNull(saved.purchasePrice)
        assertEquals(4, dao.allItems().size)
    }

    @Test
    fun badInputCannotBeSaved() = runTest(main.dispatcher) {
        val vm = itemViewModel(0)

        vm.updateForm { it.copy(name = "Rake", count = "") }
        assertFalse(vm.uiState.value.canSave)
        vm.updateForm { it.copy(count = "2", purchasePrice = "abc") }
        assertTrue(vm.uiState.value.form.priceError)
        assertFalse(vm.uiState.value.canSave)
        vm.updateForm { it.copy(name = " ", purchasePrice = "") }
        assertFalse(vm.uiState.value.canSave)
        vm.save()

        assertEquals(4, dao.allItems().size)
        vm.stepCount(-5)
        assertEquals("0", vm.uiState.value.form.count)
    }

    @Test
    fun anItemCanBeDeleted() = runTest(main.dispatcher) {
        val vm = itemViewModel(3)
        vm.delete()

        assertTrue(vm.uiState.value.isDone)
        assertNull(dao.item(3))
    }

    @Test
    fun aMissingItemIsReported() = runTest(main.dispatcher) {
        val vm = itemViewModel(99)

        assertTrue(vm.uiState.value.notFound)
        assertFalse(vm.uiState.value.canSave)
    }
}
