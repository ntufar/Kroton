package io.github.ntufar.kroton.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyCompositionCalculatorTest {
    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.01,
    ) {
        assertTrue(abs(expected - actual) < tolerance, "expected $expected but was $actual")
    }

    @Test
    fun bmi_matchesFormula() {
        // 80kg at 1.80m -> 80 / 1.8^2 = 24.69...
        assertClose(24.6914, BodyCompositionCalculator.bmi(80.0, 1.80))
    }

    @Test
    fun ema_seedsFromFirstValueThenSmooths() {
        val raw = listOf(80.0, 81.0, 79.0, 80.0)
        val ema = BodyCompositionCalculator.exponentialMovingAverage(raw, periodDays = 7)
        assertEquals(raw.size, ema.size)
        assertEquals(80.0, ema.first())
        // alpha = 2 / (7+1) = 0.25 -> ema[1] = 0.25*81 + 0.75*80 = 80.25
        assertClose(80.25, ema[1])
    }

    @Test
    fun ema_emptyInput_returnsEmpty() {
        assertEquals(emptyList(), BodyCompositionCalculator.exponentialMovingAverage(emptyList()))
    }
}
