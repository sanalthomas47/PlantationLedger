package com.santhomach.plantationledger.ui.calculator

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorPositionTest {

    @Test
    fun theButtonStartsStackedAboveThePlusButton() {
        val offset = defaultButtonOffset(IntSize(400, 800), Density(1f))

        // Right edges line up: 16dp margin, like the "+" button.
        assertEquals(400f - 16f - 56f, offset.x, 0f)
        // Bottom of the calculator + 16dp gap = top of the "+" button (800 - 16 - 56).
        assertEquals(800f - 16f - 56f, offset.y + 56f + 16f, 0f)
    }

    @Test
    fun thePositionScalesWithScreenDensity() {
        val offset = defaultButtonOffset(IntSize(1080, 2200), Density(2.75f))

        assertEquals(1080f - 72f * 2.75f, offset.x, 0.01f)
        assertEquals(2200f - 144f * 2.75f, offset.y, 0.01f)
    }

    @Test
    fun clampKeepsTheButtonOnScreen() {
        val size = IntSize(56, 56)
        val area = IntSize(400, 800)

        assertEquals(Offset(0f, 0f), clamp(Offset(-30f, -5f), size, area))
        assertEquals(Offset(344f, 744f), clamp(Offset(900f, 900f), size, area))
        assertEquals(Offset(120f, 300f), clamp(Offset(120f, 300f), size, area))
        // A screen smaller than the button pins it to the corner instead of failing.
        assertEquals(Offset(0f, 0f), clamp(Offset(10f, 10f), size, IntSize(40, 40)))
    }
}
