package com.santhomach.plantationledger.data.schedule

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The Schedule's own database (monthly checklist and combination cards). It is a separate file
 * from the expense database, whose schema and version stay unchanged.
 */
@Database(
    entities = [ScheduleTask::class, TaskCompletion::class, CombinationCard::class],
    version = 1,
    exportSchema = true
)
abstract class ScheduleDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    companion object {
        private const val DATABASE_NAME = "plantation_schedule.db"

        @Volatile
        private var INSTANCE: ScheduleDatabase? = null

        fun getInstance(context: Context): ScheduleDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScheduleDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(SeedCallback)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /** Fills in the starting calendar once, when the database file is first created. */
        private object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DefaultSchedule.tasks().forEach { task ->
                    db.insert(
                        "schedule_tasks",
                        SQLiteDatabase.CONFLICT_ABORT,
                        ContentValues().apply {
                            put("month", task.month)
                            put("title", task.title)
                            put("sortOrder", task.sortOrder)
                            put("createdAt", task.createdAt)
                        }
                    )
                }
            }
        }
    }
}
