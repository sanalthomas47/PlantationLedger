package com.santhomach.plantationledger.data.inventory

import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** The estate's equipment, tools and other items with their counts. */
class InventoryRepository(private val dao: InventoryDao) {

    fun itemsFlow(): Flow<List<InventoryItem>> = dao.itemsFlow()

    suspend fun item(id: Int): InventoryItem? = dao.item(id)

    /** Adds a new item and returns its id. */
    suspend fun addItem(item: InventoryItem): Long = dao.insertItem(cleaned(item).copy(id = 0))

    suspend fun saveItem(item: InventoryItem) =
        dao.updateItem(cleaned(item).copy(updatedAt = LocalDateTime.now().toString()))

    suspend fun deleteItem(item: InventoryItem) = dao.deleteItem(item)

    /** The + / − buttons: changes the count by [delta], never below zero. */
    suspend fun changeCount(id: Int, delta: Int) = dao.changeCount(id, delta, LocalDateTime.now().toString())

    /** Everything, for a backup file. */
    suspend fun snapshot(): List<InventoryItem> = dao.allItems()

    /** Restores a backup: the inventory becomes exactly what the backup holds. */
    suspend fun replaceAll(items: List<InventoryItem>) = dao.replaceAll(items)

    private fun cleaned(item: InventoryItem): InventoryItem {
        require(item.name.isNotBlank()) { "An item needs a name" }
        require(item.count >= 0) { "The count cannot be negative" }
        return item.copy(
            name = item.name.trim(),
            category = item.category.trim().ifEmpty { DEFAULT_CATEGORY },
            purchasePrice = item.purchasePrice?.trim()?.ifEmpty { null },
            notes = item.notes.trim()
        )
    }
}
