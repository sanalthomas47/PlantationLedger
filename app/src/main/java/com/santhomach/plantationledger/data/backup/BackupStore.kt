package com.santhomach.plantationledger.data.backup

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class BackupKind { EXPORT, AUTO }

/**
 * One backup file. [id] is the MediaStore content URI for an export (Downloads) or the absolute
 * path for an automatic daily backup (app-private `auto_backups` folder).
 */
data class BackupFile(
    val id: String,
    val name: String,
    val kind: BackupKind,
    val createdAtMillis: Long,
    val sizeBytes: Long
)

/** Lists, reads and deletes the app's backups. */
interface BackupStore {
    suspend fun list(): List<BackupFile>
    suspend fun delete(backup: BackupFile): Boolean
    suspend fun readText(backup: BackupFile): String

    /** A content URI another app can read, for sharing. */
    fun shareUri(backup: BackupFile): Uri
}

/**
 * Exports are the `plantation_ledger_backup_*.json` files this app saved to Downloads. Android only
 * lets an app see the Downloads files it created itself, so files from other apps, a computer or an
 * earlier installation are not listed.
 */
class AndroidBackupStore(private val context: Context) : BackupStore {

    private val autoDir get() = File(context.filesDir, DailyBackupManager.BACKUP_DIR)

    /** If one kind cannot be read (e.g. Downloads is unavailable), the other is still listed. */
    override suspend fun list(): List<BackupFile> = withContext(Dispatchers.IO) {
        val exports = runCatching { listExports() }.getOrDefault(emptyList())
        val autos = runCatching { listAutoBackups() }.getOrDefault(emptyList())
        (exports + autos).sortedByDescending { it.createdAtMillis }
    }

    private fun listExports(): List<BackupFile> {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val result = mutableListOf<BackupFile>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$EXPORT_PREFIX%.json"),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                result += BackupFile(
                    id = ContentUris.withAppendedId(collection, cursor.getLong(idCol)).toString(),
                    name = name,
                    kind = BackupKind.EXPORT,
                    createdAtMillis = exportTimeFromName(name) ?: (cursor.getLong(addedCol) * 1000),
                    sizeBytes = cursor.getLong(sizeCol)
                )
            }
        }
        return result
    }

    private fun listAutoBackups(): List<BackupFile> =
        autoDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.map { file ->
                BackupFile(
                    id = file.absolutePath,
                    name = file.name,
                    kind = BackupKind.AUTO,
                    createdAtMillis = file.lastModified(),
                    sizeBytes = file.length()
                )
            }
            ?: emptyList()

    override suspend fun delete(backup: BackupFile): Boolean = withContext(Dispatchers.IO) {
        when (backup.kind) {
            BackupKind.EXPORT -> context.contentResolver.delete(Uri.parse(backup.id), null, null) > 0
            BackupKind.AUTO -> autoFile(backup).delete()
        }
    }

    override suspend fun readText(backup: BackupFile): String = withContext(Dispatchers.IO) {
        when (backup.kind) {
            BackupKind.EXPORT -> context.contentResolver.openInputStream(Uri.parse(backup.id))
                ?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                ?: throw IllegalStateException("Could not open ${backup.name}")
            BackupKind.AUTO -> autoFile(backup).readText(Charsets.UTF_8)
        }
    }

    override fun shareUri(backup: BackupFile): Uri = when (backup.kind) {
        BackupKind.EXPORT -> Uri.parse(backup.id)
        BackupKind.AUTO -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", autoFile(backup))
    }

    /** Only files inside the auto-backup folder can be touched. */
    private fun autoFile(backup: BackupFile): File {
        val file = File(backup.id).canonicalFile
        require(file.parentFile == autoDir.canonicalFile) { "Not an automatic backup: ${backup.name}" }
        return file
    }

    companion object {
        const val EXPORT_PREFIX = "plantation_ledger_backup_"

        /** `plantation_ledger_backup_1792512369592.json` was saved at 1792512369592 ms. */
        fun exportTimeFromName(name: String): Long? =
            name.removePrefix(EXPORT_PREFIX).substringBefore('.').substringBefore(' ').toLongOrNull()
                ?.takeIf { name.startsWith(EXPORT_PREFIX) }
    }
}
