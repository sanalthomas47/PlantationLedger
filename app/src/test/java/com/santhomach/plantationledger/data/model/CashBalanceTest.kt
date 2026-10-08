package com.santhomach.plantationledger.data.model

import com.santhomach.plantationledger.data.export.ExportData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class CashBalanceTest {

    private fun day(
        date: String,
        labour: Int = 0,
        settlement: Int = 0,
        advance: Int = 0,
        updatedAt: String = "${date}T20:00:00"
    ) = DailyExpense(
        date = date,
        totalLaborCost = BigDecimal(labour),
        weeklyPaymentDone = BigDecimal(settlement),
        advanceAmount = BigDecimal(advance),
        updatedAt = updatedAt
    )

    private val today = LocalDate.parse("2026-10-07") // a Wednesday

    @Test
    fun weeksRunMondayToSundayWithARunningTotal() {
        val days = listOf(
            // Week of 21 Sep: costs 10,000, settled 10,500 -> excess 500
            day("2026-09-21", labour = 5_000),
            day("2026-09-24", labour = 5_000, settlement = 10_500),
            // Week of 28 Sep: costs 8,000, settled 7,000 -> short 1,000
            day("2026-09-28", labour = 8_000),
            day("2026-10-01", settlement = 7_000),
            // Current week: costs 4,000 so far, nothing paid
            day("2026-10-05", labour = 4_000)
        )
        val weeks = buildWeeklyBalances(days, today)

        assertEquals(listOf("2026-09-21", "2026-09-28", "2026-10-05"), weeks.map { it.weekStart.toString() })
        assertEquals(listOf(500, -1_000, -4_000), weeks.map { it.result.toInt() })
        assertEquals(listOf(500, -500, -4_500), weeks.map { it.runningTotal.toInt() })
        assertEquals(LocalDate.parse("2026-10-01"), weeks[1].settlementDate)
        assertTrue(weeks[2].isCurrentWeek)
        // The current week is not flagged as "no payment" while it is still running.
        assertFalse(weeks[2].noPayment)
        // Brought forward into the current week = every earlier day.
        assertEquals(-500, days.balanceBroughtForward(LocalDate.parse("2026-10-05")).toInt())
    }

    @Test
    fun flagsWeeksWithoutPaymentAndEditsAfterSettlement() {
        val days = listOf(
            day("2026-07-06", labour = 4_600),
            day("2026-07-07", labour = 2_850),
            // Settled Thursday, Saturday entered on time -> not flagged
            day("2026-08-20", labour = 3_000, settlement = 6_000),
            day("2026-08-22", labour = 2_850, updatedAt = "2026-08-22T21:00:00"),
            // Monday edited two weeks later -> flagged
            day("2026-08-17", labour = 1_000, updatedAt = "2026-09-05T10:00:00")
        )
        val weeks = buildWeeklyBalances(days, today)

        assertTrue(weeks[0].noPayment)
        assertEquals(listOf(LocalDate.parse("2026-08-17")), weeks[1].editedAfterSettlement)
    }

    /**
     * Runs the real balance code over an export file, if one is supplied:
     *   gradlew testDebugUnitTest -Dplantation.export=path\to\backup.json
     * Skipped otherwise, so no personal data is needed to run the test suite.
     */
    @Test
    fun printsBalancesForAnExportFile() {
        val path = System.getProperty("plantation.export")
        assumeTrue("no export file supplied", !path.isNullOrBlank() && File(path).exists())

        val export = Json { ignoreUnknownKeys = true }
            .decodeFromString(ExportData.serializer(), File(path!!).readText())
        val asOf = LocalDate.parse(System.getProperty("plantation.today") ?: LocalDate.now().toString())
        val weeks = buildWeeklyBalances(export.dailyExpenses, asOf)
        weeks.filter { it.result.signum() != 0 || it.isCurrentWeek }.forEach {
            println("WEEK ${it.weekStart} costs=${it.costs} paid=${it.payments} result=${it.result} running=${it.runningTotal} flags=${it.noPayment}/${it.editedAfterSettlement}")
        }
        val monday = weekStartOf(asOf)
        val thisWeek = export.dailyExpenses.filter { it.date >= monday.toString() && it.date <= monday.plusDays(6).toString() }
        val expense = thisWeek.fold(BigDecimal.ZERO) { a, e -> a.add(e.balanceExpenses()) }
        val brought = export.dailyExpenses.balanceBroughtForward(monday)
        println("HOME expense=$expense payments=${thisWeek.fold(BigDecimal.ZERO) { a, e -> a.add(e.balancePayments()) }} broughtForward=$brought balance=${thisWeek.balanceTotal().add(brought)}")
    }
}
