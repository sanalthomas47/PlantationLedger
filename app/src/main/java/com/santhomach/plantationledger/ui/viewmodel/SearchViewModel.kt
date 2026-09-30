package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.model.DailyExpense
import com.santhomach.plantationledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<DailyExpense>>(emptyList())
    val searchResults: StateFlow<List<DailyExpense>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                _searchResults.value = repository.searchDailyExpenses(query)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }
}
