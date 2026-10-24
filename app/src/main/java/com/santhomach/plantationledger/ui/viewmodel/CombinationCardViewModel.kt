package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.schedule.CombinationCard
import com.santhomach.plantationledger.data.schedule.CombinationCategory
import com.santhomach.plantationledger.data.schedule.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardForm(
    val name: String = "",
    val purpose: String = "",
    val combination: String = "",
    val usage: String = "",
    val comments: String = ""
)

data class CombinationCardUiState(
    val card: CombinationCard? = null,
    val form: CardForm = CardForm(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Saved or deleted: the page can close. */
    val isDone: Boolean = false,
    val notFound: Boolean = false,
    val error: String? = null
) {
    val category: CombinationCategory?
        get() = card?.let { c -> CombinationCategory.entries.firstOrNull { it.name == c.category } }

    val hasChanges: Boolean
        get() = card != null && form != CardForm(card.name, card.purpose, card.combination, card.usage, card.comments)

    val canSave: Boolean get() = form.name.isNotBlank() && !isSaving
}

/** One combination card's page: edit its fields, save to the database, or delete it. */
@HiltViewModel
class CombinationCardViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val cardId: Int = savedStateHandle.get<Int>(CARD_ID) ?: 0

    private val _uiState = MutableStateFlow(CombinationCardUiState())
    val uiState: StateFlow<CombinationCardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val card = try {
                repository.card(cardId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
                return@launch
            }
            _uiState.update {
                if (card == null) it.copy(isLoading = false, notFound = true)
                else it.copy(
                    card = card,
                    form = CardForm(card.name, card.purpose, card.combination, card.usage, card.comments),
                    isLoading = false
                )
            }
        }
    }

    fun updateForm(transform: (CardForm) -> CardForm) = _uiState.update { it.copy(form = transform(it.form)) }

    fun save() {
        val state = _uiState.value
        val card = state.card ?: return
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val form = state.form
                repository.saveCard(
                    card.copy(
                        name = form.name,
                        purpose = form.purpose.trim(),
                        combination = form.combination.trim(),
                        usage = form.usage.trim(),
                        comments = form.comments.trim()
                    )
                )
                _uiState.update { it.copy(isSaving = false, isDone = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Could not save: ${e.message}") }
            }
        }
    }

    fun delete() {
        val card = _uiState.value.card ?: return
        viewModelScope.launch {
            try {
                repository.deleteCard(card)
                _uiState.update { it.copy(isDone = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not delete: ${e.message}") }
            }
        }
    }

    companion object {
        const val CARD_ID = "cardId"
    }
}
