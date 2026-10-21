package com.santhomach.plantationledger.data.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.math.BigDecimal

/*
 * Pesticide, fertilizer and foliar purchases ("vendor bills") are bought on credit and settled through
 * the Vendor Ledger. They are a real cost of running the estate, so they stay in expense totals,
 * reports and efficiency figures, but they are kept OUT of every excess / short (cash balance)
 * calculation. Their payments are kept out of those calculations too.
 */

/** Expense types (matched by name, ignoring case) whose purchases are tracked in the Vendor Ledger. */
private val VENDOR_TYPES = listOf("Pesticide", "Fertilizer", "Foliar")

/** True when this line is a pesticide, fertilizer or foliar purchase tracked in the Vendor Ledger. */
fun OtherExpenseEntry.isVendorPurchase(): Boolean =
    VENDOR_TYPES.any { typeName.contains(it, ignoreCase = true) }

/** Sum of this day's pesticide / fertilizer / foliar purchases (paid or not). */
fun DailyExpense.vendorPurchaseTotal(): BigDecimal =
    try {
        Json.decodeFromString(ListSerializer(OtherExpenseEntry.serializer()), otherExpenses)
            .filter { it.isVendorPurchase() }
            .fold(BigDecimal.ZERO) { acc, entry -> acc.add(entry.amount) }
    } catch (e: Exception) {
        BigDecimal.ZERO
    }

/** Expenses that count against the cash balance: labour + overtime + other expenses, minus vendor bills. */
fun DailyExpense.balanceExpenses(): BigDecimal =
    totalLaborCost
        .add(totalOvertimeCost)
        .add(totalOtherExpensesCost)
        .subtract(vendorPurchaseTotal())
