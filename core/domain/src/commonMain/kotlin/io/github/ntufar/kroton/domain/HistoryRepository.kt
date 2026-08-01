package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.WorkoutDao
import io.github.ntufar.kroton.database.WorkoutEntity
import io.github.ntufar.kroton.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HistoryHeaderStats(
    val workoutsThisWeek: Int,
    val workoutsThisMonth: Int,
    val currentStreakDays: Int,
    val totalVolumeKg: Double,
)

/** Owns the finished-workout list/calendar views and read-only detail lookups for History
 * (spec §5.4). Edit-mode mutations reuse `WorkoutRepository`'s set/exercise methods directly —
 * none of them gate on `is_in_progress`, so a finished workout is editable the same way an
 * active one is. */
class HistoryRepository(private val workoutDao: WorkoutDao) {
    fun observeFinishedWorkouts(): Flow<List<Workout>> =
        workoutDao.observeFinished().map { list -> list.map { it.toModel() } }

    suspend fun getTrainedLocalDates(
        startLocalDate: Int,
        endLocalDate: Int,
    ): Set<Int> = workoutDao.getTrainedLocalDates(startLocalDate, endLocalDate).toSet()

    fun headerStats(
        finishedWorkouts: List<Workout>,
        weekStartLocalDate: Int,
        monthStartLocalDate: Int,
    ): HistoryHeaderStats {
        val workoutsThisWeek = finishedWorkouts.count { it.localDate >= weekStartLocalDate }
        val workoutsThisMonth = finishedWorkouts.count { it.localDate >= monthStartLocalDate }
        val totalVolumeKg = finishedWorkouts.sumOf { it.totalVolumeKg }
        val trainedDates = finishedWorkouts.map { it.localDate }.toSortedSet(compareByDescending { it })
        var streak = 0
        var expected: Int? = null
        for (date in trainedDates) {
            if (expected == null || date == expected) {
                streak++
                expected = previousLocalDate(date)
            } else {
                break
            }
        }
        return HistoryHeaderStats(workoutsThisWeek, workoutsThisMonth, streak, totalVolumeKg)
    }
}

/** `local_date` is `yyyymmdd` as a plain integer (spec §3), so simple arithmetic can't step a
 * day back across month/year boundaries — this walks it properly via epoch days. */
private fun previousLocalDate(localDate: Int): Int {
    val year = localDate / LOCAL_DATE_YEAR_DIVISOR
    val month = (localDate / LOCAL_DATE_MONTH_DIVISOR) % LOCAL_DATE_MONTH_MODULUS
    val day = localDate % LOCAL_DATE_MONTH_DIVISOR
    val date = java.time.LocalDate.of(year, month, day).minusDays(1)
    return date.year * LOCAL_DATE_YEAR_DIVISOR + date.monthValue * LOCAL_DATE_MONTH_DIVISOR + date.dayOfMonth
}

private const val LOCAL_DATE_YEAR_DIVISOR = 10000
private const val LOCAL_DATE_MONTH_DIVISOR = 100
private const val LOCAL_DATE_MONTH_MODULUS = 100

private fun WorkoutEntity.toModel() =
    Workout(
        id = id,
        routineId = routineId,
        name = name,
        notes = notes,
        startedAt = startedAt,
        endedAt = endedAt,
        durationSec = durationSec,
        localDate = localDate,
        totalVolumeKg = totalVolumeKg,
        totalSets = totalSets,
        prCount = prCount,
        isInProgress = isInProgress,
        profileId = profileId,
    )
