package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
import com.santhomach.plantationledger.data.model.WorkerGroupEntry
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class TrendDirection { UP, DOWN, NEUTRAL }

data class SubtypeRow(
    val subtypeName: String,
    val thisWeekAmount: BigDecimal,
    val thisYearAmount: BigDecimal,
    val allTimeAmount: BigDecimal
)

data class ExpenseTypeRow(
    val typeName: String,
    val thisWeekAmount: BigDecimal,
    val thisYearAmount: BigDecimal,
    val allTimeAmount: BigDecimal,
    val thisWeekPercent: Float,
    val thisYearPercent: Float,
    val allTimePercent: Float,
    val weekTrend: TrendDirection,
    val yearTrend: TrendDirection,
    val subtypes: List<SubtypeRow>,
    val isExpanded: Boolean = false
)

/** One labour type's share of a task. */
data class TaskLabourRow(
    val labourType: String,
    val thisYearAmount: BigDecimal,
    val allTimeAmount: BigDecimal
)

/** Labour cost (wages + overtime) of one work task. */
data class TaskRow(
    val taskName: String,
    val thisYearAmount: BigDecimal,
    val allTimeAmount: BigDecimal,
    val thisYearPercent: Float,
    val allTimePercent: Float,
    val labourTypes: List<TaskLabourRow>,
    val isExpanded: Boolean = false
)

data class ExpenseTypeSummaryUiState(
    val rows: List<ExpenseTypeRow> = emptyList(),
    val thisWeekTotal: BigDecimal = BigDecimal.ZERO,
    val thisYearTotal: BigDecimal = BigDecimal.ZERO,
    val allTimeTotal: BigDecimal = BigDecimal.ZERO,
    /** Labour by work task, largest all-time cost first. */
    val taskRows: List<TaskRow> = emptyList(),
    val taskThisYearTotal: BigDecimal = BigDecimal.ZERO,
    val taskAllTimeTotal: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Breaks every expense down by type and subtype for this week, this year and all time: labour (by
 * labour type, plus overtime) and each "other expense" type.
 */
@HiltViewModel
class ExpenseTypeSummaryViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _expandedTypes = MutableStateFlow<Set<String>>(emptySet())
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    private val allExpenses = repository.getDailyExpensesByDateRangeFlow("1900-01-01", "2100-12-31")

    val uiState: StateFlow<ExpenseTypeSummaryUiState> =
        combine(allExpenses, _expandedTypes) { expenses, expanded ->
            buildUiState(
                expenses,
                expanded
            )
        }
            .catch { e -> emit(ExpenseTypeSummaryUiState(isLoading = false, error = e.message)) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                ExpenseTypeSummaryUiState(isLoading = true)
            )

    fun toggleExpanded(typeName: String) {
        _expandedTypes.update { set ->
            if (set.contains(typeName)) set - typeName else set + typeName
        }
    }

    fun toggleTaskExpanded(taskName: String) = toggleExpanded(TASK_KEY_PREFIX + taskName)

    private fun thisWeekStart() = LocalDate.now().with(DayOfWeek.MONDAY).format(iso)
    private fun thisWeekEnd() = LocalDate.now().with(DayOfWeek.SATURDAY).format(iso)
    private fun prevWeekStart() = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1).format(iso)
    private fun prevWeekEnd() = LocalDate.now().with(DayOfWeek.SATURDAY).minusWeeks(1).format(iso)
    private fun thisYearStart() = LocalDate.now().withDayOfYear(1).format(iso)
    private fun thisYearEnd() = LocalDate.now().format(iso)
    private fun prevYearStart() = LocalDate.now().minusYears(1).withDayOfYear(1).format(iso)
    private fun prevYearEnd() =
        LocalDate.now().minusYears(1).with(TemporalAdjusters.lastDayOfYear()).format(iso)

    private fun buildUiState(
        expenses: List<DailyExpense>,
        expandedTypes: Set<String>
    ): ExpenseTypeSummaryUiState {
        val weekStart = thisWeekStart()
        val weekEnd = thisWeekEnd()
        val prevWeekStart = prevWeekStart()
        val prevWeekEnd = prevWeekEnd()
        val yearStart = thisYearStart()
        val yearEnd = thisYearEnd()
        val prevYearStart = prevYearStart()
        val prevYearEnd = prevYearEnd()

        val thisWeek = expenses.filter { it.date >= weekStart && it.date <= weekEnd }
        val prevWeek = expenses.filter { it.date >= prevWeekStart && it.date <= prevWeekEnd }
        val thisYear = expenses.filter { it.date >= yearStart && it.date <= yearEnd }
        val prevYear = expenses.filter { it.date >= prevYearStart && it.date <= prevYearEnd }

        val weekMap = buildTypeMap(thisWeek)
        val prevWeekMap = buildTypeMap(prevWeek)
        val yearMap = buildTypeMap(thisYear)
        val prevYearMap = buildTypeMap(prevYear)
        val allMap = buildTypeMap(expenses)

        val weekTotal = weekMap.values.sumOf { it.values.sumOf { v -> v } }
        val yearTotal = yearMap.values.sumOf { it.values.sumOf { v -> v } }
        val allTotal = allMap.values.sumOf { it.values.sumOf { v -> v } }

        // Labour first (usually the largest cost), then the other expense types by name.
        val typeOrder = allMap.keys.sortedWith(compareBy({ it != LABOUR }, { it }))
        val rows = typeOrder.map { typeName ->
            // Labour types by name with Overtime last; other types' subtypes by name.
            val subtypeNames = (allMap[typeName]?.keys ?: emptySet())
                .sortedWith(compareBy({ it == OVERTIME || it == OTHER_LABOUR }, { it }))

            val weekAmount = weekMap[typeName]?.values?.sumOf { it } ?: BigDecimal.ZERO
            val prevWeekAmount = prevWeekMap[typeName]?.values?.sumOf { it } ?: BigDecimal.ZERO
            val yearAmount = yearMap[typeName]?.values?.sumOf { it } ?: BigDecimal.ZERO
            val prevYearAmount = prevYearMap[typeName]?.values?.sumOf { it } ?: BigDecimal.ZERO
            val allAmount = allMap[typeName]?.values?.sumOf { it } ?: BigDecimal.ZERO

            val subtypes = subtypeNames.map { subtypeName ->
                SubtypeRow(
                    subtypeName = subtypeName,
                    thisWeekAmount = weekMap[typeName]?.get(subtypeName) ?: BigDecimal.ZERO,
                    thisYearAmount = yearMap[typeName]?.get(subtypeName) ?: BigDecimal.ZERO,
                    allTimeAmount = allMap[typeName]?.get(subtypeName) ?: BigDecimal.ZERO
                )
            }

            ExpenseTypeRow(
                typeName = typeName,
                thisWeekAmount = weekAmount,
                thisYearAmount = yearAmount,
                allTimeAmount = allAmount,
                thisWeekPercent = percent(weekAmount, weekTotal),
                thisYearPercent = percent(yearAmount, yearTotal),
                allTimePercent = percent(allAmount, allTotal),
                weekTrend = trend(weekAmount, prevWeekAmount),
                yearTrend = trend(yearAmount, prevYearAmount),
                subtypes = subtypes,
                isExpanded = expandedTypes.contains(typeName)
            )
        }

        val yearTasks = buildTaskMap(thisYear)
        val allTasks = buildTaskMap(expenses)
        val taskYearTotal = yearTasks.values.sumOf { it.values.sumOf { v -> v } }
        val taskAllTotal = allTasks.values.sumOf { it.values.sumOf { v -> v } }
        val taskRows = allTasks.map { (task, byType) ->
            val yearAmount = yearTasks[task]?.values?.sumOf { it } ?: BigDecimal.ZERO
            val allAmount = byType.values.sumOf { it }
            TaskRow(
                taskName = task,
                thisYearAmount = yearAmount,
                allTimeAmount = allAmount,
                thisYearPercent = percent(yearAmount, taskYearTotal),
                allTimePercent = percent(allAmount, taskAllTotal),
                labourTypes = byType.keys.filter { it.isNotEmpty() }.sorted().map { type ->
                    TaskLabourRow(type, yearTasks[task]?.get(type) ?: BigDecimal.ZERO, byType.getValue(type))
                },
                isExpanded = expandedTypes.contains(TASK_KEY_PREFIX + task)
            )
        }.sortedWith(compareBy<TaskRow>({ it.taskName == OVERTIME_NO_TASK || it.taskName == NO_TASK })
            .thenByDescending { it.allTimeAmount }
            .thenBy { it.taskName })

        return ExpenseTypeSummaryUiState(
            rows = rows,
            thisWeekTotal = weekTotal,
            thisYearTotal = yearTotal,
            allTimeTotal = allTotal,
            taskRows = taskRows,
            taskThisYearTotal = taskYearTotal,
            taskAllTimeTotal = taskAllTotal,
            isLoading = false,
            error = null
        )
    }

    /**
     * task -> (labour type -> wages + overtime). Overtime entered for the whole day has no task and
     * goes to [OVERTIME_NO_TASK]; labour saved without worker groups goes to [NO_TASK] (keyed by ""),
     * so the table adds up to the Labour total.
     */
    private fun buildTaskMap(expenses: List<DailyExpense>): Map<String, Map<String, BigDecimal>> {
        val result = LinkedHashMap<String, MutableMap<String, BigDecimal>>()
        fun add(task: String, type: String, amount: BigDecimal) {
            if (amount.signum() != 0) result.getOrPut(task) { LinkedHashMap() }.merge(type, amount, BigDecimal::add)
        }
        expenses.forEach { expense ->
            var wages = BigDecimal.ZERO
            var overtime = BigDecimal.ZERO
            parseWorkerGroups(expense.workerGroups).forEach { group ->
                val groupWages = group.wagePerDay.multiply(BigDecimal(group.count))
                val groupOvertime = group.calculateTotalGroupCost().subtract(groupWages)
                wages = wages.add(groupWages)
                overtime = overtime.add(groupOvertime)
                add(group.taskPerformed.trim().ifEmpty { NO_TASK }, group.workerTypeName.ifBlank { OTHER_LABOUR },
                    groupWages.add(groupOvertime))
            }
            add(NO_TASK, "", expense.totalLaborCost.subtract(wages))
            add(OVERTIME_NO_TASK, "", expense.totalOvertimeCost.subtract(overtime))
        }
        return result
    }

    /**
     * typeName -> (subtypeName -> total). Entries without a subtype are keyed by "".
     * Labour is listed under [LABOUR] by labour type, with overtime as its own line.
     */
    private fun buildTypeMap(expenses: List<DailyExpense>): Map<String, Map<String, BigDecimal>> {
        val result = LinkedHashMap<String, MutableMap<String, BigDecimal>>()
        expenses.forEach { expense ->
            addLabour(expense, result)
            parseOtherExpenses(expense.otherExpenses).forEach { entry ->
                val subtypeName = entry.subtypeName?.takeIf { it.isNotBlank() } ?: ""
                val typeMap = result.getOrPut(entry.typeName) { LinkedHashMap() }
                typeMap.merge(subtypeName, entry.amount, BigDecimal::add)
            }
        }
        return result
    }

    private fun addLabour(expense: DailyExpense, result: MutableMap<String, MutableMap<String, BigDecimal>>) {
        fun add(subtype: String, amount: BigDecimal) {
            if (amount.signum() != 0) result.getOrPut(LABOUR) { LinkedHashMap() }.merge(subtype, amount, BigDecimal::add)
        }
        val groups = parseWorkerGroups(expense.workerGroups)
        var wagesByType = BigDecimal.ZERO
        groups.forEach { group ->
            val wages = group.wagePerDay.multiply(BigDecimal(group.count))
            wagesByType = wagesByType.add(wages)
            add(group.workerTypeName.ifBlank { OTHER_LABOUR }, wages)
        }
        // Labour saved without worker groups (older records) is still counted.
        add(OTHER_LABOUR, expense.totalLaborCost.subtract(wagesByType))
        add(OVERTIME, expense.totalOvertimeCost)
    }

    private fun parseWorkerGroups(json: String): List<WorkerGroupEntry> {
        return try {
            if (json.isBlank() || json == "[]") emptyList()
            else Json.decodeFromString(ListSerializer(WorkerGroupEntry.serializer()), json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseOtherExpenses(json: String): List<OtherExpenseEntry> {
        return try {
            if (json.isBlank() || json == "[]") emptyList()
            else Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun percent(part: BigDecimal, total: BigDecimal): Float {
        if (total == BigDecimal.ZERO) return 0f
        return part.multiply(BigDecimal("100")).divide(total, 2, RoundingMode.HALF_UP).toFloat()
    }

    private fun trend(current: BigDecimal, previous: BigDecimal): TrendDirection {
        return when {
            previous == BigDecimal.ZERO -> TrendDirection.NEUTRAL
            current > previous -> TrendDirection.UP
            current < previous -> TrendDirection.DOWN
            else -> TrendDirection.NEUTRAL
        }
    }

    companion object {
        const val LABOUR = "Labour"
        const val OVERTIME = "Overtime"
        const val OTHER_LABOUR = "Other labour"
        const val NO_TASK = "No task recorded"
        const val OVERTIME_NO_TASK = "Overtime (no task)"
        private const val TASK_KEY_PREFIX = "task:"
    }
}
