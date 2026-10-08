package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.WeekBalance
import com.santhomach.plantationledger.data.model.buildWeeklyBalances
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class WeeklyBalanceUiState(
    /** Newest week first. */
    val weeks: List<WeekBalance> = emptyList(),
    /** Balance after every recorded day, including the current week. */
    val currentBalance: BigDecimal = BigDecimal.ZERO,
    /** Balance carried into the current week. */
    val broughtForward: BigDecimal = BigDecimal.ZERO,
    /** Completed weeks whose payments do not match their costs. */
    val unbalancedCount: Int = 0,
    val onlyUnbalanced: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * Week-by-week cash balance (payments vs costs, vendor bills excluded) with a running total,
 * so the owner can find the week that causes an unexpected excess or shortfall.
 */
@HiltViewModel
class WeeklyBalanceViewModel @Inject constructor(
    repository: ExpenseRepository
) : ViewModel() {

    private val onlyUnbalanced = MutableStateFlow(false)

    private val allWeeks = repository.getDailyExpensesByDateRangeFlow("1900-01-01", "2100-12-31")
        .map { buildWeeklyBalances(it, LocalDate.now()) }

    val uiState: StateFlow<WeeklyBalanceUiState> = combine(allWeeks, onlyUnbalanced.asStateFlow()) { weeks, filter ->
        val current = weeks.lastOrNull { it.isCurrentWeek }
        val broughtForward = if (current != null) current.runningTotal.subtract(current.result)
        else weeks.lastOrNull()?.runningTotal ?: BigDecimal.ZERO
        WeeklyBalanceUiState(
            weeks = weeks
                .filter { !filter || (!it.isCurrentWeek && it.result.signum() != 0) }
                .reversed(),
            currentBalance = weeks.lastOrNull()?.runningTotal ?: BigDecimal.ZERO,
            broughtForward = broughtForward,
            unbalancedCount = weeks.count { !it.isCurrentWeek && it.result.signum() != 0 },
            onlyUnbalanced = filter,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyBalanceUiState())

    fun setOnlyUnbalanced(value: Boolean) {
        onlyUnbalanced.value = value
    }
}
