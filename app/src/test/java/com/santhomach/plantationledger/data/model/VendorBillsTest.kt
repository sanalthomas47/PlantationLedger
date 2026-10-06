package com.santhomach.plantationledger.data.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class VendorBillsTest {

    private fun entry(type: String, amount: Int, paid: Boolean = false, subtype: String? = null) =
        OtherExpenseEntry(
            expenseTypeId = 1,
            typeName = type,
            subtypeName = subtype,
            amount = BigDecimal(amount),
            isPaid = paid
        )

    private fun day(labour: Int, entries: List<OtherExpenseEntry>, settlement: Int = 0, advance: Int = 0) =
        DailyExpense(
            date = "2026-10-05",
            totalLaborCost = BigDecimal(labour),
            otherExpenses = Json.encodeToString(ListSerializer(OtherExpenseEntry.serializer()), entries),
            totalOtherExpensesCost = entries.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amount) },
            weeklyPaymentDone = BigDecimal(settlement),
            advanceAmount = BigDecimal(advance)
        )

    @Test
    fun recognisesPesticideAndFertilizerCategories() {
        assertTrue(entry("Pesticides", 1).isVendorPurchase())
        assertTrue(entry("Fertilizers", 1).isVendorPurchase())
        assertTrue(entry("organic fertilizer", 1).isVendorPurchase())
        assertFalse(entry("Fuel", 1, subtype = "Diesel").isVendorPurchase())
    }

    @Test
    fun vendorBillsAreLeftOutOfTheBalanceWhetherPaidOrNot() {
        // Labour 30,000 + diesel 2,000 + fertilizer 12,000 (paid) + pesticide 5,000 (unpaid)
        val expense = day(
            labour = 30_000,
            entries = listOf(
                entry("Fuel", 2_000),
                entry("Fertilizers", 12_000, paid = true),
                entry("Pesticides", 5_000, paid = false)
            ),
            settlement = 32_000
        )

        assertEquals(BigDecimal(17_000), expense.vendorPurchaseTotal())
        // Real cost still includes the vendor bills...
        assertEquals(BigDecimal(49_000), expense.totalLaborCost.add(expense.totalOtherExpensesCost))
        // ...but the balance only counts labour + diesel.
        assertEquals(BigDecimal(32_000), expense.balanceExpenses())

        // Settlement 32,000 against 32,000 of balance expenses: neither excess nor short.
        val balance = expense.weeklyPaymentDone.add(expense.advanceAmount).subtract(expense.balanceExpenses())
        assertEquals(0, balance.signum())
    }

    @Test
    fun malformedExpenseJsonCountsAsNoVendorBills() {
        val broken = DailyExpense(
            date = "2026-10-05",
            totalLaborCost = BigDecimal(1_000),
            otherExpenses = "not json",
            totalOtherExpensesCost = BigDecimal(500)
        )
        assertEquals(BigDecimal.ZERO, broken.vendorPurchaseTotal())
        assertEquals(BigDecimal(1_500), broken.balanceExpenses())
    }
}
