package com.santhomach.plantationledger.testutil

import com.santhomach.plantationledger.data.inventory.InventoryDao
import com.santhomach.plantationledger.data.inventory.InventoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The inventory DAO kept in memory, with the same ordering, id generation and never-below-zero
 * count change as the Room table. [replaceAll] is the real implementation.
 */
class FakeInventoryDao(items: List<InventoryItem> = emptyList()) : InventoryDao() {
    val items = MutableStateFlow(items)
    private var nextId = (items.maxOfOrNull { it.id } ?: 0) + 1

    private fun List<InventoryItem>.ordered() = sortedWith(
        compareBy<InventoryItem, String>(String.CASE_INSENSITIVE_ORDER) { it.category }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.id }
    )

    override fun itemsFlow(): Flow<List<InventoryItem>> = items.map { it.ordered() }
    override suspend fun allItems() = items.value.ordered()
    override suspend fun item(id: Int) = items.value.firstOrNull { it.id == id }

    override suspend fun insertItem(item: InventoryItem): Long {
        val id = if (item.id == 0) nextId++ else item.id
        items.value = items.value + item.copy(id = id)
        return id.toLong()
    }

    override suspend fun updateItem(item: InventoryItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun deleteItem(item: InventoryItem) {
        items.value = items.value.filterNot { it.id == item.id }
    }

    override suspend fun changeCount(id: Int, delta: Int, updatedAt: String) {
        items.value = items.value.map {
            if (it.id == id) it.copy(count = maxOf(0, it.count + delta), updatedAt = updatedAt) else it
        }
    }

    override suspend fun clearItems() {
        items.value = emptyList()
    }

    override suspend fun insertItems(items: List<InventoryItem>) {
        val ids = items.map { it.id }.toSet()
        this.items.value = this.items.value.filterNot { it.id in ids } + items
        nextId = maxOf(nextId, (items.maxOfOrNull { it.id } ?: 0) + 1)
    }
}
