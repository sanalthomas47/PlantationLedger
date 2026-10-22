package com.santhomach.plantationledger.data.backup

import android.content.ContentResolver
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The automatic daily backups part of the store (exports need a real MediaStore). */
class AndroidBackupStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy {
        val resolver: ContentResolver = mockk { every { query(any(), any(), any(), any(), any()) } returns null }
        val context: Context = mockk {
            every { filesDir } returns tmp.root
            every { contentResolver } returns resolver
        }
        AndroidBackupStore(context)
    }

    private fun autoBackup(name: String, text: String, modified: Long) =
        File(tmp.root, "auto_backups").apply { mkdirs() }.resolve(name).apply {
            writeText(text)
            setLastModified(modified)
        }

    @Test
    fun listsDailyBackupsNewestFirstAndIgnoresOtherFiles() = runTest {
        autoBackup("backup_2026-10-20.json", "{\"a\":1}", 2_000_000_000_000)
        autoBackup("backup_2026-10-21.json", "{\"a\":2}", 2_000_100_000_000)
        autoBackup("notes.txt", "x", 2_000_200_000_000)

        val list = store.list()

        assertEquals(listOf("backup_2026-10-21.json", "backup_2026-10-20.json"), list.map { it.name })
        assertTrue(list.all { it.kind == BackupKind.AUTO })
        assertEquals(7L, list.first().sizeBytes)
        assertEquals("{\"a\":2}", store.readText(list.first()))
    }

    @Test
    fun deletesADailyBackup() = runTest {
        val file = autoBackup("backup_2026-10-20.json", "{}", 2_000_000_000_000)

        assertTrue(store.delete(store.list().single()))

        assertFalse(file.exists())
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun refusesToTouchFilesOutsideTheBackupFolder() = runTest {
        val secret = tmp.newFile("plantation_ledger.db").apply { writeText("database") }
        val outside = BackupFile(secret.absolutePath, secret.name, BackupKind.AUTO, 0, 0)
        val sneaky = outside.copy(id = File(tmp.root, "auto_backups/../plantation_ledger.db").path)

        assertTrue(runCatching { store.delete(outside) }.isFailure)
        assertTrue(runCatching { store.delete(sneaky) }.isFailure)
        assertTrue(runCatching { store.readText(sneaky) }.isFailure)
        assertTrue(secret.exists())
    }

    @Test
    fun readsTheExportTimeFromTheFileName() {
        assertEquals(1792512369592L, AndroidBackupStore.exportTimeFromName("plantation_ledger_backup_1792512369592.json"))
        assertEquals(1792512369592L, AndroidBackupStore.exportTimeFromName("plantation_ledger_backup_1792512369592 (1).json"))
        assertNull(AndroidBackupStore.exportTimeFromName("plantation_ledger_backup_copy.json"))
        assertNull(AndroidBackupStore.exportTimeFromName("other_1792512369592.json"))
    }
}
