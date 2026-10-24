package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.santhomach.plantationledger.data.schedule.CombinationCard
import com.santhomach.plantationledger.data.schedule.CombinationCategory
import com.santhomach.plantationledger.data.schedule.ScheduleRepository
import com.santhomach.plantationledger.testutil.FakeScheduleDao
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CombinationCardViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val dao = FakeScheduleDao(
        cards = listOf(
            CombinationCard(id = 7, category = "PESTICIDE", name = "Thrips", purpose = "Thrips on panicles",
                createdAt = "2026-10-01T10:00", updatedAt = "2026-10-01T10:00")
        )
    )

    private fun viewModel(id: Int = 7) =
        CombinationCardViewModel(ScheduleRepository(dao), SavedStateHandle(mapOf(CombinationCardViewModel.CARD_ID to id)))

    @Test
    fun loadsTheCardIntoTheForm() = runTest(main.dispatcher) {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(CombinationCategory.PESTICIDE, state.category)
        assertEquals(CardForm(name = "Thrips", purpose = "Thrips on panicles"), state.form)
        assertFalse(state.hasChanges)
    }

    @Test
    fun savesEveryFieldToTheDatabase() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.updateForm {
            it.copy(name = " Thrips - flowering ", combination = "Spinosad 0.3 ml/L\n+ sticker",
                usage = "  Spray panicles every 21 days ", comments = "Avoid noon")
        }
        assertTrue(vm.uiState.value.hasChanges)

        vm.save()

        val saved = dao.cards.value.single()
        assertEquals("Thrips - flowering", saved.name)
        assertEquals("Thrips on panicles", saved.purpose)
        assertEquals("Spinosad 0.3 ml/L\n+ sticker", saved.combination)
        assertEquals("Spray panicles every 21 days", saved.usage)
        assertEquals("Avoid noon", saved.comments)
        assertEquals("PESTICIDE", saved.category)
        assertEquals("2026-10-01T10:00", saved.createdAt)
        assertTrue(saved.updatedAt > "2026-10-01T10:00")
        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun aCardNeedsAName() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.updateForm { it.copy(name = "   ") }

        assertFalse(vm.uiState.value.canSave)
        vm.save()

        assertEquals("Thrips", dao.cards.value.single().name)
        assertFalse(vm.uiState.value.isDone)
    }

    @Test
    fun deletesTheCard() = runTest(main.dispatcher) {
        val vm = viewModel()

        vm.delete()

        assertTrue(dao.cards.value.isEmpty())
        assertTrue(vm.uiState.value.isDone)
    }

    @Test
    fun aMissingCardIsReported() = runTest(main.dispatcher) {
        val state = viewModel(id = 99).uiState.value
        assertTrue(state.notFound)
        assertFalse(state.canSave && state.card != null)
    }
}
