package com.santhomach.plantationledger.data.backup

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.santhomach.plantationledger.data.export.ExportException
import com.santhomach.plantationledger.data.export.ExportManager
import com.santhomach.plantationledger.testutil.FakeDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class DailyBackupManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val lastBackupKey = stringPreferencesKey("last_auto_backup_date")
    private val today = LocalDate.now().toString()
    private val exportManager: ExportManager = mockk { coEvery { exportToJson() } returns """{"backup":1}""" }
    private val dataStore = FakeDataStore()

    private fun manager(): DailyBackupManager {
        val context: Context = mockk { every { filesDir } returns tmp.root }
        return DailyBackupManager(context, exportManager, dataStore)
    }

    private fun backupDir() = File(tmp.root, "auto_backups")

    @Test
    fun writesTodaysBackupOnlyOnceADay() = runTest {
        val manager = manager()
        manager.maybeBackup()
        manager.maybeBackup()

        assertEquals("""{"backup":1}""", File(backupDir(), "backup_$today.json").readText())
        assertEquals(today, dataStore.data.first()[lastBackupKey])
        coVerify(exactly = 1) { exportManager.exportToJson() }
    }

    @Test
    fun keepsOnlyTheSevenNewestBackups() = runTest {
        backupDir().mkdirs()
        (1..9).forEach { day -> File(backupDir(), "backup_2020-01-0$day.json").writeText("old") }

        manager().maybeBackup()

        val names = backupDir().list()!!.sorted()
        assertEquals(7, names.size)
        assertEquals("backup_$today.json", names.last())
        assertEquals("backup_2020-01-04.json", names.first())      // 01 to 03 removed
    }

    @Test
    fun aFailedBackupIsTriedAgainNextTime() = runTest {
        coEvery { exportManager.exportToJson() } throws ExportException("disk full")
        val manager = manager()

        assertTrue(runCatching { manager.maybeBackup() }.isFailure)
        assertNull(dataStore.data.first()[lastBackupKey])
        assertFalse(File(backupDir(), "backup_$today.json").exists())

        coEvery { exportManager.exportToJson() } returns "{}"
        manager.maybeBackup()
        assertEquals("{}", File(backupDir(), "backup_$today.json").readText())
    }
}
