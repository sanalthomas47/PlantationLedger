package com.santhomach.plantationledger.ui.calculator

import java.math.BigDecimal
import java.math.RoundingMode

/** The four operators, with the symbols shown on the keypad. */
enum class Operator(val symbol: String) {
    ADD("+"), SUBTRACT("−"), MULTIPLY("×"), DIVIDE("÷");

    val highPrecedence: Boolean get() = this == MULTIPLY || this == DIVIDE
}

/** One finished calculation in the session history. */
data class HistoryEntry(val expression: String, val result: String)

/**
 * Immutable state of the floating calculator. Every key press returns a new state, which keeps
 * the logic easy to test. × and ÷ are worked out before + and −, like a normal calculator.
 *
 * Numbers are kept as the text the user typed (e.g. "12.", "0.5") so the display shows exactly
 * what was entered; [BigDecimal] is used for the arithmetic so there are no rounding surprises
 * with money.
 */
data class CalculatorState(
    /** Alternating numbers and operators, e.g. ["8", ×, "575", +, "1000"]. */
    val tokens: List<Any> = emptyList(),
    /** True right after "=", so the next digit starts a new calculation. */
    val justEvaluated: Boolean = false,
    val error: String? = null,
    /** Newest first, at most [MAX_HISTORY] entries, kept only while the app is running. */
    val history: List<HistoryEntry> = emptyList()
) {
    /** The calculation as typed, e.g. "8 × 575 + 1,000". */
    val expression: String
        get() = tokens.joinToString(" ") { if (it is Operator) it.symbol else formatNumber(it as String) }

    /** Live result of what has been typed so far (a trailing operator is ignored), or null. */
    val preview: String?
        get() = evaluate(tokens.dropLastWhile { it is Operator })?.let { formatNumber(it.toPlainString()) }

    fun digit(d: Char): CalculatorState {
        require(d in '0'..'9')
        if (justEvaluated) return copy(tokens = listOf(d.toString()), justEvaluated = false, error = null)
        val last = tokens.lastOrNull()
        return when {
            last is String -> {
                if (last.filter { it.isDigit() }.length >= MAX_DIGITS) return this
                val updated = if (last == "0") d.toString() else last + d
                copy(tokens = tokens.dropLast(1) + updated, error = null)
            }
            else -> copy(tokens = tokens + d.toString(), error = null)
        }
    }

    fun decimalPoint(): CalculatorState {
        if (justEvaluated) return copy(tokens = listOf("0."), justEvaluated = false, error = null)
        val last = tokens.lastOrNull()
        return when {
            last is String && '.' in last -> this
            last is String -> copy(tokens = tokens.dropLast(1) + "$last.", error = null)
            else -> copy(tokens = tokens + "0.", error = null)
        }
    }

    fun operator(op: Operator): CalculatorState {
        val base = when {
            tokens.isEmpty() -> listOf<Any>("0")
            else -> tokens.map { if (it is String && it.endsWith(".")) it.dropLast(1) else it }
        }
        val next = if (base.last() is Operator) base.dropLast(1) + op else base + op
        return copy(tokens = next, justEvaluated = false, error = null)
    }

    fun backspace(): CalculatorState {
        if (justEvaluated) return copy(tokens = emptyList(), justEvaluated = false, error = null)
        val last = tokens.lastOrNull() ?: return copy(error = null)
        val next = when {
            last is String && last.length > 1 -> tokens.dropLast(1) + last.dropLast(1)
            else -> tokens.dropLast(1)
        }
        return copy(tokens = next, error = null)
    }

    /** Clears the current calculation; the history is kept. */
    fun clear(): CalculatorState = copy(tokens = emptyList(), justEvaluated = false, error = null)

    fun clearHistory(): CalculatorState = copy(history = emptyList())

    /** The "=" key. */
    fun calculate(): CalculatorState {
        val complete = tokens.dropLastWhile { it is Operator }
        if (complete.size < 3) return copy(tokens = complete)   // nothing to work out
        val result = try {
            evaluate(complete) ?: return this
        } catch (e: ArithmeticException) {
            return copy(error = "Can't divide by zero")
        }
        val resultText = result.toPlainString()
        val entry = HistoryEntry(copy(tokens = complete).expression, formatNumber(resultText))
        return copy(
            tokens = listOf(resultText),
            justEvaluated = true,
            error = null,
            history = (listOf(entry) + history).take(MAX_HISTORY)
        )
    }

    companion object {
        const val MAX_HISTORY = 20
        const val MAX_DIGITS = 15
        private const val DIVISION_SCALE = 10

        /**
         * Works out [tokens] with × and ÷ first. Returns null for an incomplete expression.
         * Throws [ArithmeticException] on division by zero.
         */
        fun evaluate(tokens: List<Any>): BigDecimal? {
            if (tokens.isEmpty() || tokens.size % 2 == 0) return null
            val numbers = tokens.filterIsInstance<String>().map { it.removeSuffix(".").toBigDecimalOrNull() ?: return null }
            val ops = tokens.filterIsInstance<Operator>()
            if (numbers.size != ops.size + 1) return null

            // First pass: × and ÷
            val terms = mutableListOf(numbers[0])
            val lowOps = mutableListOf<Operator>()
            ops.forEachIndexed { i, op ->
                val n = numbers[i + 1]
                when (op) {
                    Operator.MULTIPLY -> terms[terms.lastIndex] = terms.last().multiply(n)
                    Operator.DIVIDE -> {
                        if (n.signum() == 0) throw ArithmeticException("Division by zero")
                        terms[terms.lastIndex] = terms.last().divide(n, DIVISION_SCALE, RoundingMode.HALF_UP)
                    }
                    else -> { terms.add(n); lowOps.add(op) }
                }
            }
            // Second pass: + and −
            var total = terms[0]
            lowOps.forEachIndexed { i, op ->
                total = if (op == Operator.ADD) total.add(terms[i + 1]) else total.subtract(terms[i + 1])
            }
            val stripped = total.stripTrailingZeros()
            return if (stripped.scale() < 0) stripped.setScale(0) else stripped
        }

        /** Indian digit grouping (1,23,456.5); keeps what the user typed after the decimal point. */
        fun formatNumber(text: String): String {
            val negative = text.startsWith("-")
            val body = text.removePrefix("-")
            val intPart = body.substringBefore('.')
            val fraction = if ('.' in body) "." + body.substringAfter('.') else ""
            val grouped = if (intPart.length <= 3) intPart else {
                val last3 = intPart.takeLast(3)
                val rest = intPart.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
                "$rest,$last3"
            }
            return (if (negative) "−" else "") + grouped + fraction
        }
    }
}
