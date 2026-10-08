package com.santhomach.plantationledger.ui.calculator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel

/**
 * Holds the floating calculator for the whole app session. It is scoped to the activity, so it
 * survives screen changes and rotation, and is reset when the app is closed. Nothing is saved.
 */
class CalculatorViewModel : ViewModel() {

    var state by mutableStateOf(CalculatorState())
        private set

    var isOpen by mutableStateOf(false)
        private set

    var showHistory by mutableStateOf(false)
        private set

    /** Top-left corner of the floating button / panel in pixels; null = default place. */
    var buttonOffset by mutableStateOf<Offset?>(null)
    var panelOffset by mutableStateOf<Offset?>(null)

    fun open() { isOpen = true }
    fun close() { isOpen = false }
    fun toggleHistory() { showHistory = !showHistory }

    fun digit(d: Char) { state = state.digit(d) }
    fun decimalPoint() { state = state.decimalPoint() }
    fun operator(op: Operator) { state = state.operator(op) }
    fun backspace() { state = state.backspace() }
    fun clear() { state = state.clear() }
    fun calculate() { state = state.calculate() }
    fun clearHistory() { state = state.clearHistory() }
}
