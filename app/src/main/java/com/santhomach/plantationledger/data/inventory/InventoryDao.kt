package com.santhomach.plantationledger.data.inventory

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InventoryDao {

    @Query("SELECT * FROM inventory_items ORDER BY category COLLATE NOCASE, name COLLATE NOCASE, id")
    abstract fun itemsFlow(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items ORDER BY category COLLATE NOCASE, name COLLATE NOCASE, id")
    abstract suspend fun allItems(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    abstract suspend fun item(id: Int): InventoryItem?

    @Insert
    abstract suspend fun insertItem(item: InventoryItem): Long

    @Update
    abstract suspend fun updateItem(item: InventoryItem)

    @Delete
    abstract suspend fun deleteItem(item: InventoryItem)

    /** Adds [delta] to the count in one step; the count never goes below zero. */
    @Query("UPDATE inventory_items SET count = MAX(0, count + :delta), updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun changeCount(id: Int, delta: Int, updatedAt: String)

    // Restore from a backup
    @Query("DELETE FROM inventory_items")
    protected abstract suspend fun clearItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertItems(items: List<InventoryItem>)

    /** Replaces the whole inventory in one transaction: all of it is restored, or none of it. */
    @Transaction
    open suspend fun replaceAll(items: List<InventoryItem>) {
        clearItems()
        insertItems(items)
    }
}
