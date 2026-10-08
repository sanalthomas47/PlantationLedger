package com.santhomach.plantationledger.data

import com.santhomach.plantationledger.data.export.ExportData
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.serialization.BigDecimalSerializer
import com.santhomach.plantationledger.testutil.advance
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.income
import com.santhomach.plantationledger.testutil.money
import com.santhomach.plantationledger.testutil.other
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Money formulas on the models, and the formats data is stored and exported in. */
class ModelAndSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- Calculations on the models ------------------------------------------------------------

    @Test
    fun workerGroupCostIsWagesPlusOvertimeForEveryWorker() {
        // 5 workers x 500 + 5 workers x 2 hrs x 75/hr
        assertMoney(3_250, group("Malayali Male", 5, 500, otHours = 2, otRate = 75).calculateTotalGroupCost())
        assertMoney(0, group("Malayali Male", 0, 500, otHours = 2, otRate = 75).calculateTotalGroupCost())
    }

    @Test
    fun dailyNetAmountIsIncomeMinusAllCostsAndPayments() {
        val day = dailyExpense(
            date = "2026-10-01",
            groups = listOf(group("Bengali Male", 4, 500, otHours = 1, otRate = 100)), // 2,000 + 400
            others = listOf(other("Fuel", 600)),
            incomes = listOf(income("Cardamom Sales", 10_000)),
            advances = listOf(advance(1_000)),
            settlement = 2_500
        )
        // 10,000 - (2,000 + 400 + 600 + 1,000 + 2,500)
        assertMoney(3_500, day.calculateNetAmount())
    }

    // ---- Storage formats ----------------------------------------------------------------------

    @Test
    fun moneyIsStoredAsExactTextWithoutLosingPaise() {
        val converters = RoomConverters()
        assertEquals("1234.50", converters.fromBigDecimal(BigDecimal("1234.50")))
        assertEquals(BigDecimal("1234.50"), converters.toBigDecimal("1234.50"))
        // A corrupt value must not crash the app; it reads as zero.
        assertEquals(BigDecimal.ZERO, converters.toBigDecimal("abc"))
        assertNull(converters.toBigDecimal(null))

        val encoded = Json.encodeToString(BigDecimalSerializer, BigDecimal("0.10"))
        assertEquals("\"0.10\"", encoded)
        assertEquals(BigDecimal("0.10"), Json.decodeFromString(BigDecimalSerializer, encoded))
    }

    // ---- Export / import file ----------------------------------------------------------------

    private fun export(vararg days: DailyExpense, vendorPayments: List<VendorPayment> = emptyList()) = ExportData(
        exportDate = "2026-10-08T12:00:00",
        dailyExpenses = days.toList(),
        weeklySettlements = emptyList(),
        excessBalances = emptyList(),
        expenseTypes = emptyList(),
        expenseSubtypes = emptyList(),
        incomeTypes = emptyList(),
        workerTypes = emptyList(),
        permanentWorkers = emptyList(),
        workTasks = emptyList(),
        workerPayments = emptyList(),
        weeklyFunds = emptyList(),
        vendorPayments = vendorPayments
    )

    @Test
    fun exportFileUsesTheSameKeysAsOlderVersions() {
        val text = json.encodeToString(ExportData.serializer(), export(dailyExpense("2026-10-01")))
        val keys = json.parseToJsonElement(text).jsonObject.keys
        // Changing any of these would make old backups unreadable (and new ones unreadable by old apps).
        assertTrue(
            keys.containsAll(
                listOf(
                    "export_date", "app_version", "daily_expenses", "weekly_settlements", "excess_balances",
                    "expense_types", "expense_subtypes", "income_types", "worker_types", "permanent_workers",
                    "work_tasks", "worker_payments", "weekly_funds", "vendor_payments", "receipts"
                )
            )
        )
        val day = json.parseToJsonElement(text).jsonObject["daily_expenses"].toString()
        assertTrue("amounts are exported as text", day.contains("\"totalLaborCost\":\"0\""))
    }

    @Test
    fun exportRoundTripKeepsEveryAmountAndEntry() {
        val original = dailyExpense(
            date = "2026-10-07",
            groups = listOf(group("Malayali Male", 8, 575, task = "Harvesting", otHours = 1, otRate = 70)),
            others = listOf(other("Cardamom Drying", "3350.50".toBigDecimal()), other("Pesticides", 7_660, paid = true)),
            incomes = listOf(income("Cardamom Sales", 15_000, weight = 30.0)),
            advances = listOf(advance(3_350, recipient = "Kaa onakku")),
            settlement = 37_310,
            comments = "Rain in the afternoon"
        )
        val payment = VendorPayment(date = "2026-10-08", amount = money(7_660), sourceExpenseDate = "2026-10-07", sourceTypeName = "Pesticides")

        val text = json.encodeToString(ExportData.serializer(), export(original, vendorPayments = listOf(payment)))
        val restored = json.decodeFromString(ExportData.serializer(), text)

        assertEquals(original, restored.dailyExpenses.single())
        assertEquals(payment, restored.vendorPayments.single())
    }

    @Test
    fun importAcceptsBackupsFromOlderVersionsAndIgnoresUnknownFields() {
        // An old backup: no vendor_payments or receipts sections, and a field this version doesn't know.
        val oldBackup = """
            {
              "export_date": "2026-03-01T10:00:00",
              "app_version": "1.0",
              "daily_expenses": [{ "date": "2026-02-27", "totalLaborCost": "4600", "weeklyPaymentDone": "4600",
                                   "someFutureField": true }],
              "weekly_settlements": [], "excess_balances": [], "expense_types": [], "expense_subtypes": [],
              "income_types": [], "worker_types": [], "permanent_workers": [], "work_tasks": [],
              "worker_payments": [], "weekly_funds": []
            }
        """.trimIndent()

        val restored = json.decodeFromString(ExportData.serializer(), oldBackup)

        val day = restored.dailyExpenses.single()
        assertMoney(4_600, day.totalLaborCost)
        assertEquals("[]", day.otherExpenses)
        assertTrue(restored.vendorPayments.isEmpty())
        assertTrue(restored.receipts.isEmpty())
    }
}
