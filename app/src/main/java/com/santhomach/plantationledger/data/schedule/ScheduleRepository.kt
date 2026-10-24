package com.santhomach.plantationledger.data.schedule

import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/** Monthly checklist and combination cards. */
class ScheduleRepository(private val dao: ScheduleDao) {

    fun tasksFlow(): Flow<List<ScheduleTask>> = dao.tasksFlow()

    fun completionsFlow(year: Int): Flow<List<TaskCompletion>> = dao.completionsFlow(year)

    suspend fun addTask(month: Int, title: String): Long {
        require(month in 1..12) { "Month must be 1-12" }
        return dao.insertTask(ScheduleTask(month = month, title = title.trim(), sortOrder = dao.nextTaskOrder(month)))
    }

    suspend fun renameTask(task: ScheduleTask, title: String) = dao.updateTask(task.copy(title = title.trim()))

    suspend fun deleteTask(task: ScheduleTask) = dao.deleteTask(task)

    suspend fun setDone(taskId: Int, year: Int, done: Boolean) {
        if (done) dao.insertCompletion(TaskCompletion(taskId = taskId, year = year))
        else dao.deleteCompletion(taskId, year)
    }

    fun cardsFlow(category: CombinationCategory): Flow<List<CombinationCard>> = dao.cardsFlow(category.name)

    suspend fun card(id: Int): CombinationCard? = dao.card(id)

    /** Creates a card with just its name and returns its id. */
    suspend fun addCard(category: CombinationCategory, name: String): Long =
        dao.insertCard(
            CombinationCard(category = category.name, name = name.trim(), sortOrder = dao.nextCardOrder(category.name))
        )

    suspend fun saveCard(card: CombinationCard) =
        dao.updateCard(card.copy(name = card.name.trim(), updatedAt = LocalDateTime.now().toString()))

    suspend fun deleteCard(card: CombinationCard) = dao.deleteCard(card)

    /** Everything, for a backup file. */
    suspend fun snapshot(): ScheduleExport =
        ScheduleExport(tasks = dao.allTasks(), completions = dao.allCompletions(), cards = dao.allCards())

    /** Restores a backup: the schedule becomes exactly what the backup holds. */
    suspend fun replaceAll(data: ScheduleExport) = dao.replaceAll(data)
}
