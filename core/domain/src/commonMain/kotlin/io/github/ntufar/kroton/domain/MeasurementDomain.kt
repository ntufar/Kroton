package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.model.MeasurementType

data class MeasurementSummary(
    val type: MeasurementType,
    val latestValue: Double?,
    val deltaSinceLast: Double?,
    val sparkline: List<Double>,
)

data class DerivedMetrics(
    val navyBodyFatPercent: Double?,
    val leanMassKg: Double?,
    val fatMassKg: Double?,
    val ffmi: Double?,
    val normalisedFfmi: Double?,
    val bmi: Double?,
    val bodyweightEma: Double?,
    val inputsUsed: Map<String, Double>,
)
