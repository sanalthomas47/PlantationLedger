package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.schedule.CombinationCard
import com.santhomach.plantationledger.data.schedule.CombinationCategory
import com.santhomach.plantationledger.data.schedule.ScheduleRepository
import com.santhomach.plantationledger.data.schedule.ScheduleTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MonthChecklist(
    /** 1 = January ... 12 = December. */
    val month: Int,
    val tasks: List<ScheduleTask>,
    /** Ids of the tasks ticked for the selected year. */
    val done: Set<Int>
) {
    val doneCount get() = tasks.count { it.id in done }
}

data class ScheduleUiState(
    val year: Int = LocalDate.now().year,
    val months: List<MonthChecklist> = (1..12).map { MonthChecklist(it, emptyList(), emptySet()) },
    val cards: Map<CombinationCategory, List<CombinationCard>> = CombinationCategory.entries.associateWith { emptyList() },
    val isLoading: Boolean = true,
    val error: String? = null
)

/** The Schedule page: the monthly checklist for a chosen year and the three card lists. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    private val _year = MutableStateFlow(LocalDate.now().year)
    private val _error = MutableStateFlow<String?>(null)

    private val completions = _year.flatMapLatest { repository.completionsFlow(it) }

    private val cards = combine(CombinationCategory.entries.map { category -> repository.cardsFlow(category) }) { lists ->
        CombinationCategory.entries.zip(lists.toList()).toMap()
    }

    val uiState: StateFlow<ScheduleUiState> =
        combine(_year, repository.tasksFlow(), completions, cards, _error) { year, tasks, done, cardMap, error ->
            val doneIds = done.map { it.taskId }.toSet()
            val byMonth = tasks.groupBy { it.month }
            ScheduleUiState(
                year = year,
                months = (1..12).map { month -> MonthChecklist(month, byMonth[month].orEmpty(), doneIds) },
                cards = cardMap,
                isLoading = false,
                error = error
            )
        }
            .catch { e -> emit(ScheduleUiState(isLoading = false, error = e.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleUiState())

    fun changeYear(delta: Int) = _year.update { it + delta }

    fun setDone(task: ScheduleTask, done: Boolean) = launchSafely { repository.setDone(task.id, _year.value, done) }

    fun addTask(month: Int, title: String) {
        if (title.isBlank()) return
        launchSafely { repository.addTask(month, title) }
    }

    fun renameTask(task: ScheduleTask, title: String) {
        if (title.isBlank()) return
        launchSafely { repository.renameTask(task, title) }
    }

    fun deleteTask(task: ScheduleTask) = launchSafely { repository.deleteTask(task) }

    /** Creates a card and hands its id to [onCreated] so its page can be opened. */
    fun addCard(category: CombinationCategory, name: String, onCreated: (Int) -> Unit) {
        if (name.isBlank()) return
        launchSafely { onCreated(repository.addCard(category, name).toInt()) }
    }

    fun clearError() = _error.update { null }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _error.update { "Could not save: ${e.message}" }
            }
        }
    }
}
