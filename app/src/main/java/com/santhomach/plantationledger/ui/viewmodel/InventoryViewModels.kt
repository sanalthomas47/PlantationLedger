package com.santhomach.plantationledger.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santhomach.plantationledger.data.inventory.DEFAULT_CATEGORY
import com.santhomach.plantationledger.data.inventory.INVENTORY_CATEGORIES
import com.santhomach.plantationledger.data.inventory.InventoryItem
import com.santhomach.plantationledger.data.inventory.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

// -------------------------------------------------------------------------------------------------
// The list
// -------------------------------------------------------------------------------------------------

data class InventoryGroup(val category: String, val items: List<InventoryItem>) {
    val totalCount: Int get() = items.sumOf { it.count }
}

data class InventoryUiState(
    val groups: List<InventoryGroup> = emptyList(),
    val query: String = "",
    /** Items on the phone, before the search filter. */
    val itemCount: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val shownItems: Int get() = groups.sumOf { it.items.size }
    val shownCount: Int get() = groups.sumOf { it.totalCount }
}

/** Groups items by category (case-insensitive, A-Z) and keeps those matching [query]. */
fun groupInventory(items: List<InventoryItem>, query: String = ""): List<InventoryGroup> {
    val q = query.trim()
    return items
        .filter {
            q.isEmpty() || it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) || it.notes.contains(q, ignoreCase = true)
        }
        .groupBy { it.category.trim().ifEmpty { DEFAULT_CATEGORY }.lowercase() }
        .map { (key, list) ->
            // "tools" and "Tools" are one group, named as the standard list spells it, otherwise as most items do.
            val label = INVENTORY_CATEGORIES.firstOrNull { it.lowercase() == key }
                ?: list.groupingBy { it.category.trim() }.eachCount().maxBy { it.value }.key.ifEmpty { DEFAULT_CATEGORY }
            InventoryGroup(label, list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.category })
}

/** The inventory list: items grouped by category, a search box and quick + / − on each count. */
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<InventoryUiState> =
        combine(repository.itemsFlow(), query, error) { items, q, err ->
            InventoryUiState(
                groups = groupInventory(items, q),
                query = q,
                itemCount = items.size,
                isLoading = false,
                error = err
            )
        }
            .catch { e -> emit(InventoryUiState(isLoading = false, error = e.message)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InventoryUiState())

    fun setQuery(value: String) = query.update { value }

    fun increment(item: InventoryItem) = change(item, +1)

    fun decrement(item: InventoryItem) {
        if (item.count > 0) change(item, -1)
    }

    fun clearError() = error.update { null }

    private fun change(item: InventoryItem, delta: Int) {
        viewModelScope.launch {
            try {
                repository.changeCount(item.id, delta)
            } catch (e: Exception) {
                error.update { "Could not change the count: ${e.message}" }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// One item's page
// -------------------------------------------------------------------------------------------------

data class InventoryForm(
    val name: String = "",
    val category: String = DEFAULT_CATEGORY,
    val count: String = "1",
    val inUseSince: LocalDate? = null,
    val purchasePrice: String = "",
    val notes: String = ""
) {
    val countValue: Int? get() = count.trim().toIntOrNull()?.takeIf { it >= 0 }

    val priceValue: BigDecimal?
        get() = purchasePrice.trim().replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }

    val countError: Boolean get() = countValue == null
    val priceError: Boolean get() = purchasePrice.isNotBlank() && priceValue == null

    companion object {
        fun of(item: InventoryItem) = InventoryForm(
            name = item.name,
            category = item.category,
            count = item.count.toString(),
            inUseSince = item.inUseSince?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            purchasePrice = item.purchasePrice.orEmpty(),
            notes = item.notes
        )
    }
}

data class InventoryItemUiState(
    /** Null while adding a new item. */
    val item: InventoryItem? = null,
    val form: InventoryForm = InventoryForm(),
    val original: InventoryForm = InventoryForm(),
    /** The standard categories plus every one already in use. */
    val categories: List<String> = INVENTORY_CATEGORIES,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    /** Saved or deleted: the page can close. */
    val isDone: Boolean = false,
    val notFound: Boolean = false,
    val error: String? = null
) {
    val isNew: Boolean get() = item == null
    val hasChanges: Boolean get() = form != original
    val canSave: Boolean
        get() = form.name.isNotBlank() && !form.countError && !form.priceError && !isSaving && !isLoading
}

/** Adds a new item (id 0) or edits, saves and deletes an existing one. */
@HiltViewModel
class InventoryItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: Int = savedStateHandle.get<Int>(ITEM_ID) ?: 0

    private val _uiState = MutableStateFlow(InventoryItemUiState())
    val uiState: StateFlow<InventoryItemUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val inUse = repository.itemsFlow().first().map { it.category.trim() }.filter { it.isNotEmpty() }
                val categories = (INVENTORY_CATEGORIES + inUse).distinctBy { it.lowercase() }
                if (itemId == 0) {
                    _uiState.update { it.copy(categories = categories, isLoading = false) }
                    return@launch
                }
                val item = repository.item(itemId)
                _uiState.update {
                    if (item == null) it.copy(categories = categories, isLoading = false, notFound = true)
                    else InventoryForm.of(item).let { form ->
                        it.copy(item = item, form = form, original = form, categories = categories, isLoading = false)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateForm(transform: (InventoryForm) -> InventoryForm) = _uiState.update { it.copy(form = transform(it.form)) }

    /** The − / + beside the count box. */
    fun stepCount(delta: Int) = updateForm { form ->
        form.copy(count = ((form.countValue ?: 0) + delta).coerceAtLeast(0).toString())
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val form = state.form
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val base = state.item ?: InventoryItem(name = form.name)
                val item = base.copy(
                    name = form.name,
                    category = form.category,
                    count = form.countValue ?: 0,
                    inUseSince = form.inUseSince?.toString(),
                    purchasePrice = form.priceValue?.stripTrailingZeros()?.toPlainString(),
                    notes = form.notes
                )
                if (state.item == null) repository.addItem(item) else repository.saveItem(item)
                _uiState.update { it.copy(isSaving = false, isDone = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = "Could not save: ${e.message}") }
            }
        }
    }

    fun delete() {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            try {
                repository.deleteItem(item)
                _uiState.update { it.copy(isDone = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Could not delete: ${e.message}") }
            }
        }
    }

    companion object {
        const val ITEM_ID = "itemId"
    }
}
