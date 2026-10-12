package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.WorkerPayment
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerPaymentViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val manager = PermanentWorker(id = 3, name = "Raju", role = "Manager", dailyBasicWage = money(900))
    private val existing = WorkerPayment(id = 11, workerId = 3, workerName = "Raju", amount = money(9_000),
        paymentDate = "2024-03-31", createdAt = "2024-03-31T18:00")
    private val repository: ExpenseRepository = mockk(relaxed = true) {
        every { getAllActivePermanentWorkersFlow() } returns flowOf(listOf(manager))
        every { getAllPaymentsFlow() } returns flowOf(listOf(existing))
    }

    private fun TestScope.viewModel() = WorkerPaymentViewModel(repository).also { vm ->
        backgroundScope.launch { vm.permanentWorkers.collect {} }
        backgroundScope.launch { vm.allPayments.collect {} }
    }

    @Test
    fun recordsAPaymentUnderTheWorkersName() = runTest(main.dispatcher) {
        val saved = slot<WorkerPayment>()
        coEvery { repository.insertWorkerPayment(capture(saved)) } returns 12L
        val vm = viewModel()

        vm.recordPayment(3, money(9_500), LocalDate.of(2024, 4, 30), "MONTHLY", "April")

        assertEquals("Raju", saved.captured.workerName)
        assertEquals("2024-04-30", saved.captured.paymentDate)
        assertMoney(9_500, saved.captured.amount)
        assertEquals("Payment recorded successfully", vm.uiState.value.successMessage)
        assertFalse(vm.uiState.value.isSaving)

        vm.recordPayment(42, money(1), LocalDate.of(2024, 4, 30), "ADVANCE", "")
        assertEquals("Unknown", saved.captured.workerName)
    }

    @Test
    fun editingKeepsTheRecordIdAndWhenItWasCreated() = runTest(main.dispatcher) {
        val vm = viewModel()

        vm.updatePayment(11, 3, money(9_200), LocalDate.of(2024, 3, 30), "MONTHLY", "corrected")

        coVerify {
            repository.updateWorkerPayment(existing.copy(amount = money(9_200), paymentDate = "2024-03-30",
                notes = "corrected"))
        }
        assertEquals("Payment updated successfully", vm.uiState.value.successMessage)
    }

    @Test
    fun editingAPaymentThatNoLongerExistsShowsAnError() = runTest(main.dispatcher) {
        val vm = viewModel()

        vm.updatePayment(99, 3, money(1), LocalDate.of(2024, 3, 30), "MONTHLY", "")

        assertEquals("Payment record not found", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
        coVerify(exactly = 0) { repository.updateWorkerPayment(any()) }
    }

    @Test
    fun deletesAPaymentAndClearsMessages() = runTest(main.dispatcher) {
        val vm = viewModel()

        vm.deletePayment(11)
        coVerify { repository.deleteWorkerPayment(existing) }
        assertEquals("Payment deleted successfully", vm.uiState.value.successMessage)

        vm.clearMessages()
        assertNull(vm.uiState.value.successMessage)
    }

    @Test
    fun aFailedSaveIsShownAndTheButtonIsReleased() = runTest(main.dispatcher) {
        coEvery { repository.insertWorkerPayment(any()) } throws IllegalStateException("disk full")
        val vm = viewModel()

        vm.recordPayment(3, money(1), LocalDate.of(2024, 4, 30), "MONTHLY", "")

        assertEquals("disk full", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isSaving)
        assertNull(vm.uiState.value.successMessage)
    }

    @Test
    fun looksUpAnExistingPaymentByWorkerAndDay() = runTest(main.dispatcher) {
        coEvery { repository.getPaymentByWorkerAndDate(3, "2024-03-31") } returns existing
        assertEquals(existing, viewModel().getExistingPayment(3, LocalDate.of(2024, 3, 31)))
    }
}
