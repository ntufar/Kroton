package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.model.OneRmFormula
import kotlin.math.pow

object OneRepMaxCalculator {
    private val VALID_REPS = 1..12

    fun estimate(
        weightKg: Double,
        reps: Int,
        formula: OneRmFormula = OneRmFormula.EPLEY,
    ): Double? {
        if (reps !in VALID_REPS || weightKg <= 0.0) return null
        return when (formula) {
            OneRmFormula.EPLEY -> weightKg * (1 + reps / 30.0)
            OneRmFormula.BRZYCKI -> weightKg * 36.0 / (37.0 - reps)
            OneRmFormula.LOMBARDI -> weightKg * reps.toDouble().pow(0.10)
            OneRmFormula.OCONNER -> weightKg * (1 + reps / 40.0)
        }
    }
}
