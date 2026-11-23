package com.santhomach.plantationledger.data.inventory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The inventory's own database. It is a separate file from the expense database, whose schema and
 * version stay unchanged.
 */
@Database(entities = [InventoryItem::class], version = 1, exportSchema = true)
abstract class InventoryDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao

    companion object {
        private const val DATABASE_NAME = "plantation_inventory.db"

        @Volatile
        private var INSTANCE: InventoryDatabase? = null

        fun getInstance(context: Context): InventoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    InventoryDatabase::class.java,
                    DATABASE_NAME
                )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
