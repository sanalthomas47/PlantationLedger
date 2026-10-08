package com.santhomach.plantationledger.data.model

import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/*
 * The cash balance between the owner and the estate manager, worked out the same way on every
 * screen (Home, Daily Expense, Reports, Weekly Balance):
 *
 *   day result = payments (advances + weekly settlement + excess balance)
 *              - balance expenses (labour + overtime + other expenses, excluding vendor bills)
 *
 * A positive result means more was paid than spent (excess); negative means short.
 * The balance brought forward into any day is the sum of every earlier day's result, so an
 * unsettled week keeps showing until it is actually covered. Weeks run Monday to Sunday.
 */

/** Money handed over on this day that counts towards the cash balance. */
fun DailyExpense.balancePayments(): BigDecimal =
    advanceAmount.add(weeklyPaymentDone).add(excessBalance)

/** Payments minus expenses for this day (vendor bills excluded). Positive = excess. */
fun DailyExpense.balanceResult(): BigDecimal =
    balancePayments().subtract(balanceExpenses())

/** Sum of [balanceResult] over all given days. */
fun Iterable<DailyExpense>.balanceTotal(): BigDecimal =
    fold(BigDecimal.ZERO) { acc, e -> acc.add(e.balanceResult()) }

/** Balance brought forward into [date]: every day strictly before it. */
fun Iterable<DailyExpense>.balanceBroughtForward(date: LocalDate): BigDecimal {
    val cutoff = date.toString()
    return filter { it.date < cutoff }.balanceTotal()
}

/** Monday of the week containing [date]. */
fun weekStartOf(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** One row of the Weekly Balance list. */
data class WeekBalance(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val days: Int,
    val costs: BigDecimal,
    val payments: BigDecimal,
    /** payments - costs for this week. Positive = excess, negative = short. */
    val result: BigDecimal,
    /** Balance after this week, including every earlier week. */
    val runningTotal: BigDecimal,
    /** Latest day in the week with a weekly settlement, if any. */
    val settlementDate: LocalDate?,
    val isCurrentWeek: Boolean,
    /** Costs were recorded but nothing was paid (and the week is over). */
    val noPayment: Boolean,
    /** Days changed well after the week was settled, a common cause of a mismatch. */
    val editedAfterSettlement: List<LocalDate>
)

/**
 * Groups daily records into Monday-Sunday weeks, oldest first, with a running balance.
 * An edit counts as "after settlement" when it happened more than one day after both the
 * settlement date and the day's own date (so normal late-evening entry is not flagged).
 */
fun buildWeeklyBalances(expenses: List<DailyExpense>, today: LocalDate = LocalDate.now()): List<WeekBalance> {
    val currentWeek = weekStartOf(today)
    val byWeek = expenses
        .mapNotNull { e -> runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e } }
        .groupBy({ weekStartOf(it.first) }, { it })
        .toSortedMap()

    var running = BigDecimal.ZERO
    return byWeek.map { (start, days) ->
        val records = days.map { it.second }
        val costs = records.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.balanceExpenses()) }
        val payments = records.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.balancePayments()) }
        val result = payments.subtract(costs)
        running = running.add(result)

        val settlementDate = days
            .filter { it.second.weeklyPaymentDone.signum() > 0 }
            .maxOfOrNull { it.first }

        val editedAfter = if (settlementDate == null) emptyList() else days.mapNotNull { (date, e) ->
            val edited = runCatching { LocalDateTime.parse(e.updatedAt).toLocalDate() }.getOrNull()
            val threshold = maxOf(date, settlementDate).plusDays(1)
            if (edited != null && edited.isAfter(threshold)) date else null
        }.sorted()

        val isCurrent = start == currentWeek
        WeekBalance(
            weekStart = start,
            weekEnd = start.plusDays(6),
            days = records.size,
            costs = costs,
            payments = payments,
            result = result,
            runningTotal = running,
            settlementDate = settlementDate,
            isCurrentWeek = isCurrent,
            noPayment = !isCurrent && costs.signum() > 0 && payments.signum() == 0,
            editedAfterSettlement = editedAfter
        )
    }
}
