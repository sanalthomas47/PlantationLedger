package com.santhomach.plantationledger.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.santhomach.plantationledger.data.export.ExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Writes one JSON snapshot of the whole database per day into the app's private
 * `auto_backups` folder and keeps only the most recent [MAX_BACKUPS] files.
 */
class DailyBackupManager(
    private val context: Context,
    private val exportManager: ExportManager,
    private val dataStore: DataStore<Preferences>
) {

    /**
     * Creates today's backup if it has not been created yet.
     */
    suspend fun maybeBackup() {
        val todayStr = LocalDate.now().toString()
        val prefs = dataStore.data.first()
        if (prefs[LAST_BACKUP_DATE_KEY] == todayStr) return

        val jsonData = exportManager.exportToJson()

        withContext(Dispatchers.IO) {
            val backupDir = File(context.filesDir, BACKUP_DIR)
            backupDir.mkdirs()
            File(backupDir, "backup_$todayStr.json").writeText(jsonData)

            // Keep only the newest MAX_BACKUPS files
            backupDir.listFiles()
                ?.sortedByDescending { it.name }
                ?.drop(MAX_BACKUPS)
                ?.forEach { it.delete() }
        }

        dataStore.edit { it[LAST_BACKUP_DATE_KEY] = todayStr }
    }

    companion object {
        private const val BACKUP_DIR = "auto_backups"
        private const val MAX_BACKUPS = 7
        private val LAST_BACKUP_DATE_KEY = stringPreferencesKey("last_auto_backup_date")
    }
}
