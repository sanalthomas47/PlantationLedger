package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeeklyFundsUiState(
    val error: String? = null
)

/**
 * Totals of a week used to compare the money received against the money spent.
 */
data class WeekComparison(
    val totalExpenses: BigDecimal = BigDecimal.ZERO,
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalPayments: BigDecimal = BigDecimal.ZERO
)

@HiltViewModel
class WeeklyFundsViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeeklyFundsUiState())
    val uiState: StateFlow<WeeklyFundsUiState> = _uiState.asStateFlow()

    val weeklyFunds: StateFlow<List<WeeklyFunds>> = repository.getAllWeeklyFundsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Expenses / income / payments for the working week (Monday to Saturday) starting at [startDate].
     */
    fun getComparisonFlow(startDate: LocalDate): Flow<WeekComparison> {
        val endDate = startDate.plusDays(5)
        return repository.getDailyExpenseSummaryFlow(
            startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ).map { summary ->
            WeekComparison(
                totalExpenses = summary.totalLaborCost
                    .add(summary.totalOvertimeCost)
                    .add(summary.totalOtherExpenses),
                totalIncome = summary.totalIncome,
                totalPayments = summary.totalAdvanceAmount.add(summary.totalWeeklyPayment)
            )
        }
    }

    fun addFunds(amount: BigDecimal, startDate: LocalDate, notes: String) {
        viewModelScope.launch {
            try {
                val monday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val funds = WeeklyFunds(
                    weekStartDate = monday.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    amountReceived = amount,
                    notes = notes
                )
                repository.insertWeeklyFunds(funds)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun addFunds(amount: BigDecimal, paymentMade: BigDecimal, startDate: LocalDate, notes: String) {
        viewModelScope.launch {
            try {
                val monday = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val funds = WeeklyFunds(
                    weekStartDate = monday.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    amountReceived = amount,
                    paymentMade = paymentMade,
                    notes = notes
                )
                repository.insertWeeklyFunds(funds)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updateFunds(id: Int, amount: BigDecimal, paymentMade: BigDecimal, notes: String) {
        viewModelScope.launch {
            try {
                val existingFunds = weeklyFunds.value.find { it.id == id }
                existingFunds?.let {
                    val updatedFunds = it.copy(
                        amountReceived = amount,
                        paymentMade = paymentMade,
                        notes = notes
                    )
                    repository.updateWeeklyFunds(updatedFunds)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun updatePaymentMade(id: Int, paymentMade: BigDecimal) {
        viewModelScope.launch {
            try {
                val existingFunds = weeklyFunds.value.find { it.id == id }
                existingFunds?.let {
                    val updatedFunds = it.copy(paymentMade = paymentMade)
                    repository.updateWeeklyFunds(updatedFunds)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteFunds(funds: WeeklyFunds) {
        viewModelScope.launch {
            try {
                repository.deleteWeeklyFunds(funds)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Payment made for the week minus the expenses of that week (Monday to Sunday).
     */
    fun getExcessBalanceFlow(startDate: LocalDate): Flow<BigDecimal> {
        val endDate = startDate.plusDays(6)
        return repository.getDailyExpenseSummaryFlow(
            startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ).map { summary ->
            val totalExpenses = summary.totalLaborCost
                .add(summary.totalOvertimeCost)
                .add(summary.totalOtherExpenses)
            val funds = weeklyFunds.value.find { LocalDate.parse(it.weekStartDate) == startDate }
            val paymentMade = funds?.paymentMade ?: BigDecimal.ZERO
            paymentMade.subtract(totalExpenses)
        }
    }
}
