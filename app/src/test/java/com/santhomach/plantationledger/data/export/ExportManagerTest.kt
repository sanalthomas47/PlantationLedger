package com.santhomach.plantationledger.data.export

import android.content.Context
import android.util.Base64
import com.santhomach.plantationledger.data.AppDatabase
import com.santhomach.plantationledger.data.model.ExcessBalance
import com.santhomach.plantationledger.data.model.ExpenseSubtype
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.PermanentWorker
import com.santhomach.plantationledger.data.model.VendorPayment
import com.santhomach.plantationledger.data.model.WeeklyFunds
import com.santhomach.plantationledger.data.model.WeeklySettlement
import com.santhomach.plantationledger.data.model.WorkTask
import com.santhomach.plantationledger.data.model.WorkerPayment
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.testutil.FakeDatabase
import com.santhomach.plantationledger.testutil.dailyExpense
import com.santhomach.plantationledger.testutil.group
import com.santhomach.plantationledger.testutil.money
import com.santhomach.plantationledger.testutil.other
import com.santhomach.plantationledger.testutil.otherEntriesOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Backup and restore of the whole database, which is what protects the user's data. */
class ExportManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        mockkObject(AppDatabase.Companion)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() = unmockkAll()

    /** A device: its own files folder and database. */
    private class Device(val filesDir: File) {
        val fake = FakeDatabase().apply { stubTransactions() }
        val context: Context = mockk(relaxed = true) { every { filesDir } returns this@Device.filesDir }
        val manager: ExportManager

        init {
            every { AppDatabase.getInstance(context) } returns fake.db
            manager = ExportManager(context)
        }
    }

    private fun device(name: String) = Device(tmp.newFolder(name))

    private fun fillWithOneOfEverything(fake: FakeDatabase, receiptPath: String) {
        val pesticides = ExpenseType(typeName = "Pesticides")
        fake.expenseTypes.insert(pesticides)
        fake.expenseSubtypes.insert(ExpenseSubtype(typeName = "Diesel", parentTypeName = ExpenseType(typeName = "Fuel")))
        fake.incomeTypes.insert(IncomeType(typeName = "Cardamom Sales"))
        fake.workerTypes.insert(WorkerType(workerTypeName = "Malayali Male", dailyBasicWage = money(600)))
        fake.permanentWorkers.insert(PermanentWorker(name = "Manager", role = "Supervisor", dailyBasicWage = money(900)))
        fake.workTasks.insert(WorkTask(taskName = "Weeding"))
        fake.expenses.insert(dailyExpense("2024-03-04",
            groups = listOf(group("Malayali Male", 3, 600)),
            others = listOf(other("Pesticides", 2_500.75).copy(receiptImagePath = receiptPath)),
            settlement = 1_000, comments = "മഴ / rain"))
        fake.workerPayments.insert(WorkerPayment(workerId = 1, workerName = "Manager", amount = money(9_000),
            paymentDate = "2024-03-31"))
        fake.settlements.insert(WeeklySettlement(settlementDate = "2024-03-09", weekStartDate = "2024-03-04",
            weekEndDate = "2024-03-09"))
        fake.excessBalances.insert(ExcessBalance(date = "2024-03-09", amount = money(255), direction = "CREDIT",
            reason = "carry"))
        fake.weeklyFunds.insert(WeeklyFunds(weekStartDate = "2024-03-04", amountReceived = money(20_000)))
        fake.vendorPayments.insert(VendorPayment(date = "2024-03-20", amount = money(2_500.75),
            sourceExpenseDate = "2024-03-04", sourceTypeName = "Pesticides"))
    }

    @Test
    fun aBackupRestoresEveryTableAndReceiptOnAnotherDevice() = runTest {
        val phone = device("phone")
        val receipt = File(phone.filesDir, "receipts/bill1.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, -1))
        }
        fillWithOneOfEverything(phone.fake, receipt.absolutePath)

        val json = phone.manager.exportToJson()
        val newPhone = device("newPhone")
        val note = newPhone.manager.importFromJson(json)

        assertNull(note)
        val a = phone.fake
        val b = newPhone.fake
        assertEquals(a.expenseTypes.all(), b.expenseTypes.all())
        assertEquals(a.expenseSubtypes.all(), b.expenseSubtypes.all())
        assertEquals(a.incomeTypes.all(), b.incomeTypes.all())
        assertEquals(a.workerTypes.all(), b.workerTypes.all())
        assertEquals(a.permanentWorkers.all(), b.permanentWorkers.all())
        assertEquals(a.workTasks.all(), b.workTasks.all())
        assertEquals(a.workerPayments.all(), b.workerPayments.all())
        assertEquals(a.settlements.all(), b.settlements.all())
        assertEquals(a.excessBalances.all(), b.excessBalances.all())
        assertEquals(a.weeklyFunds.all(), b.weeklyFunds.all())
        assertEquals(a.vendorPayments.all(), b.vendorPayments.all())

        val restoredDay = b.expenses.all().single()
        val originalDay = a.expenses.all().single()
        assertEquals(originalDay.copy(otherExpenses = ""), restoredDay.copy(otherExpenses = ""))
        val restoredBill = otherEntriesOf(restoredDay).single()
        assertEquals(0, money(2_500.75).compareTo(restoredBill.amount))
        // The receipt now points at the new device's own copy of the image.
        val restoredReceipt = File(newPhone.filesDir, "receipts/bill1.jpg")
        assertEquals(restoredReceipt.absolutePath, restoredBill.receiptImagePath)
        assertArrayEquals(receipt.readBytes(), restoredReceipt.readBytes())

        assertTrue("all writes in one transaction", b.writesOutsideTransaction.isEmpty())
    }

    @Test
    fun theExportUsesTheDocumentedKeys() = runTest {
        val phone = device("phone")
        fillWithOneOfEverything(phone.fake, "")
        val root = Json.parseToJsonElement(phone.manager.exportToJson()).jsonObject
        listOf("export_date", "daily_expenses", "weekly_settlements", "excess_balances", "expense_types",
            "expense_subtypes", "income_types", "worker_types", "permanent_workers", "work_tasks",
            "worker_payments", "weekly_funds", "vendor_payments", "receipts"
        ).forEach { key -> assertTrue("missing $key", key in root) }
        assertEquals(1, root.getValue("vendor_payments").jsonArray.size)
    }

    @Test
    fun anOldBackupWithoutVendorPaymentsKeepsTheOnesOnThePhone() = runTest {
        val old = device("old")
        fillWithOneOfEverything(old.fake, "")
        old.fake.vendorPayments.all().forEach { old.fake.vendorPayments.delete(it) }
        val json = old.manager.exportToJson()

        val phone = device("phone")
        phone.fake.vendorPayments.insert(VendorPayment(date = "2024-05-01", amount = money(700)))
        phone.fake.vendorPayments.insert(VendorPayment(date = "2024-05-02", amount = money(800)))

        val note = phone.manager.importFromJson(json)

        assertTrue(note!!.contains("2 existing vendor payment record(s) have been preserved"))
        assertEquals(2, phone.fake.vendorPayments.all().size)
        assertEquals(1, phone.fake.expenses.all().size)
    }

    @Test
    fun anEmptyOrBrokenFileIsRejectedWithoutTouchingTheData() = runTest {
        val phone = device("phone")
        fillWithOneOfEverything(phone.fake, "")
        val before = phone.fake.expenses.all()

        listOf("", "   ", "{ not json", """{"export_date":"x"}""").forEach { bad ->
            try {
                phone.manager.importFromJson(bad)
                fail("expected the import of '$bad' to fail")
            } catch (e: ExportException) {
                assertTrue(e.message!!.startsWith("Failed to import data"))
            }
        }
        assertEquals("Import file is empty", runCatching { phone.manager.importFromJson("") }
            .exceptionOrNull()!!.cause!!.message)
        assertEquals(before, phone.fake.expenses.all())
    }

    @Test
    fun receiptsThatCannotBeDecodedAreSkippedAndTheRestIsImported() = runTest {
        val json = """
            {"export_date":"2024-03-04T10:00","daily_expenses":[],"weekly_settlements":[],
             "excess_balances":[],"expense_types":[{"id":1,"typeName":"Fuel"}],"expense_subtypes":[],
             "income_types":[],"worker_types":[],"permanent_workers":[],"work_tasks":[],
             "worker_payments":[],"weekly_funds":[],
             "receipts":[{"filename":"bad.jpg","base64Data":"%%%"},{"filename":"ok.jpg","base64Data":"AQID"}]}
        """.trimIndent()
        val phone = device("phone")

        assertNull(phone.manager.importFromJson(json))

        assertEquals(listOf("Fuel"), phone.fake.expenseTypes.all().map { it.typeName })
        assertArrayEquals(byteArrayOf(1, 2, 3), File(phone.filesDir, "receipts/ok.jpg").readBytes())
    }
}
