package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.model.OneRmFormula
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OneRepMaxCalculatorTest {
    private fun assertClose(
        expected: Double,
        actual: Double?,
        tolerance: Double = 0.01,
    ) {
        requireNotNull(actual)
        assertEquals(true, abs(expected - actual) < tolerance, "expected $expected but was $actual")
    }

    @Test
    fun epley_matchesFormula() {
        // 100kg x 5 -> 100 * (1 + 5/30) = 116.666...
        assertClose(116.6666, OneRepMaxCalculator.estimate(100.0, 5, OneRmFormula.EPLEY))
    }

    @Test
    fun brzycki_matchesFormula() {
        // 100kg x 5 -> 100 * 36 / 32 = 112.5
        assertClose(112.5, OneRepMaxCalculator.estimate(100.0, 5, OneRmFormula.BRZYCKI))
    }

    @Test
    fun lombardi_matchesFormula() {
        // 100kg x 5 -> 100 * 5^0.10
        val expected = 100.0 * 5.0.pow(0.10)
        assertClose(expected, OneRepMaxCalculator.estimate(100.0, 5, OneRmFormula.LOMBARDI))
    }

    @Test
    fun oconner_matchesFormula() {
        // 100kg x 5 -> 100 * (1 + 5/40) = 112.5
        assertClose(112.5, OneRepMaxCalculator.estimate(100.0, 5, OneRmFormula.OCONNER))
    }

    @Test
    fun singleRep_epleyAddsSmallCushion() {
        // 100kg x 1 -> 100 * (1 + 1/30) = 103.33...
        assertClose(103.3333, OneRepMaxCalculator.estimate(100.0, 1, OneRmFormula.EPLEY))
    }

    @Test
    fun repsAboveTwelve_suppressed() {
        assertNull(OneRepMaxCalculator.estimate(100.0, 13, OneRmFormula.EPLEY))
    }

    @Test
    fun repsZeroOrNegative_suppressed() {
        assertNull(OneRepMaxCalculator.estimate(100.0, 0, OneRmFormula.EPLEY))
    }

    @Test
    fun nonPositiveWeight_suppressed() {
        assertNull(OneRepMaxCalculator.estimate(0.0, 5, OneRmFormula.EPLEY))
    }
}
