package com.santhomach.plantationledger.ui.viewmodel

import android.net.Uri
import com.santhomach.plantationledger.data.backup.BackupFile
import com.santhomach.plantationledger.data.backup.BackupKind
import com.santhomach.plantationledger.data.backup.BackupStore
import com.santhomach.plantationledger.data.export.ExportException
import com.santhomach.plantationledger.data.export.ExportManager
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    /** Backups kept in a list; [failDeleting] names files that refuse to be deleted. */
    private class FakeStore(initial: List<BackupFile>) : BackupStore {
        val files = initial.toMutableList()
        val failDeleting = mutableSetOf<String>()
        val contents = mutableMapOf<String, String>()

        override suspend fun list() = files.shuffled()
        override suspend fun delete(backup: BackupFile): Boolean {
            if (backup.name in failDeleting) return false
            return files.removeIf { it.id == backup.id }
        }
        override suspend fun readText(backup: BackupFile) = contents[backup.id] ?: "{}"
        override fun shareUri(backup: BackupFile): Uri = mockk()
    }

    private fun export(day: Int) = BackupFile("content://downloads/$day", "plantation_ledger_backup_$day.json",
        BackupKind.EXPORT, createdAtMillis = day * 86_400_000L, sizeBytes = 11_000_000)

    private fun auto(day: Int) = BackupFile("/data/auto_backups/backup_$day.json", "backup_$day.json",
        BackupKind.AUTO, createdAtMillis = day * 86_400_000L + 1, sizeBytes = 11_000_000)

    private val store = FakeStore(listOf(export(1), export(5), export(3), auto(2), auto(6), auto(4)))
    private val exportManager: ExportManager = mockk(relaxed = true)
    private val vm by lazy { BackupsViewModel(store, exportManager) }

    @Test
    fun listsEachKindNewestFirstAndProtectsTheNewest() = runTest(main.dispatcher) {
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(5, 3, 1).map { export(it).id }, state.exports.map { it.id })
        assertEquals(listOf(6, 4, 2).map { auto(it).id }, state.autoBackups.map { it.id })
        assertTrue(state.isProtected(export(5)))
        assertTrue(state.isProtected(auto(6)))
        assertFalse(state.isProtected(export(3)))
    }

    @Test
    fun deletesTheSelectedBackupsButNeverTheNewest() = runTest(main.dispatcher) {
        vm.toggleSelected(export(1))
        vm.toggleSelected(auto(2))
        vm.toggleSelected(export(5))                        // newest: cannot be selected
        assertEquals(setOf(export(1).id, auto(2).id), vm.uiState.value.selected)

        vm.deleteSelected()

        assertEquals(listOf(export(5), export(3), auto(6), auto(4)).map { it.id }.toSet(), store.files.map { it.id }.toSet())
        assertTrue(vm.uiState.value.selected.isEmpty())
        assertEquals("Deleted 2 backups.", vm.uiState.value.message)

        vm.delete(listOf(export(5), auto(6)))                // asked directly: still refused
        assertEquals(4, store.files.size)
    }

    @Test
    fun keepNewestOnlyTouchesThatKindAndAlwaysKeepsOne() = runTest(main.dispatcher) {
        assertEquals(listOf(export(1)).map { it.id }, vm.olderThanNewest(BackupKind.EXPORT, 2).map { it.id })
        assertEquals(2, vm.olderThanNewest(BackupKind.EXPORT, 0).size)   // 0 is treated as 1

        vm.keepNewest(BackupKind.EXPORT, 1)

        assertEquals(listOf(export(5).id), vm.uiState.value.exports.map { it.id })
        assertEquals(3, vm.uiState.value.autoBackups.size)
    }

    @Test
    fun aBackupThatCannotBeDeletedIsReported() = runTest(main.dispatcher) {
        store.failDeleting += export(1).name

        vm.delete(listOf(export(1), export(3)))

        assertEquals("Deleted 1 backup.", vm.uiState.value.message)
        assertEquals("Could not delete: plantation_ledger_backup_1.json", vm.uiState.value.error)
        assertTrue(vm.uiState.value.exports.any { it.id == export(1).id })
        assertFalse(vm.uiState.value.isWorking)
    }

    @Test
    fun restoreSavesASafetyExportBeforeImporting() = runTest(main.dispatcher) {
        store.contents[auto(4).id] = """{"backup":"day 4"}"""
        coEvery { exportManager.exportToFile(any()) } returns "Downloads/plantation_ledger_backup_9.json"
        coEvery { exportManager.importFromJson(any()) } returns null

        vm.restore(auto(4))

        coVerifyOrder {
            exportManager.exportToFile(any())
            exportManager.importFromJson("""{"backup":"day 4"}""")
        }
        assertEquals("Restored backup_4.json. Your data before the restore was saved to " +
            "Downloads/plantation_ledger_backup_9.json.", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isWorking)
    }

    @Test
    fun noRestoreHappensIfTheSafetyExportFails() = runTest(main.dispatcher) {
        coEvery { exportManager.exportToFile(any()) } throws ExportException("storage full")

        vm.restore(auto(4))

        coVerify(exactly = 0) { exportManager.importFromJson(any()) }
        assertTrue(vm.uiState.value.error!!.startsWith("Restore cancelled"))
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun aFailedRestoreSaysWhereTheSafetyExportIs() = runTest(main.dispatcher) {
        coEvery { exportManager.exportToFile(any()) } returns "Downloads/plantation_ledger_backup_9.json"
        coEvery { exportManager.importFromJson(any()) } throws ExportException("Failed to import data: bad file")

        vm.restore(export(3))

        assertEquals("Restore failed: Failed to import data: bad file. Your data before the restore was saved " +
            "to Downloads/plantation_ledger_backup_9.json.", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isWorking)
    }
}
