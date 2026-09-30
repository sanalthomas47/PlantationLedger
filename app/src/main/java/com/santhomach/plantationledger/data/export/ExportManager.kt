package com.santhomach.plantationledger.data.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.room.withTransaction
import com.santhomach.plantationledger.data.AppDatabase
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExcessBalance
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.model.WeeklySettlement
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerPayment
import com.santhomach.plantationledger.data.model.WorkerType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime

/**
 * Full snapshot of the database used for backup / restore.
 */
@Serializable
data class ExportData(
    @SerialName("export_date")
    val exportDate: String,
    @SerialName("app_version")
    val appVersion: String = "1.1",
    @SerialName("daily_expenses")
    val dailyExpenses: List<DailyExpense>,
    @SerialName("weekly_settlements")
    val weeklySettlements: List<WeeklySettlement>,
    @SerialName("excess_balances")
    val excessBalances: List<ExcessBalance>,
    @SerialName("expense_types")
    val expenseTypes: List<ExpenseType>,
    @SerialName("expense_subtypes")
    val expenseSubtypes: List<ExpenseSubtype>,
    @SerialName("income_types")
    val incomeTypes: List<IncomeType>,
    @SerialName("worker_types")
    val workerTypes: List<WorkerType>,
    @SerialName("permanent_workers")
    val permanentWorkers: List<PermanentWorker>,
    @SerialName("work_tasks")
    val workTasks: List<WorkTask>,
    @SerialName("worker_payments")
    val workerPayments: List<WorkerPayment>,
    @SerialName("weekly_funds")
    val weeklyFunds: List<WeeklyFunds>,
    @SerialName("vendor_payments")
    val vendorPayments: List<VendorPayment> = emptyList(),
    @SerialName("receipts")
    val receipts: List<ExportReceipt> = emptyList()
)

/**
 * A receipt image embedded in the export as base64.
 */
@Serializable
data class ExportReceipt(
    val filename: String,
    val base64Data: String
)

class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exports the whole database (plus receipt images) to JSON and imports it back.
 */
class ExportManager(private val context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Serialises every table plus the receipt images into a JSON string.
     */
    suspend fun exportToJson(): String {
        try {
            val dailyExpenses = database.dailyExpenseDao().getRecent(Int.MAX_VALUE)
            val weeklySettlements = database.weeklySettlementDao().getRecent(Int.MAX_VALUE)
            val excessBalances = database.excessBalanceDao().getByDateRange("1900-01-01", "2100-12-31")
            val expenseTypes = database.expenseTypeDao().getAll()
            val expenseSubtypes = database.expenseSubtypeDao().getAll()
            val incomeTypes = database.incomeTypeDao().getAll()
            val workerTypes = database.workerTypeDao().getAll()
            val permanentWorkers = database.permanentWorkerDao().getAll()
            val workTasks = database.workTaskDao().getAll()
            val workerPayments = database.workerPaymentDao().getAll()
            val weeklyFunds = database.weeklyFundsDao().getAll()
            val vendorPayments = database.vendorPaymentDao().getAll()

            val receiptsDir = File(context.filesDir, RECEIPTS_DIR)
            val receipts = if (receiptsDir.exists()) {
                receiptsDir.listFiles()?.mapNotNull { file ->
                    try {
                        ExportReceipt(
                            filename = file.name,
                            base64Data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
            } else {
                emptyList()
            }

            val exportData = ExportData(
                exportDate = LocalDateTime.now().toString(),
                dailyExpenses = dailyExpenses,
                weeklySettlements = weeklySettlements,
                excessBalances = excessBalances,
                expenseTypes = expenseTypes,
                expenseSubtypes = expenseSubtypes,
                incomeTypes = incomeTypes,
                workerTypes = workerTypes,
                permanentWorkers = permanentWorkers,
                workTasks = workTasks,
                workerPayments = workerPayments,
                weeklyFunds = weeklyFunds,
                vendorPayments = vendorPayments,
                receipts = receipts
            )

            return json.encodeToString(ExportData.serializer(), exportData)
        } catch (e: Exception) {
            throw ExportException("Failed to export data: ${e.message}", e)
        }
    }

    /**
     * Writes the JSON export into the public Downloads folder via MediaStore.
     * @return the relative path of the saved file (e.g. `Downloads/plantation_ledger_backup_123.json`).
     */
    suspend fun exportToFile(
        filename: String = "plantation_ledger_backup_${System.currentTimeMillis()}.json"
    ): String {
        val jsonData = exportToJson()
        var uri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Could not create MediaStore entry")

            resolver.openOutputStream(uri)?.use { output ->
                output.write(jsonData.toByteArray(Charsets.UTF_8))
            } ?: throw Exception("Could not open output stream")

            return "Downloads/$filename"
        } catch (e: Exception) {
            uri?.let { context.contentResolver.delete(it, null, null) }
            throw ExportException("Failed to save export file to Downloads: ${e.message}", e)
        }
    }

    /**
     * Restores a JSON export produced by [exportToJson].
     *
     * @return an informational message for the user, or null when nothing special happened.
     */
    suspend fun importFromJson(jsonString: String): String? {
        try {
            if (jsonString.isBlank()) throw Exception("Import file is empty")

            val exportData = json.decodeFromString(ExportData.serializer(), jsonString)

            // Restore receipt images
            val receiptsDir = File(context.filesDir, RECEIPTS_DIR)
            if (!receiptsDir.exists() && !receiptsDir.mkdirs() && !receiptsDir.exists()) {
                throw Exception("Could not create receipts directory")
            }
            exportData.receipts.forEach { receipt ->
                try {
                    val bytes = Base64.decode(receipt.base64Data, Base64.DEFAULT)
                    File(receiptsDir, receipt.filename).writeBytes(bytes)
                } catch (e: Exception) {
                    // Skip corrupt receipt
                }
            }

            // Backups created before vendor payment tracking existed have no vendor payments.
            // In that case, existing vendor payments on the device are kept.
            val liveVendorPaymentCount = database.vendorPaymentDao().getAll().size
            val backupMissingVendorPayments = exportData.vendorPayments.isEmpty() && liveVendorPaymentCount > 0

            importDataSequentially(exportData)

            return if (backupMissingVendorPayments) {
                "Import complete. Note: this backup was created before vendor payment tracking was " +
                    "introduced. Your $liveVendorPaymentCount existing vendor payment record(s) have " +
                    "been preserved and were not overwritten."
            } else {
                null
            }
        } catch (e: Exception) {
            throw ExportException("Failed to import data: ${e.message}", e)
        }
    }

    /**
     * Inserts everything inside one transaction, masters first so foreign keys resolve.
     * Receipt image paths inside the daily expenses are rewritten to this device's receipts folder.
     */
    suspend fun importDataSequentially(exportData: ExportData) {
        val filesDir = context.filesDir.absolutePath

        val fixedDailyExpenses = exportData.dailyExpenses.map { expense ->
            var fixed = expense
            try {
                val otherExpenses = expense.otherExpenses
                if (otherExpenses.isNotBlank() && otherExpenses != "[]") {
                    val entries = json.decodeFromString(
                        ListSerializer(OtherExpenseEntry.serializer()),
                        otherExpenses
                    )
                    val fixedEntries = entries.map { entry ->
                        val path = entry.receiptImagePath
                        if (!path.isNullOrBlank()) {
                            entry.copy(
                                receiptImagePath = File(
                                    File(filesDir, RECEIPTS_DIR),
                                    File(path).name
                                ).absolutePath
                            )
                        } else {
                            entry
                        }
                    }
                    fixed = expense.copy(
                        otherExpenses = json.encodeToString(
                            ListSerializer(OtherExpenseEntry.serializer()),
                            fixedEntries
                        )
                    )
                }
            } catch (e: Exception) {
                // Keep the original row if the JSON cannot be parsed
            }
            fixed
        }

        database.withTransaction {
            val expenseTypeDao = database.expenseTypeDao()
            exportData.expenseTypes.forEach { expenseTypeDao.insert(it) }

            val incomeTypeDao = database.incomeTypeDao()
            exportData.incomeTypes.forEach { incomeTypeDao.insert(it) }

            val workerTypeDao = database.workerTypeDao()
            exportData.workerTypes.forEach { workerTypeDao.insert(it) }

            val permanentWorkerDao = database.permanentWorkerDao()
            exportData.permanentWorkers.forEach { permanentWorkerDao.insert(it) }

            val workTaskDao = database.workTaskDao()
            exportData.workTasks.forEach { workTaskDao.insert(it) }

            val expenseSubtypeDao = database.expenseSubtypeDao()
            exportData.expenseSubtypes.forEach { expenseSubtypeDao.insert(it) }

            val dailyExpenseDao = database.dailyExpenseDao()
            fixedDailyExpenses.forEach { dailyExpenseDao.insert(it) }

            val workerPaymentDao = database.workerPaymentDao()
            exportData.workerPayments.forEach { workerPaymentDao.insert(it) }

            val weeklySettlementDao = database.weeklySettlementDao()
            exportData.weeklySettlements.forEach { weeklySettlementDao.insert(it) }

            val excessBalanceDao = database.excessBalanceDao()
            exportData.excessBalances.forEach { excessBalanceDao.insert(it) }

            val weeklyFundsDao = database.weeklyFundsDao()
            exportData.weeklyFunds.forEach { weeklyFundsDao.insert(it) }

            val vendorPaymentDao = database.vendorPaymentDao()
            exportData.vendorPayments.forEach { vendorPaymentDao.insert(it) }
        }
    }

    companion object {
        private const val RECEIPTS_DIR = "receipts"
    }
}
