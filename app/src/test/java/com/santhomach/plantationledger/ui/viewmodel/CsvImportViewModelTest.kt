package com.santhomach.plantationledger.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.santhomach.plantationledger.data.backup.DailyBackupManager
import com.santhomach.plantationledger.data.model.AdvanceEntry
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.model.ExpenseType
import com.santhomach.plantationledger.data.model.IncomeEntry
import com.santhomach.plantationledger.data.model.IncomeType
import com.santhomach.plantationledger.data.model.WorkerType
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.assertMoney
import com.santhomach.plantationledger.testutil.groupsOf
import com.santhomach.plantationledger.testutil.money
import com.santhomach.plantationledger.testutil.otherEntriesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Collections

/**
 * CSV import (rebuilt from bytecode, so it gets the most thorough checks): parsing, grouping
 * rows into one record per date, wage fallbacks, new categories and error reporting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CsvImportViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository: ExpenseRepository = mockk(relaxed = true)
    private val inserted: MutableList<DailyExpense> = Collections.synchronizedList(mutableListOf())

    private fun import(csv: String): CsvImportViewModel.UiState {
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(csv.trimIndent().toByteArray())

        coEvery { repository.getAllActiveExpenseTypes() } returns listOf(ExpenseType(id = 5, typeName = "Fertilizers"))
        coEvery { repository.getAllActiveIncomeTypes() } returns listOf(IncomeType(id = 6, typeName = "Cardamom Sales"))
        coEvery { repository.getAllActiveWorkerTypes() } returns listOf(
            WorkerType(id = 1, workerTypeName = "Malayali Male", dailyBasicWage = money(575)),
            WorkerType(id = 2, workerTypeName = "Bengali Male", dailyBasicWage = money(500))
        )
        coEvery { repository.insertExpenseType(any()) } returns 77L
        coEvery { repository.insertIncomeType(any()) } returns 88L
        coEvery { repository.insertDailyExpense(any()) } answers { inserted.add(firstArg()); inserted.size.toLong() }

        val vm = CsvImportViewModel(repository, context)
        vm.importFromUri(uri)
        // The import runs on Dispatchers.IO; wait for it to finish.
        return runBlocking { withTimeout(10_000) { vm.uiState.first { it.isDone } } }
    }

    private fun day(date: String) = inserted.single { it.date == date }

    @Test
    fun rowsAreMergedIntoOneRecordPerDateWithCorrectTotals() {
        val state = import(
            """
            date,type,category,count,rate,amount,notes
            2023-01-15,LABOR,Malayali Male,5,500,,Weeding
            2023-01-15,LABOR,Bengali Male,3,500,,Weeding
            2023-01-15,EXPENSE,Fertilizers,,,2500,NPK 50kg
            2023-01-15,INCOME,Cardamom Sales,,,15000,30kg at 500/kg
            2023-01-15,ADVANCE,Rajan,,,500,weekly advance
            2023-01-16,LABOR,Malayali Male,2,600,,Harvesting
            """
        )

        assertEquals(2, state.importedCount)
        assertEquals(0, state.skippedCount)
        assertTrue(state.errors.isEmpty())

        with(day("2023-01-15")) {
            assertMoney(4_000, totalLaborCost)                       // 5 x 500 + 3 x 500
            assertEquals(2, groupsOf(this).size)
            assertEquals("Weeding", groupsOf(this).first().taskPerformed)
            assertMoney(2_500, totalOtherExpensesCost)
            assertEquals(5, otherEntriesOf(this).single().expenseTypeId)
            assertMoney(15_000, totalIncome)
            assertMoney(500, advanceAmount)
            val advance = Json.decodeFromString(ListSerializer(AdvanceEntry.serializer()), advanceEntries).single()
            assertEquals("Rajan", advance.recipientName)
            assertEquals("weekly advance", advance.reason)
            assertEquals("import", expenseAdditionType)
        }
        assertMoney(1_200, day("2023-01-16").totalLaborCost)
    }

    @Test
    fun labourWageFallsBackToAmountPerHeadThenToTheWorkerTypeWage() {
        import(
            """
            date,type,category,count,rate,amount,notes
            2023-02-01,LABOR,Malayali Male,4,,2400,
            2023-02-01,LABOR,Bengali Male,2,,,
            """
        )

        val groups = groupsOf(day("2023-02-01"))
        assertMoney(600, groups[0].wagePerDay)    // 2,400 / 4
        assertMoney(500, groups[1].wagePerDay)    // Bengali Male default wage
        assertEquals(2, groups[1].workerTypeId)
        assertMoney(3_400, day("2023-02-01").totalLaborCost)
    }

    @Test
    fun quotedFieldsMayContainCommasAndThousandSeparators() {
        import(
            """
            date,type,category,count,rate,amount,notes
            2023-03-01,EXPENSE,Fertilizers,,,"12,500","NPK, 250kg"
            """
        )

        val entry = otherEntriesOf(day("2023-03-01")).single()
        assertMoney(12_500, entry.amount)
        assertEquals("NPK, 250kg", entry.notes)
    }

    @Test
    fun unknownCategoriesAreCreated() {
        import(
            """
            date,type,category,count,rate,amount,notes
            2023-04-01,EXPENSE,Cardamom Drying,,,3350,
            2023-04-01,INCOME,Pepper Sales,,,9000,
            """
        )

        coVerify { repository.insertExpenseType(match { it.typeName == "Cardamom Drying" }) }
        coVerify { repository.insertIncomeType(match { it.typeName == "Pepper Sales" }) }
        assertEquals(77, otherEntriesOf(day("2023-04-01")).single().expenseTypeId)
        val incomeEntry = Json.decodeFromString(ListSerializer(IncomeEntry.serializer()), day("2023-04-01").incomeEntries).single()
        assertEquals(88, incomeEntry.incomeTypeId)
    }

    @Test
    fun badRowsAreReportedWithLineNumbersAndGoodRowsStillImport() {
        val state = import(
            """
            date,type,category,count,rate,amount,notes
            15/01/2023,LABOR,Malayali Male,5,500,,
            2023-01-15,SALARY,Raja,,,20000,
            # a comment line is ignored
            2023-01-15
            2023-01-16,LABOR,Malayali Male,1,575,,
            """
        )

        assertEquals(1, state.importedCount)
        assertEquals(listOf("2023-01-16"), inserted.map { it.date })
        assertEquals(3, state.errors.size)
        assertTrue(state.errors[0], state.errors[0].startsWith("Line 2: invalid date"))
        assertTrue(state.errors[1], state.errors[1].startsWith("Line 3: unknown type 'SALARY'"))
        assertTrue(state.errors[2], state.errors[2].contains("not enough columns"))
    }

    @Test
    fun importedLabourIsStoredOnceAndSurvivesALaterEdit() {
        import(
            """
            date,type,category,count,rate,amount,notes
            2023-01-15,LABOR,Malayali Male,5,500,,Weeding
            2023-01-15,LABOR,Bengali Male,3,500,,Weeding
            """
        )
        val imported = day("2023-01-15")
        // Labour lives in the worker groups only, not also in the legacy per-type counts.
        assertEquals(0, imported.malayaliMaleCount + imported.bengaliMaleCount)
        assertMoney(4_000, imported.totalLaborCost)

        // Open the imported day on the Daily Expense screen and change something unrelated.
        val dailyRepository: ExpenseRepository = mockk(relaxed = true)
        val dataStore: DataStore<Preferences> = mockk(relaxed = true)
        every { dataStore.data } returns flowOf(emptyPreferences())
        coEvery { dailyRepository.getDailyExpenseById(1) } returns imported.copy(id = 1)
        val daily = DailyExpenseViewModel(dailyRepository, mockk<Application>(relaxed = true), mockk<DailyBackupManager>(relaxed = true), dataStore)
        daily.loadExpense(1)
        daily.updateComments("checked")
        daily.updateExtraOvertimeAmount(money(0))

        assertMoney(4_000, daily.currentExpense.value!!.totalLaborCost)
    }

    @Test
    fun emptyFileIsRejected() {
        val state = import("date,type,category,count,rate,amount,notes")

        assertEquals(0, state.importedCount)
        assertEquals(listOf("File is empty or missing header row"), state.errors)
        coVerify(exactly = 0) { repository.insertDailyExpense(any()) }
    }
}
