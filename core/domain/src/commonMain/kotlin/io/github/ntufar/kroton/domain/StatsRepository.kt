package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseDao
import io.github.ntufar.kroton.database.StatsDao
import io.github.ntufar.kroton.model.MeasurementEntry
import io.github.ntufar.kroton.model.MeasurementType
import io.github.ntufar.kroton.model.MuscleGroup
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private const val SECONDARY_MUSCLE_CREDIT = 0.5

/**
 * Feeds every §5.7 chart from `StatsDao`'s pre-aggregated rows — this repository does the
 * muscle-credit fan-out and week-bucketing in Kotlin, but always over rowsets already reduced by
 * SQL `GROUP BY` to at most one row per day/exercise/muscle, never per completed set (spec §8).
 */
class StatsRepository(
    private val statsDao: StatsDao,
    private val exerciseDao: ExerciseDao,
    private val measurementRepository: MeasurementRepository,
) {
    fun resolveRange(
        preset: StatsRangePreset,
        nowMs: Long,
        customStartMs: Long? = null,
        customEndMs: Long? = null,
    ): DateRangeMs {
        if (preset == StatsRangePreset.CUSTOM && customStartMs != null && customEndMs != null) {
            return DateRangeMs(customStartMs, customEndMs)
        }
        val months =
            when (preset) {
                StatsRangePreset.ONE_MONTH -> 1L
                StatsRangePreset.THREE_MONTHS -> 3L
                StatsRangePreset.SIX_MONTHS -> 6L
                StatsRangePreset.ONE_YEAR -> 12L
                StatsRangePreset.ALL, StatsRangePreset.CUSTOM -> null
            }
        val startMs = months?.let { nowMs - it * AVG_MS_PER_MONTH } ?: 0L
        return DateRangeMs(startMs, nowMs)
    }

    suspend fun consistencyStats(range: DateRangeMs): ConsistencyStats {
        val workouts = statsDao.getWorkoutDatesAndDurations(range.startMs, range.endMs)
        val workoutsPerIsoWeek = workouts.groupingBy { isoWeekKey(it.localDate) }.eachCount()
        val trainedDates = workouts.map { it.localDate }.toSortedSet(compareByDescending { it })
        var streak = 0
        var expected: Int? = null
        for (date in trainedDates) {
            if (expected == null || date == expected) {
                streak++
                expected = stepLocalDate(date, -1)
            } else {
                break
            }
        }
        val avgDuration = if (workouts.isNotEmpty()) workouts.sumOf { it.durationSec } / workouts.size else 0
        return ConsistencyStats(workoutsPerIsoWeek, trainedDates.toSet(), streak, avgDuration)
    }

    suspend fun volumeStats(range: DateRangeMs): VolumeStats {
        val totalByDay =
            statsDao.getVolumeByDay(
                range.startMs,
                range.endMs,
            ).map { DayValue(it.localDate, it.totalVolumeKg) }
        val byDayAndMuscle = statsDao.getVolumeByDayAndPrimaryMuscle(range.startMs, range.endMs)
        val shareByMuscle = byDayAndMuscle.groupBy { it.muscle }.mapValues { (_, rows) -> rows.sumOf { it.volumeKg } }
        return VolumeStats(
            totalVolumeByDay = totalByDay,
            volumeShareByMuscle = shareByMuscle,
            volumeByDayAndMuscle = byDayAndMuscle.map { Triple(it.localDate, it.muscle, it.volumeKg) },
        )
    }

    suspend fun weeklyHardSets(range: DateRangeMs): WeeklyHardSets {
        val rows = statsDao.getSetCountsByExerciseAndDay(range.startMs, range.endMs)
        val exerciseIds = rows.map { it.exerciseId }.distinct()
        val musclesByExercise =
            exerciseIds.associateWith { id ->
                val primary = exerciseDao.getById(id)?.primaryMuscle
                val secondaries = exerciseDao.getSecondaryMuscles(id)
                primary to secondaries
            }
        val result = mutableMapOf<Int, MutableMap<MuscleGroup, Double>>()
        rows.forEach { row ->
            val week = isoWeekKey(row.localDate)
            val (primary, secondaries) = musclesByExercise[row.exerciseId] ?: (null to emptyList())
            val weekMap = result.getOrPut(week) { mutableMapOf() }
            primary?.let { weekMap[it] = (weekMap[it] ?: 0.0) + row.setCount }
            secondaries.forEach { muscle ->
                weekMap[muscle] = (weekMap[muscle] ?: 0.0) + row.setCount * SECONDARY_MUSCLE_CREDIT
            }
        }
        return WeeklyHardSets(result)
    }

    suspend fun strengthSeries(
        exerciseIds: List<Long>,
        range: DateRangeMs,
    ): List<StrengthSeries> =
        exerciseIds.mapNotNull { id ->
            val exercise = exerciseDao.getById(id) ?: return@mapNotNull null
            val points =
                statsDao.get1RmByDay(
                    id,
                    range.startMs,
                    range.endMs,
                ).map { DayValue(it.localDate, it.best1RmKg) }
            StrengthSeries(id, exercise.name, points)
        }

    suspend fun bodyStats(range: DateRangeMs): BodyStats {
        val weight = entriesInRange("body_weight", range)
        val bodyFat = entriesInRange("body_fat_pct", range)
        val circumferenceKeys =
            listOf("neck", "chest", "waist", "hips", "bicep_left", "bicep_right", "thigh_left", "thigh_right")
        val circumferences =
            circumferenceKeys.associateWith {
                entriesInRange(
                    it,
                    range,
                )
            }.filterValues { it.isNotEmpty() }
        val weightValues = weight.map { it.value }
        val ema = BodyCompositionCalculator.exponentialMovingAverage(weightValues)
        val emaByDay = weight.mapIndexed { index, dv -> DayValue(dv.localDate, ema.getOrElse(index) { dv.value }) }
        return BodyStats(weight, emaByDay, bodyFat, circumferences)
    }

    private suspend fun entriesInRange(
        typeKey: String,
        range: DateRangeMs,
    ): List<DayValue> {
        val entries = measurementEntriesForKey(typeKey)
        return entries.filter { it.recordedAt in range.startMs..range.endMs }.map { DayValue(it.localDate, it.value) }
    }

    private suspend fun measurementEntriesForKey(typeKey: String): List<MeasurementEntry> {
        val allTypes = allTypesCache ?: measurementRepository.observeAllTypes().first().also { allTypesCache = it }
        val type = allTypes.firstOrNull { it.key == typeKey } ?: return emptyList()
        return measurementRepository.observeEntries(type.id).first()
    }

    private var allTypesCache: List<MeasurementType>? = null

    fun csvForDayValues(
        rows: List<DayValue>,
        valueHeader: String,
    ): String {
        val header = "date,$valueHeader"
        val body = rows.joinToString("\n") { "${it.localDate},${it.value}" }
        return "$header\n$body"
    }

    private fun isoWeekKey(localDate: Int): Int {
        val date = parseLocalDate(localDate)
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val weekYear = date.get(weekFields.weekBasedYear())
        return weekYear * ISO_WEEK_YEAR_MULTIPLIER + week
    }

    private fun stepLocalDate(
        localDate: Int,
        deltaDays: Long,
    ): Int {
        val date = parseLocalDate(localDate).plusDays(deltaDays)
        return date.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
    }

    private fun parseLocalDate(localDate: Int): LocalDate =
        LocalDate.parse(localDate.toString(), DateTimeFormatter.ofPattern("yyyyMMdd"))

    private companion object {
        const val ISO_WEEK_YEAR_MULTIPLIER = 100
        const val AVG_MS_PER_MONTH = 30L * 24 * 60 * 60 * 1000
    }
}
