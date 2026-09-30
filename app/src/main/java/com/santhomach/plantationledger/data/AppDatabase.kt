package com.santhomach.plantationledger.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.santhomach.plantationledger.data.dao.DailyExpenseDao
import com.santhomach.plantationledger.data.dao.ExcessBalanceDao
import com.santhomach.plantationledger.data.dao.ExpenseSubtypeDao
import com.santhomach.plantationledger.data.dao.ExpenseTypeDao
import com.santhomach.plantationledger.data.dao.IncomeTypeDao
import com.santhomach.plantationledger.data.dao.PermanentWorkerDao
import com.santhomach.plantationledger.data.dao.VendorPaymentDao
import com.santhomach.plantationledger.data.dao.WeeklyFundsDao
import com.santhomach.plantationledger.data.dao.WeeklySettlementDao
import com.santhomach.plantationledger.data.dao.WorkTaskDao
import com.santhomach.plantationledger.data.dao.WorkerPaymentDao
import com.santhomach.plantationledger.data.dao.WorkerTypeDao
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExcessBalance
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.model.WeeklySettlement
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerPayment
import com.santhomach.plantationledger.data.model.WorkerType
import java.io.File

@Database(
    entities = [
        DailyExpense::class,
        ExpenseType::class,
        ExpenseSubtype::class,
        IncomeType::class,
        WorkerType::class,
        PermanentWorker::class,
        WeeklySettlement::class,
        ExcessBalance::class,
        WorkTask::class,
        WorkerPayment::class,
        WeeklyFunds::class,
        VendorPayment::class
    ],
    version = AppDatabase.DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dailyExpenseDao(): DailyExpenseDao
    abstract fun expenseTypeDao(): ExpenseTypeDao
    abstract fun expenseSubtypeDao(): ExpenseSubtypeDao
    abstract fun incomeTypeDao(): IncomeTypeDao
    abstract fun workerTypeDao(): WorkerTypeDao
    abstract fun permanentWorkerDao(): PermanentWorkerDao
    abstract fun weeklySettlementDao(): WeeklySettlementDao
    abstract fun excessBalanceDao(): ExcessBalanceDao
    abstract fun workTaskDao(): WorkTaskDao
    abstract fun workerPaymentDao(): WorkerPaymentDao
    abstract fun weeklyFundsDao(): WeeklyFundsDao
    abstract fun vendorPaymentDao(): VendorPaymentDao

    companion object {
        const val DATABASE_VERSION = 11
        private const val DATABASE_NAME = "plantation_ledger.db"
        private const val BACKUP_NAME = "pre_upgrade_backup.db"

        private val ALL_TABLES = listOf(
            "expense_types",
            "expense_subtypes",
            "income_types",
            "worker_types",
            "permanent_workers",
            "work_tasks",
            "daily_expenses",
            "weekly_settlements",
            "excess_balances",
            "worker_payments",
            "weekly_funds",
            "vendor_payments"
        )

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_expenses ADD COLUMN extraOvertimeAmount TEXT NOT NULL DEFAULT '0'")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `vendor_payments` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `date` TEXT NOT NULL,
    `amount` TEXT NOT NULL,
    `vendorName` TEXT NOT NULL DEFAULT '',
    `notes` TEXT NOT NULL DEFAULT '',
    `createdAt` TEXT NOT NULL
)"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vendor_payments_date` ON `vendor_payments` (`date`)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `vendor_payments` ADD COLUMN `sourceExpenseDate` TEXT")
                db.execSQL("ALTER TABLE `vendor_payments` ADD COLUMN `sourceTypeName` TEXT")
                db.execSQL("ALTER TABLE `vendor_payments` ADD COLUMN `sourceSubtypeName` TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Builds the database. If an existing database with a different version is found (and no
         * explicit migration path is available), it is moved aside to a backup file and the data is
         * copied back table-by-table by [AutoRestoreCallback] once the fresh schema is created.
         */
        private fun buildDatabase(context: Context): AppDatabase {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val backupFile = File(context.filesDir, BACKUP_NAME)

            if (dbFile.exists() && !backupFile.exists()) {
                val existingVersion = readDatabaseVersion(dbFile)
                if (existingVersion > 0 && existingVersion != DATABASE_VERSION) {
                    if (copyDatabaseFiles(dbFile, backupFile)) {
                        deleteFile(dbFile)
                    }
                }
            }

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .addCallback(AutoRestoreCallback(backupFile))
                .build()
        }

        private fun readDatabaseVersion(file: File): Int {
            return try {
                SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.version
                }
            } catch (e: Exception) {
                -1
            }
        }

        private fun copyDatabaseFiles(source: File, dest: File): Boolean {
            return try {
                source.copyTo(dest, overwrite = true)
                for (suffix in listOf("-wal", "-shm")) {
                    val extra = File(source.absolutePath + suffix)
                    if (extra.exists()) {
                        extra.copyTo(File(dest.absolutePath + suffix), overwrite = true)
                    }
                }
                true
            } catch (e: Exception) {
                dest.delete()
                File(dest.absolutePath + "-wal").delete()
                File(dest.absolutePath + "-shm").delete()
                false
            }
        }

        private fun deleteFile(file: File) {
            file.delete()
            File(file.absolutePath + "-wal").delete()
            File(file.absolutePath + "-shm").delete()
        }

        /**
         * Copies rows from the pre-upgrade backup database into the freshly created schema.
         * Only columns that exist in both the backup and the new schema are copied.
         */
        private class AutoRestoreCallback(private val backupFile: File) : RoomDatabase.Callback() {

            override fun onOpen(db: SupportSQLiteDatabase) {
                if (!backupFile.exists()) return

                val escapedPath = backupFile.absolutePath.replace("'", "''")
                var restored = false
                try {
                    db.execSQL("PRAGMA foreign_keys = OFF")
                    db.execSQL("ATTACH DATABASE '$escapedPath' AS backup")
                    try {
                        db.beginTransaction()
                        try {
                            ALL_TABLES.forEach { table -> restoreTable(db, table) }
                            db.setTransactionSuccessful()
                        } finally {
                            db.endTransaction()
                        }
                        restored = true
                    } finally {
                        db.execSQL("DETACH DATABASE backup")
                    }
                } catch (e: Exception) {
                    // Restore is best-effort; a failure leaves the fresh database in place.
                } finally {
                    db.execSQL("PRAGMA foreign_keys = ON")
                    if (restored) {
                        backupFile.delete()
                        File(backupFile.absolutePath + "-wal").delete()
                        File(backupFile.absolutePath + "-shm").delete()
                    }
                }
            }

            private fun restoreTable(db: SupportSQLiteDatabase, table: String) {
                try {
                    val commonColumns = tableColumns(db, "main", table)
                        .intersect(tableColumns(db, "backup", table))
                    if (commonColumns.isEmpty()) return
                    val columnList = commonColumns.joinToString(", ") { "\"$it\"" }
                    db.execSQL(
                        "INSERT OR IGNORE INTO \"$table\" ($columnList) SELECT $columnList FROM backup.\"$table\""
                    )
                } catch (e: Exception) {
                    // Table may not exist in the backup; skip it.
                }
            }

            private fun tableColumns(db: SupportSQLiteDatabase, schema: String, table: String): Set<String> {
                val columns = LinkedHashSet<String>()
                try {
                    db.query("PRAGMA $schema.table_info($table)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        if (nameIndex >= 0) {
                            while (cursor.moveToNext()) {
                                columns.add(cursor.getString(nameIndex))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // ignore
                }
                return columns
            }
        }
    }
}
