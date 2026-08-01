package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseDao
import io.github.ntufar.kroton.database.ExerciseEntity
import io.github.ntufar.kroton.database.PersonalRecordEntity
import io.github.ntufar.kroton.database.RecordDao
import io.github.ntufar.kroton.database.WorkoutDao
import io.github.ntufar.kroton.database.WorkoutEntity
import io.github.ntufar.kroton.database.WorkoutExerciseEntity
import io.github.ntufar.kroton.database.WorkoutSetEntity
import io.github.ntufar.kroton.model.Exercise
import io.github.ntufar.kroton.model.OneRmFormula
import io.github.ntufar.kroton.model.RecordType
import io.github.ntufar.kroton.model.SetType
import io.github.ntufar.kroton.model.Workout
import io.github.ntufar.kroton.model.WorkoutExercise
import io.github.ntufar.kroton.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns the active-workout write path (spec §5.3): every mutation commits to the DB immediately
 * — there is no separate "save" step, and the in-progress workout row is the crash-recovery
 * mechanism. PR detection runs inline on set completion rather than as a batch job.
 */
class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val recordDao: RecordDao,
) {
    suspend fun getInProgressWorkoutId(): Long? = workoutDao.getInProgress()?.id

    fun observeExercises(): Flow<List<Exercise>> = exerciseDao.observeAll().map { list -> list.map { it.toModel() } }

    fun searchExercises(query: String): Flow<List<Exercise>> =
        exerciseDao.search(normaliseExerciseName(query)).map { list -> list.map { it.toModel() } }

    suspend fun startEmptyWorkout(
        nowMs: Long,
        localDate: Int,
        name: String = "Workout",
    ): Long =
        workoutDao.insert(
            WorkoutEntity(
                routineId = null,
                name = name,
                notes = null,
                startedAt = nowMs,
                endedAt = null,
                durationSec = 0,
                localDate = localDate,
                totalVolumeKg = 0.0,
                totalSets = 0,
                prCount = 0,
                isInProgress = true,
                profileId = null,
            ),
        )

    suspend fun addExercise(
        workoutId: Long,
        exerciseId: Long,
    ): Long {
        val sortOrder = workoutDao.getExercisesForWorkout(workoutId).size
        return workoutDao.insertExercise(
            WorkoutExerciseEntity(
                workoutId = workoutId,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                supersetGroupId = null,
                notes = null,
                restSec = null,
            ),
        )
    }

    suspend fun addSet(workoutExerciseId: Long): Long {
        val sortOrder = workoutDao.getSetsForExercise(workoutExerciseId).size
        return workoutDao.insertSet(
            WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId,
                sortOrder = sortOrder,
                setType = SetType.NORMAL,
                weightKg = null,
                reps = null,
                distanceM = null,
                durationSec = null,
                rpe = null,
                rir = null,
                isCompleted = false,
                completedAt = null,
                estimated1RmKg = null,
            ),
        )
    }

    suspend fun updateSetValues(
        setId: Long,
        weightKg: Double?,
        reps: Int?,
    ) {
        val set = workoutDao.getSetById(setId) ?: return
        workoutDao.updateSet(set.copy(weightKg = weightKg, reps = reps))
    }

    suspend fun setType(
        setId: Long,
        setType: SetType,
    ) {
        val set = workoutDao.getSetById(setId) ?: return
        workoutDao.updateSet(set.copy(setType = setType))
    }

    /** Marks a set completed, computes its estimated 1RM, checks it against stored records, and
     * recomputes the parent workout's denormalised totals. Returns the record types newly earned. */
    suspend fun completeSet(
        setId: Long,
        nowMs: Long,
        formula: OneRmFormula = OneRmFormula.EPLEY,
    ): List<RecordType> {
        val set = workoutDao.getSetById(setId) ?: return emptyList()
        val workoutExercise = workoutDao.getExerciseById(set.workoutExerciseId) ?: return emptyList()
        val setWeightKg = set.weightKg
        val setReps = set.reps
        val estimated1Rm =
            if (setWeightKg != null && setReps != null) {
                OneRepMaxCalculator.estimate(setWeightKg, setReps, formula)
            } else {
                null
            }
        workoutDao.updateSet(set.copy(isCompleted = true, completedAt = nowMs, estimated1RmKg = estimated1Rm))

        val earned =
            if (set.setType != SetType.WARMUP) {
                checkAndRecordPrs(
                    exerciseId = workoutExercise.exerciseId,
                    workoutId = workoutExercise.workoutId,
                    workoutSetId = setId,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    estimated1RmKg = estimated1Rm,
                    nowMs = nowMs,
                )
            } else {
                emptyList()
            }

        recomputeTotals(workoutExercise.workoutId)
        return earned
    }

    suspend fun uncompleteSet(setId: Long) {
        val set = workoutDao.getSetById(setId) ?: return
        val workoutExercise = workoutDao.getExerciseById(set.workoutExerciseId) ?: return
        workoutDao.updateSet(set.copy(isCompleted = false, completedAt = null, estimated1RmKg = null))
        recomputeTotals(workoutExercise.workoutId)
    }

    suspend fun deleteSet(setId: Long) {
        val set = workoutDao.getSetById(setId) ?: return
        val workoutExercise = workoutDao.getExerciseById(set.workoutExerciseId) ?: return
        workoutDao.deleteSet(set)
        recordDao.deleteForSet(setId)
        recomputeTotals(workoutExercise.workoutId)
    }

    suspend fun finishWorkout(
        workoutId: Long,
        nowMs: Long,
        notes: String?,
    ): WorkoutSummary {
        recomputeTotals(workoutId)
        val workout = requireNotNull(workoutDao.getById(workoutId)) { "Workout $workoutId not found" }
        val durationSec = ((nowMs - workout.startedAt) / MILLIS_PER_SECOND).toInt().coerceAtLeast(0)
        workoutDao.update(
            workout.copy(
                endedAt = nowMs,
                durationSec = durationSec,
                isInProgress = false,
                notes = notes,
            ),
        )
        return WorkoutSummary(
            durationSec = durationSec,
            totalVolumeKg = workout.totalVolumeKg,
            totalSets = workout.totalSets,
            prCount = workout.prCount,
        )
    }

    suspend fun getSnapshot(workoutId: Long): ActiveWorkoutSnapshot? {
        val workoutEntity = workoutDao.getById(workoutId) ?: return null
        val exercises =
            workoutDao.getExercisesForWorkout(workoutId).map { we ->
                val exercise = exerciseDao.getById(we.exerciseId)
                val sets = workoutDao.getSetsForExercise(we.id)
                val previousSets = workoutDao.getMostRecentSets(we.exerciseId, workoutId)
                val activeSets =
                    sets.mapIndexed { index, s ->
                        val previous = previousSets.getOrNull(index)
                        val prTypes =
                            if (s.isCompleted) {
                                recordDao.getForSet(
                                    s.id,
                                ).map { it.recordType }
                            } else {
                                emptyList()
                            }
                        ActiveWorkoutSet(
                            set = s.toModel(),
                            previousWeightKg = previous?.weightKg,
                            previousReps = previous?.reps,
                            prRecordTypes = prTypes,
                        )
                    }
                ActiveWorkoutExercise(
                    workoutExercise = we.toModel(),
                    exerciseId = we.exerciseId,
                    exerciseName = exercise?.name ?: "Unknown exercise",
                    sets = activeSets,
                )
            }
        return ActiveWorkoutSnapshot(workoutEntity.toModel(), exercises)
    }

    private suspend fun checkAndRecordPrs(
        exerciseId: Long,
        workoutId: Long,
        workoutSetId: Long,
        weightKg: Double?,
        reps: Int?,
        estimated1RmKg: Double?,
        nowMs: Long,
    ): List<RecordType> {
        val earned = mutableListOf<RecordType>()

        suspend fun tryRecord(
            type: RecordType,
            value: Double?,
        ) {
            if (value == null) return
            val best = recordDao.getBest(exerciseId, type)
            if (best == null || value > best.value) {
                recordDao.insert(
                    PersonalRecordEntity(
                        exerciseId = exerciseId,
                        recordType = type,
                        value = value,
                        workoutSetId = workoutSetId,
                        workoutId = workoutId,
                        achievedAt = nowMs,
                    ),
                )
                earned += type
            }
        }

        tryRecord(RecordType.MAX_WEIGHT, weightKg)
        tryRecord(RecordType.BEST_1RM, estimated1RmKg)
        if (weightKg != null && reps != null) {
            tryRecord(RecordType.BEST_SET_VOLUME, weightKg * reps)
        } else if (weightKg == null && reps != null) {
            tryRecord(RecordType.MAX_REPS, reps.toDouble())
        }
        return earned
    }

    private suspend fun recomputeTotals(workoutId: Long) {
        val workout = workoutDao.getById(workoutId) ?: return
        val exercises = workoutDao.getExercisesForWorkout(workoutId)
        var totalSets = 0
        val setVolumes = mutableListOf<Double>()
        exercises.forEach { we ->
            workoutDao.getSetsForExercise(we.id).forEach { set ->
                if (set.isCompleted) {
                    totalSets++
                    val weightKg = set.weightKg
                    val reps = set.reps
                    if (set.setType != SetType.WARMUP && weightKg != null && reps != null) {
                        setVolumes += VolumeCalculator.setVolume(weightKg, reps)
                    }
                }
            }
        }
        val prCount = recordDao.countForWorkout(workoutId)
        workoutDao.update(
            workout.copy(
                totalVolumeKg = VolumeCalculator.sessionVolume(setVolumes),
                totalSets = totalSets,
                prCount = prCount,
            ),
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

private fun ExerciseEntity.toModel() =
    Exercise(
        id = id,
        name = name,
        nameNormalised = nameNormalised,
        exerciseType = exerciseType,
        equipment = equipment,
        primaryMuscle = primaryMuscle,
        force = force,
        mechanic = mechanic,
        isCustom = isCustom,
        isArchived = isArchived,
        defaultRestSec = defaultRestSec,
        instructions = instructions,
        seedUuid = seedUuid,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

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

private fun WorkoutExerciseEntity.toModel() =
    WorkoutExercise(
        id = id,
        workoutId = workoutId,
        exerciseId = exerciseId,
        sortOrder = sortOrder,
        supersetGroupId = supersetGroupId,
        notes = notes,
        restSec = restSec,
    )

private fun WorkoutSetEntity.toModel() =
    WorkoutSet(
        id = id,
        workoutExerciseId = workoutExerciseId,
        sortOrder = sortOrder,
        setType = setType,
        weightKg = weightKg,
        reps = reps,
        distanceM = distanceM,
        durationSec = durationSec,
        rpe = rpe,
        rir = rir,
        isCompleted = isCompleted,
        completedAt = completedAt,
        estimated1RmKg = estimated1RmKg,
    )
