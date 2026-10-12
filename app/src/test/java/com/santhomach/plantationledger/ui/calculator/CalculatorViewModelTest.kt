package com.santhomach.plantationledger.ui.calculator

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The floating calculator keeps its sum, history and position while it is closed and reopened. */
class CalculatorViewModelTest {

    @Test
    fun closingAndReopeningKeepsTheSumAndHistory() {
        val vm = CalculatorViewModel()
        assertFalse(vm.isOpen)
        vm.open()

        "1250".forEach(vm::digit)
        vm.operator(Operator.ADD)
        "750".forEach(vm::digit)
        vm.calculate()
        vm.operator(Operator.MULTIPLY)
        vm.digit('2')

        vm.close()
        assertFalse(vm.isOpen)
        vm.open()

        assertEquals("2,000 × 2", vm.state.expression)
        assertEquals("4,000", vm.state.preview)
        assertEquals(HistoryEntry("1,250 + 750", "2,000"), vm.state.history.single())
    }

    @Test
    fun keysChangeTheDisplay() {
        val vm = CalculatorViewModel()
        vm.decimalPoint(); vm.digit('5')
        assertEquals("0.5", vm.state.expression)
        vm.backspace()
        assertEquals("0.", vm.state.expression)
        vm.clear()
        assertEquals("", vm.state.expression)

        vm.digit('1'); vm.operator(Operator.ADD); vm.digit('1'); vm.calculate()
        assertEquals(1, vm.state.history.size)
        vm.clearHistory()
        assertTrue(vm.state.history.isEmpty())
    }

    @Test
    fun historyPanelAndPositionAreRemembered() {
        val vm = CalculatorViewModel()
        assertNull(vm.buttonOffset)
        vm.toggleHistory()
        assertTrue(vm.showHistory)
        vm.buttonOffset = Offset(10f, 20f)
        vm.close(); vm.open()
        assertTrue(vm.showHistory)
        assertEquals(Offset(10f, 20f), vm.buttonOffset)
        vm.toggleHistory()
        assertFalse(vm.showHistory)
    }
}
