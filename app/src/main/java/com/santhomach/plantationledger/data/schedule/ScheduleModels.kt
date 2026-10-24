package com.santhomach.plantationledger.data.schedule

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/*
 * Schedule data lives in its own small database (see ScheduleDatabase), separate from the expense
 * database, so adding or changing it never touches the expense tables.
 */

/** A piece of work that should be done in a calendar month, every year. */
@Serializable
@Entity(tableName = "schedule_tasks", indices = [Index("month")])
data class ScheduleTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** 1 = January ... 12 = December. */
    val month: Int,
    val title: String,
    val sortOrder: Int = 0,
    val createdAt: String = LocalDateTime.now().toString()
)

/** A task ticked as done for one year; next year the same task starts unticked. */
@Serializable
@Entity(
    tableName = "schedule_task_completions",
    primaryKeys = ["taskId", "year"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId"), Index("year")]
)
data class TaskCompletion(
    val taskId: Int,
    val year: Int,
    val completedAt: String = LocalDateTime.now().toString()
)

enum class CombinationCategory(val title: String, val singular: String, val purposeHint: String) {
    FERTILIZER("Fertilizer combinations", "fertilizer combination", "e.g. Pre-monsoon round, flowering"),
    FOLIAR("Foliar combinations", "foliar combination", "e.g. Micronutrient deficiency, capsule size"),
    PESTICIDE("Pesticide combinations", "pesticide combination", "Disease or pest, e.g. Thrips, rhizome rot")
}

/** A fertilizer / foliar / pesticide mix the user keeps for reference. */
@Serializable
@Entity(tableName = "combination_cards", indices = [Index("category")])
data class CombinationCard(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** A [CombinationCategory] name. */
    val category: String,
    /** The card's label: the combination group name. */
    val name: String,
    val purpose: String = "",
    val combination: String = "",
    val usage: String = "",
    val comments: String = "",
    val sortOrder: Int = 0,
    val createdAt: String = LocalDateTime.now().toString(),
    val updatedAt: String = LocalDateTime.now().toString()
)

/** The whole schedule, as stored in a backup file. */
@Serializable
data class ScheduleExport(
    @SerialName("tasks")
    val tasks: List<ScheduleTask> = emptyList(),
    @SerialName("task_completions")
    val completions: List<TaskCompletion> = emptyList(),
    @SerialName("combination_cards")
    val cards: List<CombinationCard> = emptyList()
)
