package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.EfficiencyReport
import com.santhomach.plantationledger.data.model.IncomeTypeSummary
import com.santhomach.plantationledger.data.model.buildEfficiency
import com.santhomach.plantationledger.data.model.buildIncomeBreakdown
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class IncomeBreakdownUiState(
    val types: List<IncomeTypeSummary> = emptyList(),
    val thisWeekTotal: BigDecimal = BigDecimal.ZERO,
    val thisYearTotal: BigDecimal = BigDecimal.ZERO,
    val allTimeTotal: BigDecimal = BigDecimal.ZERO,
    val expanded: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** Income Breakdown: income by type for this week, this year and all time, with every sale. */
@HiltViewModel
class IncomeBreakdownViewModel @Inject constructor(
    repository: ExpenseRepository
) : ViewModel() {

    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<IncomeBreakdownUiState> =
        combine(repository.getDailyExpensesByDateRangeFlow(ALL_FROM, ALL_TO), expanded) { days, open ->
            val types = buildIncomeBreakdown(days)
            IncomeBreakdownUiState(
                types = types,
                thisWeekTotal = types.fold(BigDecimal.ZERO) { a, t -> a.add(t.thisWeek.amount) },
                thisYearTotal = types.fold(BigDecimal.ZERO) { a, t -> a.add(t.thisYear.amount) },
                allTimeTotal = types.fold(BigDecimal.ZERO) { a, t -> a.add(t.allTime.amount) },
                expanded = open,
                isLoading = false
            )
        }
            .catch { e -> emit(IncomeBreakdownUiState(isLoading = false, error = e.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IncomeBreakdownUiState())

    fun toggle(typeName: String) = expanded.update { if (typeName in it) it - typeName else it + typeName }
}

data class EfficiencyUiState(
    val allTime: Boolean = false,
    val report: EfficiencyReport = EfficiencyReport(emptyList(), BigDecimal.ZERO),
    val expanded: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/** Income vs Expense: each crop's income against the expenses that belong to it. */
@HiltViewModel
class EfficiencyViewModel @Inject constructor(
    repository: ExpenseRepository
) : ViewModel() {

    private val allTime = MutableStateFlow(false)
    private val expanded = MutableStateFlow<Set<String>>(emptySet())

    private val incomeTypeNames = repository.getAllActiveIncomeTypesFlow().map { list -> list.map { it.typeName } }

    val uiState: StateFlow<EfficiencyUiState> =
        combine(
            repository.getDailyExpensesByDateRangeFlow(ALL_FROM, ALL_TO),
            incomeTypeNames,
            allTime,
            expanded
        ) { days, incomeTypes, wholeHistory, open ->
            val today = LocalDate.now()
            EfficiencyUiState(
                allTime = wholeHistory,
                report = buildEfficiency(
                    expenses = days,
                    knownIncomeTypes = incomeTypes,
                    from = if (wholeHistory) null else today.withDayOfYear(1),
                    to = if (wholeHistory) null else today
                ),
                expanded = open,
                isLoading = false
            )
        }
            .catch { e -> emit(EfficiencyUiState(isLoading = false, error = e.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EfficiencyUiState())

    fun setAllTime(value: Boolean) = allTime.update { value }

    fun toggle(crop: String) = expanded.update { if (crop in it) it - crop else it + crop }
}

private const val ALL_FROM = "1900-01-01"
private const val ALL_TO = "2100-12-31"
