package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.model.MuscleGroup

enum class StatsRangePreset { ONE_MONTH, THREE_MONTHS, SIX_MONTHS, ONE_YEAR, ALL, CUSTOM }

data class DateRangeMs(val startMs: Long, val endMs: Long)

data class ConsistencyStats(
    val workoutsPerIsoWeek: Map<Int, Int>,
    val trainedLocalDates: Set<Int>,
    val currentStreakDays: Int,
    val averageDurationSec: Int,
)

data class DayValue(val localDate: Int, val value: Double)

data class VolumeStats(
    val totalVolumeByDay: List<DayValue>,
    val volumeShareByMuscle: Map<MuscleGroup, Double>,
    val volumeByDayAndMuscle: List<Triple<Int, MuscleGroup, Double>>,
)

/** Weekly hard sets per muscle (spec §4.3): fractional secondary-muscle credit applied over the
 * pre-aggregated exercise+day set counts, not over raw sets. */
data class WeeklyHardSets(val setsByIsoWeekAndMuscle: Map<Int, Map<MuscleGroup, Double>>)

data class StrengthSeries(val exerciseId: Long, val exerciseName: String, val estimated1RmByDay: List<DayValue>)

data class BodyStats(
    val weightByDay: List<DayValue>,
    val weightEmaByDay: List<DayValue>,
    val bodyFatByDay: List<DayValue>,
    val circumferencesByTypeKey: Map<String, List<DayValue>>,
)
