package com.santhomach.plantationledger.data.schedule

/**
 * The starting monthly calendar for a cardamom estate in Kerala's high ranges, filled in once when
 * the schedule database is first created. Every task can be edited or deleted.
 */
object DefaultSchedule {

    val tasksByMonth: Map<Int, List<String>> = mapOf(
        1 to listOf("Harvest final rounds", "Trashing (old leaves and tillers)", "Mulching for dry season", "Start irrigation"),
        2 to listOf("Irrigate every 10-15 days", "Thrips watch / spray", "Shade regulation"),
        3 to listOf("Irrigation", "Thrips control spray", "Nursery preparation"),
        4 to listOf("Irrigation until rains", "Weeding after first showers"),
        5 to listOf("1st fertilizer round (pre-monsoon)", "Weeding", "Prophylactic drench (rhizome rot)", "Start gap filling"),
        6 to listOf("Planting / gap filling", "Clear drainage channels", "Bordeaux 1% spray (capsule rot)", "Trashing"),
        7 to listOf("Disease sprays (capsule / rhizome rot)", "Weeding", "Check drainage"),
        8 to listOf("Harvest round 1", "2nd fertilizer round", "Foliar micronutrients", "Disease spray"),
        9 to listOf("Harvest rounds (every 15-25 days)", "Drying", "Shoot / capsule borer watch"),
        10 to listOf("Harvest rounds", "Post-monsoon fertilizer round", "Weeding", "Foliar spray"),
        11 to listOf("Harvest rounds", "Mulching", "Shade regulation", "Thrips control"),
        12 to listOf("Harvest rounds", "Mulching", "Start irrigation", "Trashing")
    )

    fun tasks(): List<ScheduleTask> = tasksByMonth.flatMap { (month, titles) ->
        titles.mapIndexed { index, title -> ScheduleTask(month = month, title = title, sortOrder = index) }
    }
}
