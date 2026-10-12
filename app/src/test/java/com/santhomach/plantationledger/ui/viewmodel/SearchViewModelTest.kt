package com.santhomach.plantationledger.ui.viewmodel

import com.santhomach.plantationledger.data.repository.ExpenseRepository
import com.santhomach.plantationledger.testutil.MainDispatcherRule
import com.santhomach.plantationledger.testutil.dailyExpense
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val repository: ExpenseRepository = mockk()
    private val day = dailyExpense("2024-03-04", comments = "diesel for pump")

    @Test
    fun searchesFromThreeLettersAndClearsWhenTheQueryGetsShorter() = runTest(main.dispatcher) {
        coEvery { repository.searchDailyExpenses("die") } returns listOf(day)
        val vm = SearchViewModel(repository)

        vm.onSearchQueryChange("di")
        assertTrue(vm.searchResults.value.isEmpty())
        coVerify(exactly = 0) { repository.searchDailyExpenses(any()) }

        vm.onSearchQueryChange("die")
        assertEquals(listOf(day), vm.searchResults.value)
        assertEquals("die", vm.searchQuery.value)
        assertFalse(vm.isSearching.value)

        vm.onSearchQueryChange("di")
        assertTrue(vm.searchResults.value.isEmpty())
    }

    @Test
    fun aFailedSearchShowsNoResultsAndStopsSearching() = runTest(main.dispatcher) {
        coEvery { repository.searchDailyExpenses(any()) } throws IllegalStateException("db closed")
        val vm = SearchViewModel(repository)

        vm.onSearchQueryChange("pump")

        assertTrue(vm.searchResults.value.isEmpty())
        assertFalse(vm.isSearching.value)
    }
}
