package com.santhomach.plantationledger.data.repository

import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.testutil.FakeDatabase
import com.santhomach.plantationledger.testutil.advance
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.income
import com.santhomach.plantationledger.testutil.money
import com.santhomach.plantationledger.testutil.other
import com.santhomach.plantationledger.testutil.otherEntriesOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ExpenseRepositoryTest {

    private val fake = FakeDatabase()
    private val repository = ExpenseRepository(fake.db)

    @Test
    fun summaryAddsUpTheRangeAndReportsVendorBillsSeparately() = runTest {
        fake.expenses.insert(dailyExpense("2024-03-04",
            groups = listOf(group("Male", 2, 600, otHours = 1, otRate = 100)),            // 1,200 + 200 OT
            others = listOf(other("Pesticides", 3_000), other("Fuel", 400)),
            incomes = listOf(income("Cardamom Sales", 10_000)),
            advances = listOf(advance(1_000))))
        fake.expenses.insert(dailyExpense("2024-03-05",
            groups = listOf(group("Female", 1, 500)),
            others = listOf(other("Fertilizers", 2_000)),
            settlement = 4_000))
        fake.expenses.insert(dailyExpense("2024-03-11", groups = listOf(group("Male", 9, 600)))) // outside

        val summary = repository.getDailyExpenseSummaryFlow("2024-03-04", "2024-03-10").first()

        assertEquals(2, summary.totalDays)
        assertMoney(1_700, summary.totalLaborCost)
        assertMoney(200, summary.totalOvertimeCost)
        assertMoney(5_400, summary.totalOtherExpenses)
        assertMoney(5_000, summary.totalVendorPurchases)                 // pesticide + fertilizer only
        assertMoney(1_000, summary.totalAdvanceAmount)
        assertMoney(4_000, summary.totalWeeklyPayment)
        assertMoney(10_000, summary.totalIncome)
        assertMoney(5_000, summary.averageDailyIncome)
        // Day 1: 1,200 + 200 + 3,400 + 1,000 advance = 5,800; day 2: 500 + 2,000 + 4,000 = 6,500
        assertMoney(6_150, summary.averageDailyExpense)
    }

    @Test
    fun anEmptyRangeGivesZeroAveragesInsteadOfDividingByZero() = runTest {
        val summary = repository.getDailyExpenseSummaryFlow("2024-03-04", "2024-03-10").first()
        assertEquals(0, summary.totalDays)
        assertMoney(0, summary.averageDailyIncome)
        assertMoney(0, summary.averageDailyExpense)
    }

    @Test
    fun weeklySummaryCountsAPartWeekAsAWholeWeek() = runTest {
        fake.expenses.insert(dailyExpense("2024-03-04",
            groups = listOf(group("Male", 2, 600)), others = listOf(other("Fuel", 400)),
            incomes = listOf(income("Cardamom Sales", 9_000))))

        val tenDays = repository.getWeeklyExpenseSummaryFlow("2024-03-01", "2024-03-10").first()
        assertEquals(2, tenDays.totalWeeks)
        assertMoney(800, tenDays.averageWeeklyExpense)                   // 1,600 / 2
        assertMoney(4_500, tenDays.averageWeeklyIncome)

        assertEquals(1, repository.getWeeklyExpenseSummaryFlow("2024-03-04", "2024-03-10").first().totalWeeks)
        assertEquals(1, repository.getWeeklyExpenseSummaryFlow("2024-03-04", "2024-03-04").first().totalWeeks)
        assertEquals(1, repository.getWeeklyExpenseSummaryFlow("not a date", "2024-03-04").first().totalWeeks)
    }

    @Test
    fun savingADayStampsWhenItWasLastChanged() = runTest {
        val before = LocalDateTime.now().minusSeconds(1).toString()
        val id = repository.insertDailyExpense(dailyExpense("2024-03-04").copy(updatedAt = "2000-01-01T00:00"))
        val saved = fake.expenses.all().single()
        assertTrue(saved.updatedAt > before)

        repository.updateDailyExpense(saved.copy(id = id.toInt(), comments = "edited", updatedAt = "2000-01-01T00:00"))
        val edited = fake.expenses.all().single()
        assertEquals("edited", edited.comments)
        assertTrue(edited.updatedAt > before)
    }

    @Test
    fun legacyVendorPaymentsMarkTheOldestUnpaidBillsPaidAndAreThenRemoved() = runTest {
        fake.expenses.insert(dailyExpense("2024-01-01", others = listOf(other("Pesticides", 1_000, paid = true))))
        fake.expenses.insert(dailyExpense("2024-01-08", others = listOf(other("Pesticides", 2_000), other("Fuel", 1_500))))
        fake.expenses.insert(dailyExpense("2024-01-15", others = listOf(other("Fertilizers", 3_000))))
        fake.expenses.insert(dailyExpense("2024-01-22", others = listOf(other("Pesticides", 500))))
        fake.vendorPayments.insert(VendorPayment(date = "2024-01-10", amount = money(3_000)))
        fake.vendorPayments.insert(VendorPayment(date = "2024-01-20", amount = money(2_000)))
        val linked = VendorPayment(date = "2024-01-02", amount = money(1_000), sourceExpenseDate = "2024-01-01",
            sourceTypeName = "Pesticides")
        fake.vendorPayments.insert(linked)

        repository.migrateVendorLegacyPayments()

        // 5,000 legacy - 1,000 already marked = 4,000 to apply: 2,000 then 3,000 (oldest first).
        val byDate = fake.expenses.all().associate { it.date to otherEntriesOf(it) }
        assertEquals(listOf(true, false), byDate.getValue("2024-01-08").map { it.isPaid })   // fuel untouched
        assertTrue(byDate.getValue("2024-01-15").single().isPaid)
        assertEquals(false, byDate.getValue("2024-01-22").single().isPaid)
        assertEquals(listOf("2024-01-01"), fake.vendorPayments.all().map { it.sourceExpenseDate })
    }

    @Test
    fun migrationLeavesEverythingAloneWhenThereAreNoLegacyPayments() = runTest {
        fake.expenses.insert(dailyExpense("2024-01-08", others = listOf(other("Pesticides", 2_000))))
        val before = fake.expenses.all()

        repository.migrateVendorLegacyPayments()

        assertEquals(before, fake.expenses.all())
    }

    @Test
    fun defaultDataIsAddedOnceAndExistingEntriesAreKept() = runTest {
        fake.expenseTypes.insert(ExpenseType(typeName = "Fuel", description = "my own description"))
        fake.expenseTypes.insert(ExpenseType(typeName = "Labour Camp"))

        repository.initializeDefaultData()
        repository.initializeDefaultData()

        val types = fake.expenseTypes.all()
        assertEquals(13, types.size)                                    // 12 defaults + 1 of the user's
        assertEquals(types.size, types.map { it.typeName }.toSet().size)
        assertEquals("my own description", types.single { it.typeName == "Fuel" }.description)
        assertTrue(types.any { it.typeName == "Foliar" })               // tracked in the Vendor Ledger
        assertEquals(2, fake.expenseSubtypes.all().size)
        assertEquals(3, fake.incomeTypes.all().size)
        assertEquals(4, fake.workerTypes.all().size)
        assertEquals(7, fake.workTasks.all().size)
        assertNotEquals(0, fake.expenseTypes.all().first().id)
    }
}
