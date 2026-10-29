package com.santhomach.plantationledger.data.model

import com.santhomach.plantationledger.data.export.ExportData
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.income
import com.santhomach.plantationledger.testutil.money
import com.santhomach.plantationledger.testutil.other
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class IncomeAnalysisTest {

    private val today = LocalDate.of(2026, 10, 22)                   // a Thursday; week = 19-25 Oct

    // ---- Income Breakdown ----

    @Test
    fun incomeIsGroupedByTypeWithWeightsAndEverySale() {
        val days = listOf(
            dailyExpense("2026-10-20", incomes = listOf(income("Cardamom Sales", 230_000, weight = 100.0))),
            dailyExpense("2026-03-02", incomes = listOf(income("Cardamom Sales", 210_000, weight = 100.0),
                income("Pepper Sales", 70_000, weight = 100.0))),
            dailyExpense("2025-11-15", incomes = listOf(income("Cardamom Sales", 180_000, weight = 80.0),
                income("Timber", 1_000)))
        )

        val types = buildIncomeBreakdown(days, today)

        assertEquals(listOf("Cardamom Sales", "Pepper Sales", "Timber"), types.map { it.typeName })
        val cardamom = types.first()
        assertMoney(230_000, cardamom.thisWeek.amount)
        assertMoney(440_000, cardamom.thisYear.amount)
        assertMoney(620_000, cardamom.allTime.amount)
        assertEquals(280.0, cardamom.allTime.weight, 0.0)
        assertMoney(2_200, cardamom.thisYear.pricePerKg)                 // 4,40,000 / 200 kg
        assertEquals(listOf("2026-10-20", "2026-03-02", "2025-11-15"), cardamom.sales.map { it.date })
        assertNull(types.last().allTime.pricePerKg)                      // no weight recorded
        assertMoney(0, types[1].thisWeek.amount)
    }

    @Test
    fun thisWeekRunsMondayToSunday() {
        val days = listOf(
            dailyExpense("2026-10-18", incomes = listOf(income("Pepper Sales", 1))),        // Sunday before
            dailyExpense("2026-10-19", incomes = listOf(income("Pepper Sales", 10))),       // Monday
            dailyExpense("2026-10-25", incomes = listOf(income("Pepper Sales", 100))),      // Sunday
            dailyExpense("2026-10-26", incomes = listOf(income("Pepper Sales", 1_000)))     // next Monday
        )

        val pepper = buildIncomeBreakdown(days, today).single()

        assertMoney(110, pepper.thisWeek.amount)
        assertMoney(1_111, pepper.allTime.amount)
    }

    @Test
    fun typeNamesAreTrimmedAndUnreadableIncomeIsSkipped() {
        val days = listOf(
            dailyExpense("2026-10-20", incomes = listOf(income("Pepper Sales ", 300), income("Banana", 300))),
            dailyExpense("2026-10-21", incomes = listOf(income(" Pepper Sales", 200))),
            dailyExpense("2026-10-21").copy(incomeEntries = "not json"),
            dailyExpense("2026-10-21").copy(incomeEntries = "")
        )

        val types = buildIncomeBreakdown(days, today)

        assertEquals(listOf("Pepper Sales", "Banana"), types.map { it.typeName })
        assertMoney(500, types.first().allTime.amount)
        assertEquals(2, types.first().sales.size)
    }

    @Test
    fun equalIncomeIsListedByName() {
        val days = listOf(dailyExpense("2026-10-20", incomes = listOf(income("Timber", 500), income("Arecanut", 500))))

        assertEquals(listOf("Arecanut", "Timber"), buildIncomeBreakdown(days, today).map { it.typeName })
    }

    // ---- Income chart ----

    private val chartDays = listOf(
        dailyExpense("2026-09-01", incomes = listOf(income("Cardamom Sales", 60_000, weight = 30.0),
            income("Pepper Sales", 30_000), income("Banana", 10_000))),
        dailyExpense("2024-01-10", incomes = listOf(income("Cardamom Sales", 100_000), income("Timber", 300_000)))
    )

    @Test
    fun chartBarsRunHighestToLowestWithTheirShare() {
        val bars = incomeChartBars(buildIncomeBreakdown(chartDays, today), allTime = false)

        assertEquals(listOf("Cardamom Sales", "Pepper Sales", "Banana"), bars.map { it.typeName })   // no Timber this year
        assertEquals(listOf("60.0", "30.0", "10.0"), bars.map { it.share.toPlainString() })
        assertEquals(listOf(1f, 0.5f, 0.1667f), bars.map { it.fraction })            // relative to the top bar
        assertEquals(30.0, bars.first().period.weight, 0.0)
        assertMoney(2_000, bars.first().period.pricePerKg)
    }

    @Test
    fun chartAllTimeReordersTheBars() {
        val bars = incomeChartBars(buildIncomeBreakdown(chartDays, today), allTime = true)

        assertEquals(listOf("Timber", "Cardamom Sales", "Pepper Sales", "Banana"), bars.map { it.typeName })
        assertMoney(160_000, bars[1].period.amount)
        assertEquals("100.0", bars.fold(BigDecimal.ZERO) { acc, bar -> acc.add(bar.share) }.toPlainString())
    }

    @Test
    fun chartIsEmptyWhenThePeriodHasNoIncome() {
        val old = listOf(dailyExpense("2024-01-10", incomes = listOf(income("Timber", 300_000))))

        assertTrue(incomeChartBars(buildIncomeBreakdown(old, today), allTime = false).isEmpty())
        assertTrue(incomeChartBars(emptyList(), allTime = true).isEmpty())
    }

    // ---- Income vs Expense ----

    @Test
    fun capitalIsRecognisedInAnyCaseAndBadLinesAreSkipped() {
        val days = listOf(
            dailyExpense("2026-04-01",
                others = listOf(other("capital expenses", 5_000), other("CAPITAL", 1_000), other("Fuel", 700)),
                incomes = listOf(income("Cardamom Sales", 400))),
            dailyExpense("2026-04-02").copy(otherExpenses = "{broken", workerGroups = "[oops")
        )

        val report = buildEfficiency(days)
        val cardamom = report.crops.single()

        assertMoney(6_000, report.excludedCapital)
        assertMoney(700, cardamom.expense)
        assertMoney(-300, cardamom.net)
        assertMoney(BigDecimal("0.57"), cardamom.returnPerRupee)
    }

    private val estate = listOf(
        dailyExpense("2026-02-10",
            groups = listOf(
                group("Bengali Male", 2, 500, task = "Pepper Drying", otHours = 1, otRate = 100),   // 1,000 + 200 OT
                group("Bengali Female", 3, 450, task = "Weeding")                                 // 1,350
            ),
            others = listOf(
                other("Coconut Oil related", 6_000),
                other("Transportation", 500, subtype = "Cardamom Harvesting"),
                other("Transportation", 900, subtype = "Other Transport"),
                other("Pesticides", 4_000, subtype = "Thrips"),
                other("Capital Expenses", 10_000, subtype = "Shade Net")
            ),
            incomes = listOf(income("Pepper Sales", 50_000), income("Pepper Cheeru", 500),
                income("Coconut Raw", 9_000), income("Other Income", 300))
        ).let { it.copy(totalOvertimeCost = it.totalOvertimeCost.add(money(150))) },             // + 150 day-level OT
        dailyExpense("2025-12-31",
            groups = listOf(group("Malayali Male", 1, 800, task = "Coconut Related")),
            incomes = listOf(income("Cardamom Sales", 100_000)))
    )

    @Test
    fun expensesGoToTheCropTheyNameAndTheRestToCardamom() {
        val report = buildEfficiency(estate)
        val crops = report.crops.associateBy { it.crop }

        val pepper = crops.getValue("Pepper")
        assertEquals(listOf("Pepper Cheeru", "Pepper Sales"), pepper.incomeTypes)
        assertMoney(50_500, pepper.income)
        assertMoney(1_200, pepper.expense)                               // drying wages + its overtime
        assertMoney(BigDecimal("42.08"), pepper.returnPerRupee)

        val coconut = crops.getValue("Coconut")
        assertMoney(9_000, coconut.income)
        assertMoney(6_800, coconut.expense)                              // type name + labour task
        assertTrue(coconut.sources.all { it.matched })

        val cardamom = crops.getValue("Cardamom")
        assertMoney(100_000, cardamom.income)
        // Weeding 1,350 + day overtime 150 + harvest transport 500 + other transport 900 + pesticide 4,000
        assertMoney(6_900, cardamom.expense)
        assertEquals(listOf("Transportation / Cardamom Harvesting"), cardamom.sources.filter { it.matched }.map { it.label })
        assertTrue(cardamom.sources.any { it.label == "Pesticides / Thrips" && !it.matched })
        assertTrue(cardamom.sources.any { it.label == "Overtime (no task)" && it.isLabour })

        // "Other Income" is its own row and does not claim "Other Transport".
        val other = crops.getValue("Other Income")
        assertMoney(300, other.income)
        assertMoney(0, other.expense)
        assertNull(other.returnPerRupee)

        assertMoney(10_000, report.excludedCapital)
        // Every expense lands in exactly one crop, apart from capital.
        val allSpending = estate.sumOf { it.totalLaborCost.add(it.totalOvertimeCost).add(it.totalOtherExpensesCost) }
        assertMoney(allSpending.subtract(report.excludedCapital), report.crops.sumOf { it.expense })
    }

    @Test
    fun aPeriodOnlyCountsItsOwnDaysButKeepsEveryCrop() {
        val report = buildEfficiency(estate, from = LocalDate.of(2026, 1, 1), to = today)
        val crops = report.crops.associateBy { it.crop }

        assertFalse("Coconut task was in 2025", crops.getValue("Coconut").sources.any { it.isLabour })
        assertMoney(6_000, crops.getValue("Coconut").expense)
        assertMoney(0, crops.getValue("Cardamom").income)                // the 2025 sale is outside the year
        assertTrue(crops.getValue("Cardamom").expense.signum() > 0)      // ...but its costs still show
    }

    @Test
    fun aCropWithNoSalesYetStillClaimsItsExpenses() {
        val days = listOf(dailyExpense("2026-05-05", groups = listOf(group("Bengali Male", 1, 500, task = "Cocoa pruning")),
            others = listOf(other("Coconut Oil related", 700))))

        val crops = buildEfficiency(days, knownIncomeTypes = listOf("Cocoa", "Coconut Oil")).crops.associateBy { it.crop }

        assertMoney(500, crops.getValue("Cocoa").expense)               // "Cocoa" is not mistaken for "Coconut"
        assertMoney(700, crops.getValue("Coconut").expense)
        assertMoney(0, crops.getValue("Cocoa").income)
    }

    @Test
    fun incomeTypesBelongToTheirFirstWord() {
        assertEquals("Pepper", cropOf("Pepper Sales"))
        assertEquals("Pepper", cropOf("Pepper Cheeru"))
        assertEquals("Nutmeg", cropOf("Nutmeg flower (Jaathipathri)"))
        assertEquals("Banana", cropOf("Banana sales"))
        assertEquals("Cloves", cropOf("Cloves (grampoo)"))
        assertEquals("Other Income", cropOf("Other Income"))
    }

    /** Opt-in: run with -Dplantation.export=<file> to print the report for a real backup. */
    @Test
    fun printsEfficiencyForAnExportFile() {
        val path = System.getProperty("plantation.export")
        assumeTrue("no export file supplied", !path.isNullOrBlank() && File(path).exists())
        val export = Json { ignoreUnknownKeys = true }.decodeFromString(ExportData.serializer(), File(path!!).readText())
        val report = buildEfficiency(export.dailyExpenses, export.incomeTypes.map { it.typeName })
        report.crops.forEach {
            println("CROP ${it.crop} income=${it.income} expense=${it.expense} net=${it.net} perRupee=${it.returnPerRupee} types=${it.incomeTypes}")
        }
        println("CAPITAL ${report.excludedCapital}")
        buildIncomeBreakdown(export.dailyExpenses).forEach {
            println("INCOME ${it.typeName} all=${it.allTime.amount} kg=${it.allTime.weight} perKg=${it.allTime.pricePerKg}")
        }
    }
}
