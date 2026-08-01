package io.github.ntufar.kroton.database

import io.github.ntufar.kroton.model.Equipment
import io.github.ntufar.kroton.model.ExerciseType
import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.SetType
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val WORKOUT_COUNT = 2_000
private const val EXERCISES_PER_WORKOUT = 3
private const val SETS_PER_EXERCISE = 10
private const val EXERCISE_CATALOG_SIZE = 20
private const val DAY_MS = 24L * 60 * 60 * 1000
private const val QUERY_BUDGET_MS = 2_000L

/**
 * Spec §8: "History and chart screens must stay smooth at 2,000 workouts / 60,000 sets;
 * generate that dataset in an instrumented test and treat it as a gate. Chart queries return
 * pre-aggregated rows from SQL, never 60,000 objects into memory." This runs against a real
 * Room/SQLite database (the JVM `BundledSQLiteDriver` build, same engine as Android) rather than
 * a fake, so the timing is meaningful — and asserts on returned row *counts* being bounded by
 * days/exercises, not sets, which is the actual "never load 60,000 objects" claim.
 */
class StatsDaoPerformanceTest {
    private lateinit var dbFile: File
    private lateinit var db: KrotonDatabase

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("kroton_perf_test", ".db")
        db = createRoomDatabase(dbFile.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }

    @Test
    fun statsDaoQueries_stayFastAndPreAggregated_at60kSets() =
        runBlocking {
            val exerciseIds = seedExercises(db.exerciseDao())
            seedWorkoutsAndSets(db.workoutDao(), exerciseIds)

            val statsDao = db.statsDao()
            val startMs = 0L
            val endMs = System.currentTimeMillis() + DAY_MS

            val volumeElapsed =
                measureMillis {
                    val rows = statsDao.getVolumeByDay(startMs, endMs)
                    assertTrue(rows.size <= WORKOUT_COUNT, "volume-by-day must be one row per day, not per set")
                }
            assertTrue(volumeElapsed < QUERY_BUDGET_MS, "getVolumeByDay took ${volumeElapsed}ms, budget is ${QUERY_BUDGET_MS}ms")

            val muscleElapsed =
                measureMillis {
                    val rows = statsDao.getVolumeByDayAndPrimaryMuscle(startMs, endMs)
                    val maxPossibleRows = WORKOUT_COUNT * MuscleGroup.entries.size
                    assertTrue(rows.size <= maxPossibleRows, "must be pre-aggregated by day+muscle, not per set")
                }
            assertTrue(muscleElapsed < QUERY_BUDGET_MS, "getVolumeByDayAndPrimaryMuscle took ${muscleElapsed}ms")

            val setCountElapsed =
                measureMillis {
                    val rows = statsDao.getSetCountsByExerciseAndDay(startMs, endMs)
                    val maxPossibleRows = WORKOUT_COUNT * EXERCISE_CATALOG_SIZE
                    assertTrue(rows.size <= maxPossibleRows, "must be pre-aggregated by exercise+day, not per set")
                }
            assertTrue(setCountElapsed < QUERY_BUDGET_MS, "getSetCountsByExerciseAndDay took ${setCountElapsed}ms")

            val rmElapsed =
                measureMillis {
                    statsDao.get1RmByDay(exerciseIds.first(), startMs, endMs)
                }
            assertTrue(rmElapsed < QUERY_BUDGET_MS, "get1RmByDay took ${rmElapsed}ms")
        }

    private suspend fun seedExercises(exerciseDao: ExerciseDao): List<Long> =
        (0 until EXERCISE_CATALOG_SIZE).map { index ->
            exerciseDao.insert(
                ExerciseEntity(
                    name = "Exercise $index",
                    nameNormalised = "exercise $index",
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    equipment = Equipment.BARBELL,
                    primaryMuscle = MuscleGroup.entries[index % MuscleGroup.entries.size],
                    force = null,
                    mechanic = null,
                    isCustom = false,
                    isArchived = false,
                    defaultRestSec = null,
                    instructions = null,
                    seedUuid = null,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )
        }

    private suspend fun seedWorkoutsAndSets(
        workoutDao: WorkoutDao,
        exerciseIds: List<Long>,
    ) {
        val nowMs = System.currentTimeMillis()
        val workouts =
            (0 until WORKOUT_COUNT).map { index ->
                val startedAt = nowMs - index.toLong() * DAY_MS
                val localDate = localDateFor(startedAt)
                WorkoutEntity(
                    routineId = null,
                    name = "Workout $index",
                    notes = null,
                    startedAt = startedAt,
                    endedAt = startedAt + 1,
                    durationSec = 3_600,
                    localDate = localDate,
                    totalVolumeKg = 0.0,
                    totalSets = 0,
                    prCount = 0,
                    isInProgress = false,
                    profileId = null,
                )
            }
        val workoutIds = workoutDao.insertWorkouts(workouts)

        val workoutExercises =
            workoutIds.flatMap { workoutId ->
                (0 until EXERCISES_PER_WORKOUT).map { slot ->
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        exerciseId = exerciseIds[slot % exerciseIds.size],
                        sortOrder = slot,
                        supersetGroupId = null,
                        notes = null,
                        restSec = null,
                    )
                }
            }
        val workoutExerciseIds = workoutDao.insertExercises(workoutExercises)

        val sets =
            workoutExerciseIds.flatMap { workoutExerciseId ->
                (0 until SETS_PER_EXERCISE).map { setIndex ->
                    WorkoutSetEntity(
                        workoutExerciseId = workoutExerciseId,
                        sortOrder = setIndex,
                        setType = SetType.NORMAL,
                        weightKg = 60.0 + setIndex,
                        reps = 8,
                        distanceM = null,
                        durationSec = null,
                        rpe = null,
                        rir = null,
                        isCompleted = true,
                        completedAt = nowMs,
                        estimated1RmKg = 75.0 + setIndex,
                    )
                }
            }
        workoutDao.insertSets(sets)
    }

    private fun localDateFor(epochMs: Long): Int {
        val instant = java.time.Instant.ofEpochMilli(epochMs)
        val date = java.time.LocalDate.ofInstant(instant, java.time.ZoneOffset.UTC)
        return date.year * LOCAL_DATE_YEAR_MULTIPLIER + date.monthValue * LOCAL_DATE_MONTH_MULTIPLIER + date.dayOfMonth
    }

    private suspend inline fun measureMillis(crossinline block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / NANOS_PER_MILLI
    }

    private companion object {
        const val LOCAL_DATE_YEAR_MULTIPLIER = 10_000
        const val LOCAL_DATE_MONTH_MULTIPLIER = 100
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
