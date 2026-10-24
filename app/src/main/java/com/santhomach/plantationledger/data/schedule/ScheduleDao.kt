package com.santhomach.plantationledger.data.schedule

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScheduleDao {

    // Tasks
    @Query("SELECT * FROM schedule_tasks ORDER BY month, sortOrder, id")
    abstract fun tasksFlow(): Flow<List<ScheduleTask>>

    @Query("SELECT * FROM schedule_tasks ORDER BY month, sortOrder, id")
    abstract suspend fun allTasks(): List<ScheduleTask>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM schedule_tasks WHERE month = :month")
    abstract suspend fun nextTaskOrder(month: Int): Int

    @Insert
    abstract suspend fun insertTask(task: ScheduleTask): Long

    @Update
    abstract suspend fun updateTask(task: ScheduleTask)

    /** Its ticks for every year are removed with it (foreign key cascade). */
    @Delete
    abstract suspend fun deleteTask(task: ScheduleTask)

    // Ticks
    @Query("SELECT * FROM schedule_task_completions WHERE year = :year")
    abstract fun completionsFlow(year: Int): Flow<List<TaskCompletion>>

    @Query("SELECT * FROM schedule_task_completions")
    abstract suspend fun allCompletions(): List<TaskCompletion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCompletion(completion: TaskCompletion)

    @Query("DELETE FROM schedule_task_completions WHERE taskId = :taskId AND year = :year")
    abstract suspend fun deleteCompletion(taskId: Int, year: Int)

    // Combination cards
    @Query("SELECT * FROM combination_cards WHERE category = :category ORDER BY sortOrder, id")
    abstract fun cardsFlow(category: String): Flow<List<CombinationCard>>

    @Query("SELECT * FROM combination_cards ORDER BY category, sortOrder, id")
    abstract suspend fun allCards(): List<CombinationCard>

    @Query("SELECT * FROM combination_cards WHERE id = :id")
    abstract suspend fun card(id: Int): CombinationCard?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM combination_cards WHERE category = :category")
    abstract suspend fun nextCardOrder(category: String): Int

    @Insert
    abstract suspend fun insertCard(card: CombinationCard): Long

    @Update
    abstract suspend fun updateCard(card: CombinationCard)

    @Delete
    abstract suspend fun deleteCard(card: CombinationCard)

    // Restore from a backup
    @Query("DELETE FROM schedule_task_completions")
    protected abstract suspend fun clearCompletions()

    @Query("DELETE FROM schedule_tasks")
    protected abstract suspend fun clearTasks()

    @Query("DELETE FROM combination_cards")
    protected abstract suspend fun clearCards()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertTasks(tasks: List<ScheduleTask>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCompletions(completions: List<TaskCompletion>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertCards(cards: List<CombinationCard>)

    /** Replaces the whole schedule in one transaction: all of it is restored, or none of it. */
    @Transaction
    open suspend fun replaceAll(data: ScheduleExport) {
        clearCompletions()
        clearTasks()
        clearCards()
        insertTasks(data.tasks)
        val taskIds = data.tasks.map { it.id }.toSet()
        insertCompletions(data.completions.filter { it.taskId in taskIds })
        insertCards(data.cards)
    }
}
