package com.santhomach.plantationledger.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.santhomach.plantationledger.data.serialization.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Expense type master (e.g. Fertilizer, Pesticide, Transport ...).
 */
@Serializable
@Entity(tableName = "expense_types")
data class ExpenseType(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val typeName: String,
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * Sub-type of an expense type. The parent type is embedded with a `parent_` column prefix.
 */
@Serializable
@Entity(tableName = "expense_subtypes")
data class ExpenseSubtype(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val typeName: String,
    @Embedded(prefix = "parent_")
    val parentTypeName: ExpenseType,
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * Income type master (e.g. Cardamom sale, Pepper sale ...).
 */
@Serializable
@Entity(tableName = "income_types")
data class IncomeType(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val typeName: String,
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * Worker type master (Malayali Male, Bengali Female ...) with default wage rates.
 */
@Serializable
@Entity(tableName = "worker_types")
data class WorkerType(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val workerTypeName: String,
    @Serializable(with = BigDecimalSerializer::class)
    val dailyBasicWage: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val dailyOvertimeRate: BigDecimal = BigDecimal.ZERO,
    val isActive: Boolean = true,
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * Permanent (salaried) worker such as the estate manager.
 */
@Serializable
@Entity(tableName = "permanent_workers")
data class PermanentWorker(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val role: String,
    @Serializable(with = BigDecimalSerializer::class)
    val dailyBasicWage: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val dailyOvertimeRate: BigDecimal = BigDecimal.ZERO,
    val isActive: Boolean = true,
    val hireDate: String = LocalDate.now().toString(),
    val notes: String = "",
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * Work task master (Weeding, Harvesting, Spraying ...).
 */
@Serializable
@Entity(tableName = "work_tasks")
data class WorkTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val taskName: String,
    val description: String = "",
    val isActive: Boolean = true,
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * A group of workers of one type that worked on a given day.
 * Stored as JSON inside [DailyExpense.workerGroups].
 */
@Serializable
data class WorkerGroupEntry(
    val workerTypeId: Int,
    val workerTypeName: String,
    val count: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val wagePerDay: BigDecimal,
    val overtimeHours: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val overtimeWagePerHour: BigDecimal = BigDecimal.ZERO,
    val taskPerformed: String,
    val comments: String = "",
    val addedAt: String = LocalDateTime.now().toString()
) {
    fun calculateTotalGroupCost(): BigDecimal {
        val basicCost = wagePerDay.multiply(BigDecimal.valueOf(count.toLong()))
        val overtimeCost = overtimeWagePerHour
            .multiply(BigDecimal.valueOf(overtimeHours.toLong()))
            .multiply(BigDecimal.valueOf(count.toLong()))
        return basicCost.add(overtimeCost)
    }
}

/**
 * A single "other expense" line (materials, transport, vendor purchases ...).
 * Stored as JSON inside [DailyExpense.otherExpenses].
 */
@Serializable
data class OtherExpenseEntry(
    val expenseTypeId: Int,
    val typeName: String,
    val subtypeName: String? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val quantity: Double = 1.0,
    val notes: String = "",
    val addedAt: String = LocalDateTime.now().toString(),
    val receiptImagePath: String? = null,
    val isPaid: Boolean = false
)

/**
 * A single income line (e.g. cardamom sold by weight).
 * Stored as JSON inside [DailyExpense.incomeEntries].
 */
@Serializable
data class IncomeEntry(
    val incomeTypeId: Int,
    val typeName: String,
    val weight: Double = 0.0,
    @Serializable(with = BigDecimalSerializer::class)
    val pricePerKilo: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val transportationCharge: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val notes: String = "",
    val addedAt: String = LocalDateTime.now().toString()
)

/**
 * An advance given to a worker / person on a given day.
 * Stored as JSON inside [DailyExpense.advanceEntries].
 */
@Serializable
data class AdvanceEntry(
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val reason: String = "",
    val recipientName: String = "",
    val addedAt: String = LocalDateTime.now().toString()
)

/**
 * One day of plantation activity: labour, overtime, other expenses, income and advances.
 */
@Serializable
@Entity(
    tableName = "daily_expenses",
    foreignKeys = [
        ForeignKey(
            entity = PermanentWorker::class,
            parentColumns = ["id"],
            childColumns = ["managerId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["managerId"]),
        Index(value = ["date", "managerId"])
    ]
)
data class DailyExpense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String, // ISO yyyy-MM-dd

    // Legacy per-type worker counts (kept for backward compatibility with old data)
    val malayaliMaleCount: Int = 0,
    val bengaliMaleCount: Int = 0,
    val malayaliFemaleCount: Int = 0,
    val bengaliFemaleCount: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val malayaliMaleWagePerDay: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val bengaliMaleWagePerDay: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val malayaliFemaleWagePerDay: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val bengaliFemaleWagePerDay: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val totalLaborCost: BigDecimal = BigDecimal.ZERO,

    // Overtime
    val overtimeHours: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val extraOvertimeAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val totalOvertimeCost: BigDecimal = BigDecimal.ZERO,

    // Other expenses (JSON list of OtherExpenseEntry)
    val otherExpenses: String = "[]",
    @Serializable(with = BigDecimalSerializer::class)
    val totalOtherExpensesCost: BigDecimal = BigDecimal.ZERO,

    // Income (JSON list of IncomeEntry)
    val incomeEntries: String = "[]",
    @Serializable(with = BigDecimalSerializer::class)
    val totalIncome: BigDecimal = BigDecimal.ZERO,

    // Excess balance / advances
    @Serializable(with = BigDecimalSerializer::class)
    val excessBalance: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val advanceAmount: BigDecimal = BigDecimal.ZERO,
    val advanceReason: String = "",

    // Worker groups (JSON list of WorkerGroupEntry)
    val workerGroups: String = "[]",
    // Advances (JSON list of AdvanceEntry)
    val advanceEntries: String = "[]",

    @Serializable(with = BigDecimalSerializer::class)
    val weeklyPaymentDone: BigDecimal = BigDecimal.ZERO,

    val managerId: Int? = null,
    val comments: String = "",
    val createdAt: String = LocalDateTime.now().toString(),
    val updatedAt: String = LocalDateTime.now().toString(),
    val createdBy: String = "system",
    val expenseAdditionType: String = "manual"
) {
    /**
     * Income minus (labour + overtime + other expenses + advances + weekly payment).
     */
    fun calculateNetAmount(): BigDecimal {
        val totalExpenses = totalLaborCost
            .add(totalOvertimeCost)
            .add(totalOtherExpensesCost)
            .add(advanceAmount)
            .add(weeklyPaymentDone)
        return totalIncome.subtract(totalExpenses)
    }
}

/**
 * Weekly settlement with the labour contractor.
 */
@Serializable
@Entity(
    tableName = "weekly_settlements",
    indices = [
        Index(value = ["settlementDate"]),
        Index(value = ["weekStartDate", "weekEndDate"])
    ]
)
data class WeeklySettlement(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val settlementDate: String,
    val weekStartDate: String,
    val weekEndDate: String,
    val totalWorkersMonThur: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val totalLaborCostMonThur: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val totalTransportMonThur: BigDecimal = BigDecimal.ZERO,
    val totalWorkersFriSat: Int = 0,
    @Serializable(with = BigDecimalSerializer::class)
    val totalLaborCostFriSat: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val differenceAmount: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val carryoverFromPreviousWeek: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val amountToPay: BigDecimal = BigDecimal.ZERO,
    @Serializable(with = BigDecimalSerializer::class)
    val amountPaid: BigDecimal = BigDecimal.ZERO,
    val paymentDate: String? = null,
    val paymentMethod: String = "",
    val paymentNotes: String = "",
    @Serializable(with = BigDecimalSerializer::class)
    val carryoverToNextWeek: BigDecimal = BigDecimal.ZERO,
    val createdAt: String = LocalDateTime.now().toString(),
    val updatedAt: String = LocalDateTime.now().toString()
)

/**
 * Excess balance held by / owed to the manager (CREDIT or DEBIT).
 */
@Serializable
@Entity(
    tableName = "excess_balances",
    indices = [
        Index(value = ["date"]),
        Index(value = ["managerId"])
    ]
)
data class ExcessBalance(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val direction: String, // "CREDIT" or "DEBIT"
    val reason: String,
    val managerId: Int? = null,
    val notes: String = "",
    val createdAt: String = LocalDateTime.now().toString(),
    val settledAt: String? = null,
    val isSettled: Boolean = false,
    val createdBy: String = "system"
)

/**
 * Payment made to a permanent worker (monthly salary, advance, bonus ...).
 */
@Serializable
@Entity(
    tableName = "worker_payments",
    indices = [
        Index(value = ["workerId"]),
        Index(value = ["paymentDate"])
    ]
)
data class WorkerPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val workerId: Int,
    val workerName: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val paymentDate: String,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val paymentType: String = "MONTHLY",
    val notes: String = "",
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * Funds received for a week (from the owner) and the payment made out of it.
 */
@Serializable
@Entity(
    tableName = "weekly_funds",
    indices = [Index(value = ["weekStartDate"])]
)
data class WeeklyFunds(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val weekStartDate: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amountReceived: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val paymentMade: BigDecimal = BigDecimal.ZERO,
    val notes: String = "",
    val createdAt: String = LocalDateTime.now().toString()
)

/**
 * Payment made to a vendor, optionally linked back to the daily expense line it settles.
 */
@Serializable
@Entity(
    tableName = "vendor_payments",
    indices = [Index(value = ["date"])]
)
data class VendorPayment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal,
    val vendorName: String = "",
    val notes: String = "",
    val createdAt: String = LocalDateTime.now().toString(),
    val sourceExpenseDate: String? = null,
    val sourceTypeName: String? = null,
    val sourceSubtypeName: String? = null
)
