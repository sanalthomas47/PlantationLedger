package com.santhomach.plantationledger.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.santhomach.plantationledger.data.export.ExportException
import com.santhomach.plantationledger.data.export.ExportManager
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.FakeDataStore
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.money
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val exportManager: ExportManager = mockk(relaxed = true)
    private val repository: ExpenseRepository = mockk(relaxed = true)
    private val dataStore = FakeDataStore()
    private val vm by lazy { SettingsViewModel(exportManager, repository, dataStore) }

    private fun contextReading(text: String?): Context {
        val resolver: ContentResolver = mockk {
            every { openInputStream(any()) } returns text?.byteInputStream(Charsets.UTF_8)
        }
        return mockk { every { contentResolver } returns resolver }
    }

    @Test
    fun theChosenThemeIsRemembered() = runTest(main.dispatcher) {
        backgroundScope.launch { vm.themeMode.collect {} }
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)

        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.themeMode.value)

        // A new screen (or app start) reads the same setting.
        val again = SettingsViewModel(exportManager, repository, dataStore)
        backgroundScope.launch { again.themeMode.collect {} }
        assertEquals(ThemeMode.DARK, again.themeMode.value)
    }

    @Test
    fun masterDataIsSavedThroughTheRepository() = runTest(main.dispatcher) {
        vm.addWorkerType("Bengali Male", money(550))
        vm.addExpenseType("Labour Camp")
        vm.addWorkTask("Shade cutting")
        vm.addPermanentWorker("Raju", "Manager", money(900))

        coVerify { repository.insertWorkerType(match<WorkerType> { it.workerTypeName == "Bengali Male" && it.dailyBasicWage.compareTo(money(550)) == 0 }) }
        coVerify { repository.insertExpenseType(match<ExpenseType> { it.typeName == "Labour Camp" }) }
        coVerify { repository.insertWorkTask(match<WorkTask> { it.taskName == "Shade cutting" }) }
        coVerify { repository.insertPermanentWorker(match { it.name == "Raju" && it.role == "Manager" }) }
    }

    @Test
    fun aMasterDataErrorIsShownAndCanBeCleared() = runTest(main.dispatcher) {
        coEvery { repository.deleteExpenseType(any()) } throws IllegalStateException("type is in use")

        vm.deleteExpenseType(ExpenseType(id = 1, typeName = "Fuel"))
        assertEquals("type is in use", vm.uiState.value.error)

        vm.clearMessages()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun exportReportsWhereTheFileWasSaved() = runTest(main.dispatcher) {
        coEvery { exportManager.exportToFile(any()) } returns "Downloads/plantation_ledger_backup_1.json"

        vm.exportData(mockk())

        assertEquals("Data exported successfully to: Downloads/plantation_ledger_backup_1.json",
            vm.uiState.value.exportMessage)
        // The path is shown as a link to the Downloads folder.
        assertEquals("Downloads/plantation_ledger_backup_1.json", vm.uiState.value.exportedFilePath)
        assertFalse(vm.uiState.value.isExporting)

        vm.clearMessages()
        assertNull(vm.uiState.value.exportedFilePath)
    }

    @Test
    fun anImportMessageHasNoFileLink() = runTest(main.dispatcher) {
        coEvery { exportManager.exportToFile(any()) } returns "Downloads/plantation_ledger_backup_1.json"
        coEvery { exportManager.importFromJson(any()) } returns null

        vm.exportData(mockk())
        vm.importData(mockk<Uri>(), contextReading("{}"))

        assertTrue(vm.uiState.value.exportMessage!!.startsWith("Data imported successfully."))
        assertNull(vm.uiState.value.exportedFilePath)
    }

    @Test
    fun importPassesTheFileOnAndShowsAnyNote() = runTest(main.dispatcher) {
        coEvery { exportManager.importFromJson("""{"x":1}""") } returns "Your 2 vendor payments were kept."

        vm.importData(mockk<Uri>(), contextReading("""{"x":1}"""))

        val message = vm.uiState.value.exportMessage!!
        assertTrue(message.startsWith("Data imported successfully. Your 2 vendor payments were kept."))
        assertFalse(vm.uiState.value.isExporting)
    }

    @Test
    fun aFailedImportIsReportedAndNothingIsClaimed() = runTest(main.dispatcher) {
        coEvery { exportManager.importFromJson(any()) } throws ExportException("Failed to import data: bad file")

        vm.importData(mockk<Uri>(), contextReading("garbage"))
        assertEquals("Import failed: Failed to import data: bad file", vm.uiState.value.error)
        assertNull(vm.uiState.value.exportMessage)

        vm.clearMessages()
        vm.importData(mockk<Uri>(), contextReading(null))                 // the file could not be opened
        assertEquals("Import failed: Could not read file", vm.uiState.value.error)
        coVerify(exactly = 1) { exportManager.importFromJson(any()) }
        assertFalse(vm.uiState.value.isExporting)
    }
}
