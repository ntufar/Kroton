package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.model.Sex
import kotlin.math.log10

object BodyCompositionCalculator {
    fun navyBodyFatPercent(
        sex: Sex,
        waistCm: Double,
        neckCm: Double,
        heightCm: Double,
        hipsCm: Double? = null,
    ): Double? {
        return when (sex) {
            Sex.MALE -> {
                val delta = waistCm - neckCm
                if (delta <= 0) {
                    null
                } else {
                    495.0 / (1.0324 - 0.19077 * log10(delta) + 0.15456 * log10(heightCm)) - 450.0
                }
            }
            Sex.FEMALE -> {
                val hips = hipsCm
                val delta = if (hips != null) waistCm + hips - neckCm else 0.0
                if (hips == null || delta <= 0) {
                    null
                } else {
                    495.0 / (1.29579 - 0.35004 * log10(delta) + 0.22100 * log10(heightCm)) - 450.0
                }
            }
        }
    }

    fun leanMassKg(
        weightKg: Double,
        bodyFatPercent: Double,
    ): Double = weightKg * (1 - bodyFatPercent / 100.0)

    fun fatMassKg(
        weightKg: Double,
        bodyFatPercent: Double,
    ): Double = weightKg - leanMassKg(weightKg, bodyFatPercent)

    fun ffmi(
        leanKg: Double,
        heightM: Double,
    ): Double = leanKg / (heightM * heightM)

    fun normalisedFfmi(
        ffmi: Double,
        heightM: Double,
    ): Double = ffmi + 6.1 * (1.8 - heightM)
}
