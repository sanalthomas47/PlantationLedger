package com.santhomach.plantationledger.testutil

import com.santhomach.plantationledger.data.schedule.CombinationCard
import com.santhomach.plantationledger.data.schedule.ScheduleDao
import com.santhomach.plantationledger.data.schedule.ScheduleTask
import com.santhomach.plantationledger.data.schedule.TaskCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The schedule DAO kept in memory, with the same ordering, id generation and delete cascade
 * (a task's ticks go with it) as the Room tables. [replaceAll] is the real implementation.
 */
class FakeScheduleDao(
    tasks: List<ScheduleTask> = emptyList(),
    completions: List<TaskCompletion> = emptyList(),
    cards: List<CombinationCard> = emptyList()
) : ScheduleDao() {
    val tasks = MutableStateFlow(tasks)
    val completions = MutableStateFlow(completions)
    val cards = MutableStateFlow(cards)
    private var nextTaskId = (tasks.maxOfOrNull { it.id } ?: 0) + 1
    private var nextCardId = (cards.maxOfOrNull { it.id } ?: 0) + 1

    private fun List<ScheduleTask>.orderedTasks() =
        sortedWith(compareBy({ it.month }, { it.sortOrder }, { it.id }))

    private fun List<CombinationCard>.orderedCards() =
        sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override fun tasksFlow(): Flow<List<ScheduleTask>> = tasks.map { it.orderedTasks() }
    override suspend fun allTasks() = tasks.value.orderedTasks()
    override suspend fun nextTaskOrder(month: Int) =
        (tasks.value.filter { it.month == month }.maxOfOrNull { it.sortOrder } ?: -1) + 1

    override suspend fun insertTask(task: ScheduleTask): Long {
        val id = if (task.id == 0) nextTaskId++ else task.id
        tasks.value = tasks.value + task.copy(id = id)
        return id.toLong()
    }

    override suspend fun updateTask(task: ScheduleTask) {
        tasks.value = tasks.value.map { if (it.id == task.id) task else it }
    }

    override suspend fun deleteTask(task: ScheduleTask) {
        tasks.value = tasks.value.filterNot { it.id == task.id }
        completions.value = completions.value.filterNot { it.taskId == task.id }
    }

    override fun completionsFlow(year: Int) =
        completions.map { list -> list.filter { it.year == year } }

    override suspend fun allCompletions() = completions.value

    override suspend fun insertCompletion(completion: TaskCompletion) {
        require(tasks.value.any { it.id == completion.taskId }) { "FOREIGN KEY constraint failed" }
        completions.value =
            completions.value.filterNot { it.taskId == completion.taskId && it.year == completion.year } + completion
    }

    override suspend fun deleteCompletion(taskId: Int, year: Int) {
        completions.value = completions.value.filterNot { it.taskId == taskId && it.year == year }
    }

    override fun cardsFlow(category: String) =
        cards.map { list -> list.filter { it.category == category }.orderedCards() }

    override suspend fun allCards() =
        cards.value.sortedWith(compareBy({ it.category }, { it.sortOrder }, { it.id }))

    override suspend fun card(id: Int) = cards.value.firstOrNull { it.id == id }
    override suspend fun nextCardOrder(category: String) =
        (cards.value.filter { it.category == category }.maxOfOrNull { it.sortOrder } ?: -1) + 1

    override suspend fun insertCard(card: CombinationCard): Long {
        val id = if (card.id == 0) nextCardId++ else card.id
        cards.value = cards.value + card.copy(id = id)
        return id.toLong()
    }

    override suspend fun updateCard(card: CombinationCard) {
        cards.value = cards.value.map { if (it.id == card.id) card else it }
    }

    override suspend fun deleteCard(card: CombinationCard) {
        cards.value = cards.value.filterNot { it.id == card.id }
    }

    override suspend fun clearCompletions() {
        completions.value = emptyList()
    }

    override suspend fun clearTasks() {
        tasks.value = emptyList()
    }

    override suspend fun clearCards() {
        cards.value = emptyList()
    }

    override suspend fun insertTasks(tasks: List<ScheduleTask>) {
        val ids = tasks.map { it.id }.toSet()
        this.tasks.value = this.tasks.value.filterNot { it.id in ids } + tasks
        nextTaskId = maxOf(nextTaskId, (tasks.maxOfOrNull { it.id } ?: 0) + 1)
    }

    override suspend fun insertCompletions(completions: List<TaskCompletion>) {
        completions.forEach { insertCompletion(it) }
    }

    override suspend fun insertCards(cards: List<CombinationCard>) {
        val ids = cards.map { it.id }.toSet()
        this.cards.value = this.cards.value.filterNot { it.id in ids } + cards
        nextCardId = maxOf(nextCardId, (cards.maxOfOrNull { it.id } ?: 0) + 1)
    }
}
