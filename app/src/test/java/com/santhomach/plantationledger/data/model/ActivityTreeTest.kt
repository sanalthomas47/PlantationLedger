package com.santhomach.plantationledger.data.model

import com.santhomach.plantationledger.testutil.advance
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.income
import com.santhomach.plantationledger.testutil.other
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The Home "Recent Activity" tree: years and months, newest first, with their totals. */
class ActivityTreeTest {

    private val days = listOf(
        dailyExpense("2026-01-05", id = 2, groups = listOf(group("M", 1, 2_000)), settlement = 1_500),
        dailyExpense("2025-12-30", id = 1, groups = listOf(group("M", 1, 1_000)),
            others = listOf(other("Pesticides", 500)), settlement = 1_200),
        dailyExpense("2026-02-02", id = 4, groups = listOf(group("M", 1, 300))),
        dailyExpense("2026-01-20", id = 3, groups = listOf(group("M", 1, 500)),
            incomes = listOf(income("Cardamom Sales", 5_000)), advances = listOf(advance(600))),
        dailyExpense("not a date", id = 9, groups = listOf(group("M", 1, 99_999)))
    )

    @Test
    fun groupsByYearAndMonthNewestFirst() {
        val tree = buildActivityTree(days)

        assertEquals(listOf(2026, 2025), tree.map { it.year })
        assertEquals(listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 1)), tree[0].months.map { it.month })
        assertEquals(listOf("2026-01-20", "2026-01-05"), tree[0].months[1].days.map { it.date })
        assertTrue("unreadable dates are left out", tree.flatMap { it.months }.flatMap { it.days }.none { it.id == 9 })
    }

    @Test
    fun monthAndYearTotalsAddUpTheirDays() {
        val (y2026, y2025) = buildActivityTree(days)
        val jan = y2026.months[1]

        assertMoney(5_000, jan.totals.income)
        assertMoney(2_500, jan.totals.expenses)
        assertMoney(2_100, jan.totals.payments)              // 1,500 settlement + 600 advance

        assertMoney(5_000, y2026.totals.income)
        assertMoney(2_800, y2026.totals.expenses)
        assertMoney(2_100, y2026.totals.payments)

        assertMoney(1_500, y2025.totals.expenses)            // pesticide bills are real spending
        assertMoney(1_200, y2025.totals.payments)
    }

    @Test
    fun noDaysMeansAnEmptyTree() {
        assertTrue(buildActivityTree(emptyList()).isEmpty())
    }
}
