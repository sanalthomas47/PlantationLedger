package com.santhomach.plantationledger.data.inventory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

/*
 * Inventory data lives in its own small database (see InventoryDatabase), separate from the expense
 * database, so adding or changing it never touches the expense tables.
 */

/** Equipment, a tool or anything else kept on the estate, with how many there are. */
@Serializable
@Entity(tableName = "inventory_items", indices = [Index("category")])
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val category: String = DEFAULT_CATEGORY,
    val count: Int = 0,
    /** The date it came into use (yyyy-MM-dd); null when not recorded. */
    val inUseSince: String? = null,
    /**
     * Indicative purchase price per item, as a plain decimal (e.g. "1250.50"); null when not
     * recorded. For reference only: it is never used in any expense, balance or efficiency figure.
     */
    val purchasePrice: String? = null,
    val notes: String = "",
    val createdAt: String = LocalDateTime.now().toString(),
    val updatedAt: String = LocalDateTime.now().toString()
)

const val DEFAULT_CATEGORY = "Other"

/** Offered when adding an item; any other category can be typed in. */
val INVENTORY_CATEGORIES = listOf("Equipment", "Tools", "Irrigation", "Spraying", "Safety", DEFAULT_CATEGORY)

/**
 * How long something has been in use, e.g. "2 yrs 3 mo", "5 mo", "12 days" or "Since today".
 * A date in the future reads "Not yet in use".
 */
fun inUseFor(since: LocalDate, today: LocalDate = LocalDate.now()): String {
    if (since.isAfter(today)) return "Not yet in use"
    val period = Period.between(since, today)
    val parts = buildList {
        if (period.years > 0) add("${period.years} ${if (period.years == 1) "yr" else "yrs"}")
        if (period.months > 0) add("${period.months} mo")
    }
    return when {
        parts.isNotEmpty() -> parts.joinToString(" ")
        period.days == 0 -> "Since today"
        else -> "${period.days} ${if (period.days == 1) "day" else "days"}"
    }
}
