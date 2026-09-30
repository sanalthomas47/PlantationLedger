package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.OtherExpenseEntry
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

data class ExpenseTypeSummaryUiState(
    val rows: List<ExpenseTypeRow> = emptyList(),
    val thisWeekTotal: BigDecimal = BigDecimal.ZERO,
    val thisYearTotal: BigDecimal = BigDecimal.ZERO,
    val allTimeTotal: BigDecimal = BigDecimal.ZERO,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Breaks "other expenses" down by expense type and subtype for this week, this year and all time.
 */
@HiltViewModel
class ExpenseTypeSummaryViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _expandedTypes = MutableStateFlow<Set<String>>(emptySet())
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    private val allExpenses = repository.getDailyExpensesByDateRangeFlow("1900-01-01", "2100-12-31")

    val uiState: StateFlow<ExpenseTypeSummaryUiState> =
        combine(allExpenses, _expandedTypes) { expenses, expanded -> buildUiState(expenses, expanded) }
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

    private fun thisWeekStart() = LocalDate.now().with(DayOfWeek.MONDAY).format(iso)
    private fun thisWeekEnd() = LocalDate.now().with(DayOfWeek.SATURDAY).format(iso)
    private fun prevWeekStart() = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(1).format(iso)
    private fun prevWeekEnd() = LocalDate.now().with(DayOfWeek.SATURDAY).minusWeeks(1).format(iso)
    private fun thisYearStart() = LocalDate.now().withDayOfYear(1).format(iso)
    private fun thisYearEnd() = LocalDate.now().format(iso)
    private fun prevYearStart() = LocalDate.now().minusYears(1).withDayOfYear(1).format(iso)
    private fun prevYearEnd() = LocalDate.now().minusYears(1).with(TemporalAdjusters.lastDayOfYear()).format(iso)

    private fun buildUiState(expenses: List<DailyExpense>, expandedTypes: Set<String>): ExpenseTypeSummaryUiState {
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

        val rows = allMap.keys.sorted().map { typeName ->
            val subtypeNames = (allMap[typeName]?.keys ?: emptySet()).sorted()

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

        return ExpenseTypeSummaryUiState(
            rows = rows,
            thisWeekTotal = weekTotal,
            thisYearTotal = yearTotal,
            allTimeTotal = allTotal,
            isLoading = false,
            error = null
        )
    }

    /** typeName -> (subtypeName -> total). Entries without a subtype are keyed by "". */
    private fun buildTypeMap(expenses: List<DailyExpense>): Map<String, Map<String, BigDecimal>> {
        val result = LinkedHashMap<String, MutableMap<String, BigDecimal>>()
        expenses.forEach { expense ->
            parseOtherExpenses(expense.otherExpenses).forEach { entry ->
                val subtypeName = entry.subtypeName?.takeIf { it.isNotBlank() } ?: ""
                val typeMap = result.getOrPut(entry.typeName) { LinkedHashMap() }
                typeMap.merge(subtypeName, entry.amount, BigDecimal::add)
            }
        }
        return result
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
}
