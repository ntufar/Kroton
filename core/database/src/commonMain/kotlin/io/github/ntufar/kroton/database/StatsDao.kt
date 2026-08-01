package io.github.ntufar.kroton.database

import androidx.room.Query
import androidx.room.Dao
import io.github.ntufar.kroton.model.MuscleGroup

data class WorkoutDateDuration(val localDate: Int, val durationSec: Int, val startedAt: Long)

data class DayVolume(val localDate: Int, val totalVolumeKg: Double)

data class DayMuscleVolume(val localDate: Int, val muscle: MuscleGroup, val volumeKg: Double)

data class ExerciseDaySetCount(val exerciseId: Long, val localDate: Int, val setCount: Int)

data class DayEstimated1Rm(val localDate: Int, val best1RmKg: Double)

/**
 * Every query here returns pre-aggregated `GROUP BY` rows — at most one row per
 * day/exercise/muscle in the requested range, never one row per set (spec §8: "chart queries
 * return pre-aggregated rows from SQL, never 60,000 objects into memory"). `WorkoutDao`
 * intentionally isn't reused for this: its queries are per-set-row shaped for the active-workout
 * and history screens, which is the wrong shape for charts at scale.
 */
@Dao
interface StatsDao {
    @Query(
        """
        SELECT localDate, durationSec, startedAt FROM workout
        WHERE isInProgress = 0 AND startedAt BETWEEN :startMs AND :endMs
        ORDER BY startedAt ASC
        """,
    )
    suspend fun getWorkoutDatesAndDurations(
        startMs: Long,
        endMs: Long,
    ): List<WorkoutDateDuration>

    @Query(
        """
        SELECT w.localDate as localDate, SUM(ws.weightKg * ws.reps) as totalVolumeKg
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout w ON we.workoutId = w.id
        WHERE w.isInProgress = 0 AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND ws.weightKg IS NOT NULL AND ws.reps IS NOT NULL
          AND w.startedAt BETWEEN :startMs AND :endMs
        GROUP BY w.localDate
        ORDER BY w.localDate ASC
        """,
    )
    suspend fun getVolumeByDay(
        startMs: Long,
        endMs: Long,
    ): List<DayVolume>

    @Query(
        """
        SELECT w.localDate as localDate, e.primaryMuscle as muscle, SUM(ws.weightKg * ws.reps) as volumeKg
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout w ON we.workoutId = w.id
        JOIN exercise e ON we.exerciseId = e.id
        WHERE w.isInProgress = 0 AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND ws.weightKg IS NOT NULL AND ws.reps IS NOT NULL
          AND w.startedAt BETWEEN :startMs AND :endMs
        GROUP BY w.localDate, e.primaryMuscle
        ORDER BY w.localDate ASC
        """,
    )
    suspend fun getVolumeByDayAndPrimaryMuscle(
        startMs: Long,
        endMs: Long,
    ): List<DayMuscleVolume>

    @Query(
        """
        SELECT we.exerciseId as exerciseId, w.localDate as localDate, COUNT(*) as setCount
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout w ON we.workoutId = w.id
        WHERE w.isInProgress = 0 AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND w.startedAt BETWEEN :startMs AND :endMs
        GROUP BY we.exerciseId, w.localDate
        """,
    )
    suspend fun getSetCountsByExerciseAndDay(
        startMs: Long,
        endMs: Long,
    ): List<ExerciseDaySetCount>

    @Query(
        """
        SELECT w.localDate as localDate, MAX(ws.estimated1RmKg) as best1RmKg
        FROM workout_set ws
        JOIN workout_exercise we ON ws.workoutExerciseId = we.id
        JOIN workout w ON we.workoutId = w.id
        WHERE we.exerciseId = :exerciseId AND ws.isCompleted = 1 AND ws.estimated1RmKg IS NOT NULL
          AND w.startedAt BETWEEN :startMs AND :endMs
        GROUP BY w.localDate
        ORDER BY w.localDate ASC
        """,
    )
    suspend fun get1RmByDay(
        exerciseId: Long,
        startMs: Long,
        endMs: Long,
    ): List<DayEstimated1Rm>
}
