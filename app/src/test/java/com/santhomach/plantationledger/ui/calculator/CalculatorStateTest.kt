package com.santhomach.plantationledger.ui.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorStateTest {

    /** Presses keys given as text, e.g. "8*575+2*500=". */
    private fun press(keys: String, start: CalculatorState = CalculatorState()): CalculatorState =
        keys.fold(start) { state, key ->
            when (key) {
                in '0'..'9' -> state.digit(key)
                '.' -> state.decimalPoint()
                '+' -> state.operator(Operator.ADD)
                '-' -> state.operator(Operator.SUBTRACT)
                '*' -> state.operator(Operator.MULTIPLY)
                '/' -> state.operator(Operator.DIVIDE)
                '=' -> state.calculate()
                '<' -> state.backspace()
                'C' -> state.clear()
                else -> error("unknown key $key")
            }
        }

    private fun result(state: CalculatorState) = state.tokens.single() as String

    @Test
    fun addsAndSubtracts() {
        assertEquals("1255", result(press("1000+605-350=")))
        assertEquals("-150", result(press("100-250=")))
    }

    @Test
    fun multipliesAndDividesBeforeAddingAndSubtracting() {
        assertEquals("5600", result(press("8*575+2*500=")))      // 4,600 + 1,000
        assertEquals("70", result(press("100-60/2=")))           // 100 - 30
    }

    @Test
    fun divisionKeepsDecimalsWithoutRoundingErrors() {
        assertEquals("1.5", result(press("3/2=")))
        assertEquals("0.3333333333", result(press("1/3=")))
        assertEquals("0.3", result(press("0.1+0.2=")))
    }

    @Test
    fun divideByZeroShowsAnErrorAndKeepsTheInput() {
        val state = press("5/0=")
        assertEquals("Can't divide by zero", state.error)
        assertEquals("5 ÷ 0", state.expression)
        assertTrue(state.history.isEmpty())
        // Typing again clears the error.
        assertNull(press("<2", state).error)
    }

    @Test
    fun showsALiveResultWhileTyping() {
        assertEquals("4,600", press("8*575").preview)
        assertEquals("4,600", press("8*575+").preview)          // trailing operator ignored
        assertNull(CalculatorState().preview)
    }

    @Test
    fun displayUsesIndianDigitGrouping() {
        assertEquals("1,23,456", CalculatorState.formatNumber("123456"))
        assertEquals("12,34,56,789.5", CalculatorState.formatNumber("123456789.5"))
        assertEquals("−4,600", CalculatorState.formatNumber("-4600"))
        assertEquals("999", CalculatorState.formatNumber("999"))
        assertEquals("37,310 − 37,915", press("37310-37915").expression)
    }

    @Test
    fun pressingAnotherOperatorReplacesTheLastOne() {
        assertEquals("5 −", press("5+-").expression)
        assertEquals("0 +", press("+").expression)               // starts from 0
    }

    @Test
    fun afterEqualsAnOperatorContinuesAndADigitStartsAgain() {
        val done = press("100+50=")
        assertTrue(done.justEvaluated)
        assertEquals("150 − 20", press("-20", done).expression)
        assertEquals("7", press("7", done).expression)
    }

    @Test
    fun decimalPointAndBackspace() {
        assertEquals("0.5", press(".5").expression)
        assertEquals("1.25", press("1..25").expression)          // a second point is ignored
        assertEquals("12", press("123<").expression)
        assertEquals("12", press("12+<").expression)
        assertEquals("", press("150+20=<").expression)            // backspace after a result clears it
    }

    @Test
    fun leadingZeroIsReplacedAndVeryLongNumbersAreCapped() {
        assertEquals("7", press("07").expression.replace(",", ""))
        assertEquals(CalculatorState.MAX_DIGITS, press("1".repeat(20)).expression.count { it.isDigit() })
    }

    @Test
    fun historyKeepsTheLastTwentyNewestFirstAndSurvivesClear() {
        var state = CalculatorState()
        repeat(25) { i -> state = press("C${i}+1=", state) }
        assertEquals(CalculatorState.MAX_HISTORY, state.history.size)
        assertEquals(HistoryEntry("24 + 1", "25"), state.history.first())

        state = state.clear()
        assertEquals(20, state.history.size)
        assertFalse(state.clearHistory().history.isNotEmpty())
    }

    @Test
    fun equalsWithNothingToWorkOutDoesNotAddHistory() {
        assertTrue(press("5=").history.isEmpty())
        assertTrue(press("5+=").history.isEmpty())
        assertEquals("5", press("5+=").expression)
    }
}
