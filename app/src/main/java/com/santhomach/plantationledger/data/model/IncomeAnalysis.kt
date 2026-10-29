package com.santhomach.plantationledger.data.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/*
 * Income Breakdown (income by type, with weights and every sale) and Income vs Expense (each crop's
 * income against the expenses that belong to it).
 */

// -------------------------------------------------------------------------------------------------
// Income Breakdown
// -------------------------------------------------------------------------------------------------

/** One sale: an income line on a day. */
data class IncomeSale(val date: String, val amount: BigDecimal, val weight: Double)

data class IncomePeriod(val amount: BigDecimal = BigDecimal.ZERO, val weight: Double = 0.0) {
    /** Average price per unit of weight, when a weight was recorded. */
    val pricePerKg: BigDecimal?
        get() = if (weight > 0) amount.divide(BigDecimal.valueOf(weight), 2, RoundingMode.HALF_UP) else null

    operator fun plus(sale: IncomeSale) = IncomePeriod(amount.add(sale.amount), weight + sale.weight)
}

data class IncomeTypeSummary(
    val typeName: String,
    val thisWeek: IncomePeriod,
    val thisYear: IncomePeriod,
    val allTime: IncomePeriod,
    /** Newest first. */
    val sales: List<IncomeSale>
)

private val json = Json { ignoreUnknownKeys = true }

private fun incomeLines(expense: DailyExpense): List<IncomeEntry> = try {
    if (expense.incomeEntries.isBlank() || expense.incomeEntries == "[]") emptyList()
    else json.decodeFromString(ListSerializer(IncomeEntry.serializer()), expense.incomeEntries)
} catch (e: Exception) {
    emptyList()
}

/** Income by type for this week (Monday-Sunday), this year and all time; largest all-time first. */
fun buildIncomeBreakdown(expenses: List<DailyExpense>, today: LocalDate = LocalDate.now()): List<IncomeTypeSummary> {
    val weekStart = weekStartOf(today).toString()
    val weekEnd = weekStartOf(today).plusDays(6).toString()
    val yearStart = today.withDayOfYear(1).toString()
    val yearEnd = today.toString()

    val salesByType = LinkedHashMap<String, MutableList<IncomeSale>>()
    expenses.forEach { day ->
        incomeLines(day).forEach { line ->
            salesByType.getOrPut(line.typeName.trim()) { mutableListOf() } += IncomeSale(day.date, line.amount, line.weight)
        }
    }
    return salesByType.map { (type, sales) ->
        IncomeTypeSummary(
            typeName = type,
            thisWeek = sales.filter { it.date in weekStart..weekEnd }.fold(IncomePeriod()) { p, s -> p + s },
            thisYear = sales.filter { it.date in yearStart..yearEnd }.fold(IncomePeriod()) { p, s -> p + s },
            allTime = sales.fold(IncomePeriod()) { p, s -> p + s },
            sales = sales.sortedByDescending { it.date }
        )
    }.sortedWith(compareByDescending<IncomeTypeSummary> { it.allTime.amount }.thenBy { it.typeName })
}

/** One bar of the income chart. */
data class IncomeBarData(
    val typeName: String,
    val period: IncomePeriod,
    /** Percent of the period's income, to one decimal place. */
    val share: BigDecimal,
    /** Bar length relative to the largest bar (0-1). */
    val fraction: Float
)

/** The income chart's bars for this year or all time: types with income only, highest to lowest. */
fun incomeChartBars(types: List<IncomeTypeSummary>, allTime: Boolean): List<IncomeBarData> {
    val bars = types
        .map { it.typeName to if (allTime) it.allTime else it.thisYear }
        .filter { it.second.amount.signum() > 0 }
        .sortedByDescending { it.second.amount }
    val total = bars.fold(BigDecimal.ZERO) { acc, bar -> acc.add(bar.second.amount) }
    val max = bars.firstOrNull()?.second?.amount ?: return emptyList()
    return bars.map { (name, period) ->
        IncomeBarData(
            typeName = name,
            period = period,
            share = period.amount.multiply(BigDecimal(100)).divide(total, 1, RoundingMode.HALF_UP),
            fraction = period.amount.divide(max, 4, RoundingMode.HALF_UP).toFloat()
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Income vs Expense
// -------------------------------------------------------------------------------------------------

/** Where an expense amount came from, for the "what was matched" list. */
data class ExpenseSource(
    /** e.g. "Pepper Drying" (a labour task) or "Coconut Oil related / Coconut harvesting". */
    val label: String,
    val isLabour: Boolean,
    val amount: BigDecimal,
    /** False when no crop was named and the amount was given to [DEFAULT_CROP]. */
    val matched: Boolean
)

data class CropEfficiency(
    /** e.g. "Pepper". */
    val crop: String,
    /** The income types in this group, e.g. Pepper Sales and Pepper Cheeru. */
    val incomeTypes: List<String>,
    val income: BigDecimal,
    val expense: BigDecimal,
    /** Largest first. */
    val sources: List<ExpenseSource>
) {
    val net: BigDecimal get() = income.subtract(expense)

    /** Income earned per ₹1 spent; null when nothing was spent. */
    val returnPerRupee: BigDecimal?
        get() = if (expense.signum() > 0) income.divide(expense, 2, RoundingMode.HALF_UP) else null
}

data class EfficiencyReport(
    val crops: List<CropEfficiency>,
    /** Capital expenses, left out of every crop. */
    val excludedCapital: BigDecimal
)

/** Expenses that name no crop belong to cardamom, the estate's main crop. */
const val DEFAULT_CROP = "Cardamom"

/** Income types like "Other Income" are their own group and never claim expenses. */
private val GENERIC_WORDS = setOf("other", "others", "misc", "miscellaneous", "income", "sales", "sale")

/** The crop an income type belongs to: its first word, e.g. "Pepper Sales" -> "Pepper". */
fun cropOf(incomeType: String): String {
    val first = Regex("[A-Za-z]+").find(incomeType)?.value ?: return incomeType.trim()
    return if (first.lowercase() in GENERIC_WORDS) incomeType.trim()
    else first.lowercase().replaceFirstChar { it.uppercase() }
}

private fun isGeneric(crop: String) = Regex("[A-Za-z]+").find(crop)?.value?.lowercase() in GENERIC_WORDS

private fun isCapital(typeName: String) = typeName.contains("capital", ignoreCase = true)

/**
 * Each crop's income against its expenses for days in [from]..[to] (null = no limit).
 *
 * An expense belongs to a crop when its subtype, type or labour task contains the crop's name as a
 * word (e.g. "Pepper Drying" -> Pepper); anything naming no crop goes to [DEFAULT_CROP]. Capital
 * expenses are left out. Labour includes its overtime; overtime entered for the whole day has no
 * task and goes to [DEFAULT_CROP]. Pesticide, fertilizer and foliar bills are counted as expenses.
 *
 * [knownIncomeTypes] (e.g. the income type master list) adds crops that have no sales yet, so
 * their expenses are still matched to them.
 */
fun buildEfficiency(
    expenses: List<DailyExpense>,
    knownIncomeTypes: List<String> = emptyList(),
    from: LocalDate? = null,
    to: LocalDate? = null
): EfficiencyReport {
    val inRange = expenses.filter { day ->
        (from == null || day.date >= from.toString()) && (to == null || day.date <= to.toString())
    }

    // Crops come from every income type ever used or set up, so matching does not depend on the period.
    val typesByCrop = LinkedHashMap<String, MutableSet<String>>()
    (knownIncomeTypes + expenses.flatMap { day -> incomeLines(day).map { it.typeName } })
        .map { it.trim() }.filter { it.isNotEmpty() }
        .forEach { type -> typesByCrop.getOrPut(cropOf(type)) { sortedSetOf() } += type }
    typesByCrop.getOrPut(DEFAULT_CROP) { sortedSetOf() }

    // Longest names first, so a more specific crop wins (e.g. "Nutmeg" over a shorter prefix).
    val matchable = typesByCrop.keys.filterNot(::isGeneric).sortedByDescending { it.length }
    val patterns = matchable.associateWith { Regex("\\b" + Regex.escape(it), RegexOption.IGNORE_CASE) }
    fun cropFor(vararg names: String?): String? = names.asSequence().filterNotNull().filter { it.isNotBlank() }
        .firstNotNullOfOrNull { name -> matchable.firstOrNull { patterns.getValue(it).containsMatchIn(name) } }

    val income = HashMap<String, BigDecimal>()
    val sources = HashMap<String, MutableMap<Pair<String, Boolean>, Pair<BigDecimal, Boolean>>>()
    var capital = BigDecimal.ZERO

    fun addExpense(crop: String?, label: String, isLabour: Boolean, amount: BigDecimal) {
        if (amount.signum() == 0) return
        val target = crop ?: DEFAULT_CROP
        val bucket = sources.getOrPut(target) { LinkedHashMap() }
        val key = label to isLabour
        val (sum, matched) = bucket[key] ?: (BigDecimal.ZERO to (crop != null))
        bucket[key] = sum.add(amount) to matched
    }

    inRange.forEach { day ->
        incomeLines(day).forEach { line ->
            val crop = cropOf(line.typeName.trim())
            income[crop] = (income[crop] ?: BigDecimal.ZERO).add(line.amount)
        }

        parseLines(day.otherExpenses, OtherExpenseEntry.serializer()).forEach { line ->
            if (isCapital(line.typeName)) {
                capital = capital.add(line.amount)
                return@forEach
            }
            val subtype = line.subtypeName?.trim().orEmpty()
            val label = if (subtype.isEmpty()) line.typeName.trim() else "${line.typeName.trim()} / $subtype"
            addExpense(cropFor(subtype, line.typeName), label, isLabour = false, amount = line.amount)
        }

        var wages = BigDecimal.ZERO
        var overtime = BigDecimal.ZERO
        parseLines(day.workerGroups, WorkerGroupEntry.serializer()).forEach { group ->
            val groupWages = group.wagePerDay.multiply(BigDecimal(group.count))
            val groupOvertime = group.calculateTotalGroupCost().subtract(groupWages)
            wages = wages.add(groupWages)
            overtime = overtime.add(groupOvertime)
            val task = group.taskPerformed.trim().ifEmpty { "No task recorded" }
            addExpense(cropFor(group.taskPerformed), task, isLabour = true, amount = groupWages.add(groupOvertime))
        }
        addExpense(null, "No task recorded", isLabour = true, amount = day.totalLaborCost.subtract(wages))
        addExpense(null, "Overtime (no task)", isLabour = true, amount = day.totalOvertimeCost.subtract(overtime))
    }

    val crops = typesByCrop.map { (crop, types) ->
        val list = sources[crop].orEmpty().map { (key, value) ->
            ExpenseSource(label = key.first, isLabour = key.second, amount = value.first, matched = value.second)
        }.sortedWith(compareByDescending<ExpenseSource> { it.matched }.thenByDescending { it.amount })
        CropEfficiency(
            crop = crop,
            incomeTypes = types.toList(),
            income = income[crop] ?: BigDecimal.ZERO,
            expense = list.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.amount) },
            sources = list
        )
    }
        .filter { it.income.signum() != 0 || it.expense.signum() != 0 }
        .sortedWith(compareByDescending<CropEfficiency> { it.income }.thenByDescending { it.expense }.thenBy { it.crop })

    return EfficiencyReport(crops, capital)
}

private fun <T> parseLines(text: String, serializer: kotlinx.serialization.KSerializer<T>): List<T> = try {
    if (text.isBlank() || text == "[]") emptyList() else json.decodeFromString(ListSerializer(serializer), text)
} catch (e: Exception) {
    emptyList()
}
