package com.santhomach.plantationledger.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.AdvanceEntry
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeEntry
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.WorkerGroupEntry
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

/**
 * Imports historical data from a simple CSV file.
 *
 * Expected columns: `date,type,category,count,rate,amount,notes`
 * where `type` is one of LABOR, EXPENSE, INCOME or ADVANCE.
 * Rows sharing the same date are merged into a single [DailyExpense].
 */
@HiltViewModel
class CsvImportViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val progress: Int = 0,
        val total: Int = 0,
        val importedCount: Int = 0,
        val skippedCount: Int = 0,
        val errors: List<String> = emptyList(),
        val isDone: Boolean = false,
        val templateSaved: Boolean = false,
        val templatePath: String = ""
    )

    private data class CsvRow(
        val date: String,
        val type: String,
        val category: String,
        val count: Int,
        val rate: BigDecimal,
        val amount: BigDecimal,
        val notes: String,
        val lineNumber: Int
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = UiState()
    }

    /**
     * Writes a sample CSV into the app's external Downloads folder so the user has a template.
     */
    fun saveTemplate() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val file = File(dir, "plantation_ledger_template.csv")
                file.writeText(
                    "date,type,category,count,rate,amount,notes\n" +
                        "2023-01-15,LABOR,Malayali Male,5,500,,Weeding\n" +
                        "2023-01-15,LABOR,Bengali Male,3,500,,Weeding\n" +
                        "2023-01-15,EXPENSE,Fertilizers,,,2500,NPK 50kg\n" +
                        "2023-01-15,INCOME,Cardamom Sales,,,15000,30kg at 500/kg\n" +
                        "2023-01-15,ADVANCE,Rajan,,,500,weekly advance\n"
                )
                _uiState.update { it.copy(templateSaved = true, templatePath = file.absolutePath) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errors = listOf("Could not save template: ${e.message}")) }
            }
        }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errors = emptyList(), isDone = false) }
            try {
                val lines = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readLines()
                }

                if (lines == null || lines.size < 2) {
                    _uiState.update {
                        it.copy(isLoading = false, isDone = true, errors = listOf("File is empty or missing header row"))
                    }
                    return@launch
                }

                // Skip header, blank lines and comment lines
                val dataLines = lines.drop(1).filter { line ->
                    line.isNotBlank() && !line.trimStart().startsWith("#")
                }
                _uiState.update { it.copy(progress = 0, total = dataLines.size) }

                // Master data lookups (by name)
                val expenseTypes = repository.getAllActiveExpenseTypes()
                    .associateBy { it.typeName }.toMutableMap()
                val incomeTypes = repository.getAllActiveIncomeTypes()
                    .associateBy { it.typeName }.toMutableMap()
                val workerTypes = repository.getAllActiveWorkerTypes()
                    .associateBy { it.workerTypeName }

                // ---- Parse rows ----
                val parseErrors = mutableListOf<String>()
                val validRows = mutableListOf<CsvRow>()

                dataLines.forEachIndexed { index, line ->
                    val lineNumber = index + 2
                    val cols = parseCsvLine(line)
                    if (cols.size < 2) {
                        if (line.isNotBlank()) {
                            parseErrors.add("Line $lineNumber: not enough columns, skipped")
                        }
                        return@forEachIndexed
                    }
                    val dateStr = cols[0].trim()
                    val type = cols[1].trim().uppercase(Locale.getDefault())
                    val category = (if (cols.size > 2) cols[2] else "").trim()
                    val count = (if (cols.size > 3) cols[3] else "0").trim().toIntOrNull() ?: 0
                    val rate = (if (cols.size > 4) cols[4] else "0").trim()
                        .replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val amount = (if (cols.size > 5) cols[5] else "0").trim()
                        .replace(",", "").toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val notes = (if (cols.size > 6) cols[6] else "").trim()

                    try {
                        LocalDate.parse(dateStr)
                        if (type !in setOf("LABOR", "EXPENSE", "INCOME", "ADVANCE")) {
                            parseErrors.add(
                                "Line $lineNumber: unknown type '$type' - use LABOR, EXPENSE, INCOME, or ADVANCE, skipped"
                            )
                            return@forEachIndexed
                        }
                        validRows.add(CsvRow(dateStr, type, category, count, rate, amount, notes, lineNumber))
                    } catch (e: Exception) {
                        parseErrors.add("Line $lineNumber: invalid date '$dateStr' - use YYYY-MM-DD, skipped")
                    }
                }

                // ---- Group by date ----
                val byDate = LinkedHashMap<String, MutableList<CsvRow>>()
                validRows.forEach { row -> byDate.getOrPut(row.date) { mutableListOf() }.add(row) }

                var imported = 0
                var skipped = 0
                var processed = 0
                val errors = mutableListOf<String>()

                byDate.forEach { (dateStr, rows) ->
                    try {
                        var totalLaborCost = BigDecimal.ZERO
                        var totalOtherExpenses = BigDecimal.ZERO
                        var totalIncome = BigDecimal.ZERO
                        var advanceAmount = BigDecimal.ZERO
                        val workerGroupsList = mutableListOf<WorkerGroupEntry>()
                        val otherExpensesList = mutableListOf<OtherExpenseEntry>()
                        val incomeEntriesList = mutableListOf<IncomeEntry>()
                        val advanceEntriesList = mutableListOf<AdvanceEntry>()

                        rows.forEach { row ->
                            when (row.type) {
                                "LABOR" -> {
                                    val workerType = workerTypes[row.category]
                                    val wage = when {
                                        row.rate > BigDecimal.ZERO -> row.rate
                                        row.count > 0 && row.amount > BigDecimal.ZERO ->
                                            row.amount.divide(BigDecimal.valueOf(row.count.toLong()), RoundingMode.HALF_UP)
                                        else -> workerType?.dailyBasicWage ?: BigDecimal.ZERO
                                    }
                                    totalLaborCost = totalLaborCost.add(wage.multiply(BigDecimal.valueOf(row.count.toLong())))
                                    workerGroupsList.add(
                                        WorkerGroupEntry(
                                            workerTypeId = workerType?.id ?: 0,
                                            workerTypeName = row.category,
                                            count = row.count,
                                            wagePerDay = wage,
                                            taskPerformed = row.notes
                                        )
                                    )
                                }

                                "EXPENSE" -> {
                                    val expenseType = expenseTypes[row.category] ?: run {
                                        val newId = repository.insertExpenseType(ExpenseType(typeName = row.category))
                                        ExpenseType(id = newId.toInt(), typeName = row.category).also {
                                            expenseTypes[row.category] = it
                                        }
                                    }
                                    totalOtherExpenses = totalOtherExpenses.add(row.amount)
                                    otherExpensesList.add(
                                        OtherExpenseEntry(
                                            expenseTypeId = expenseType.id,
                                            typeName = expenseType.typeName,
                                            amount = row.amount,
                                            notes = row.notes
                                        )
                                    )
                                }

                                "INCOME" -> {
                                    val incomeType = incomeTypes[row.category] ?: run {
                                        val newId = repository.insertIncomeType(IncomeType(typeName = row.category))
                                        IncomeType(id = newId.toInt(), typeName = row.category).also {
                                            incomeTypes[row.category] = it
                                        }
                                    }
                                    totalIncome = totalIncome.add(row.amount)
                                    incomeEntriesList.add(
                                        IncomeEntry(
                                            incomeTypeId = incomeType.id,
                                            typeName = incomeType.typeName,
                                            amount = row.amount,
                                            notes = row.notes
                                        )
                                    )
                                }

                                "ADVANCE" -> {
                                    advanceAmount = advanceAmount.add(row.amount)
                                    advanceEntriesList.add(
                                        AdvanceEntry(
                                            amount = row.amount,
                                            reason = row.notes,
                                            recipientName = row.category
                                        )
                                    )
                                }
                            }
                        }

                        val expense = DailyExpense(
                            date = dateStr,
                            // Labour is stored only as worker groups. Also filling the legacy
                            // per-type count fields made every later edit count it twice.
                            totalLaborCost = totalLaborCost,
                            otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), otherExpensesList),
                            totalOtherExpensesCost = totalOtherExpenses,
                            incomeEntries = Json.encodeToString(ListSerializer(IncomeEntry.serializer()), incomeEntriesList),
                            totalIncome = totalIncome,
                            advanceAmount = advanceAmount,
                            workerGroups = Json.encodeToString(ListSerializer(WorkerGroupEntry.serializer()), workerGroupsList),
                            advanceEntries = Json.encodeToString(ListSerializer(AdvanceEntry.serializer()), advanceEntriesList),
                            expenseAdditionType = "import"
                        )
                        repository.insertDailyExpense(expense)
                        imported++
                    } catch (e: Exception) {
                        errors.add("Date $dateStr: ${e.message}")
                        skipped++
                    }
                    processed += rows.size
                    _uiState.update {
                        it.copy(progress = processed, importedCount = imported, skippedCount = skipped)
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDone = true,
                        importedCount = imported,
                        skippedCount = skipped,
                        errors = parseErrors + errors
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isDone = true, errors = listOf("Import failed: ${e.message}"))
                }
            }
        }
    }

    /**
     * Minimal CSV parser supporting double-quoted fields.
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
