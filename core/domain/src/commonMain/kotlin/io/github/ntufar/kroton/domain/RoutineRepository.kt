package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseDao
import io.github.ntufar.kroton.database.ExerciseEntity
import io.github.ntufar.kroton.database.RoutineDao
import io.github.ntufar.kroton.database.RoutineEntity
import io.github.ntufar.kroton.database.RoutineExerciseEntity
import io.github.ntufar.kroton.database.RoutineFolderEntity
import io.github.ntufar.kroton.database.RoutineSetEntity
import io.github.ntufar.kroton.database.WorkoutDao
import io.github.ntufar.kroton.database.WorkoutEntity
import io.github.ntufar.kroton.database.WorkoutExerciseEntity
import io.github.ntufar.kroton.database.WorkoutSetEntity
import io.github.ntufar.kroton.model.Exercise
import io.github.ntufar.kroton.model.Routine
import io.github.ntufar.kroton.model.RoutineFolder
import io.github.ntufar.kroton.model.SetType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Owns routine CRUD, the routine → active-workout hand-off, and save-workout-as-routine
 * (spec §5.2/§9 M2). Routine and workout rows share no foreign-key enforcement in Room, so
 * child rows (exercises/sets) are always deleted or copied explicitly here. */
class RoutineRepository(
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
) {
    fun observeFolders(): Flow<List<RoutineFolder>> =
        routineDao.observeFolders().map { list -> list.map { it.toModel() } }

    fun observeExercises(): Flow<List<Exercise>> = exerciseDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeRoutines(): Flow<List<Routine>> =
        routineDao.observeAllRoutines().map { list -> list.map { it.toModel() } }

    suspend fun createFolder(name: String): Long {
        val sortOrder = routineDao.countFolders()
        return routineDao.insertFolder(RoutineFolderEntity(name = name, sortOrder = sortOrder))
    }

    suspend fun renameFolder(
        folderId: Long,
        name: String,
    ) {
        val folder = routineDao.getFolderById(folderId) ?: return
        routineDao.updateFolder(folder.copy(name = name))
    }

    suspend fun deleteFolder(folderId: Long) {
        val folder = routineDao.getFolderById(folderId) ?: return
        routineDao.deleteFolder(folder)
    }

    suspend fun reorderFolders(orderedFolderIds: List<Long>) {
        orderedFolderIds.forEachIndexed { index, id ->
            val folder = routineDao.getFolderById(id) ?: return@forEachIndexed
            routineDao.updateFolder(folder.copy(sortOrder = index))
        }
    }

    suspend fun reorderExercises(orderedRoutineExerciseIds: List<Long>) {
        orderedRoutineExerciseIds.forEachIndexed { index, id ->
            val exercise = routineDao.getExerciseById(id) ?: return@forEachIndexed
            routineDao.updateExercise(exercise.copy(sortOrder = index))
        }
    }

    suspend fun createRoutine(
        folderId: Long?,
        name: String,
        nowMs: Long,
    ): Long {
        val sortOrder = routineDao.countRoutinesInFolder(folderId)
        return routineDao.insertRoutine(
            RoutineEntity(
                folderId = folderId,
                name = name,
                notes = null,
                sortOrder = sortOrder,
                createdAt = nowMs,
                updatedAt = nowMs,
                lastPerformedAt = null,
            ),
        )
    }

    suspend fun deleteRoutine(routineId: Long) {
        routineDao.getExercisesForRoutine(routineId).forEach { re ->
            routineDao.getSetsForExercise(re.id).forEach { routineDao.deleteSet(it) }
            routineDao.deleteExercise(re)
        }
        val routine = routineDao.getRoutineById(routineId) ?: return
        routineDao.deleteRoutine(routine)
    }

    suspend fun duplicateRoutine(
        routineId: Long,
        nowMs: Long,
    ): Long? {
        val routine = routineDao.getRoutineById(routineId) ?: return null
        val newRoutineId =
            routineDao.insertRoutine(
                routine.copy(
                    id = 0,
                    name = "${routine.name} (copy)",
                    createdAt = nowMs,
                    updatedAt = nowMs,
                    lastPerformedAt = null,
                ),
            )
        routineDao.getExercisesForRoutine(routineId).forEach { re ->
            val newExerciseId = routineDao.insertExercise(re.copy(id = 0, routineId = newRoutineId))
            routineDao.getSetsForExercise(re.id).forEach { set ->
                routineDao.insertSet(set.copy(id = 0, routineExerciseId = newExerciseId))
            }
        }
        return newRoutineId
    }

    suspend fun getRoutineDetail(routineId: Long): RoutineDetail? {
        val routine = routineDao.getRoutineById(routineId) ?: return null
        val exercises =
            routineDao.getExercisesForRoutine(routineId).map { re ->
                val exercise = exerciseDao.getById(re.exerciseId)
                RoutineExerciseDetail(
                    routineExercise = re.toModel(),
                    exerciseId = re.exerciseId,
                    exerciseName = exercise?.name ?: "Unknown exercise",
                    sets = routineDao.getSetsForExercise(re.id).map { it.toModel() },
                )
            }
        return RoutineDetail(routine.toModel(), exercises)
    }

    suspend fun exerciseSummaryLine(routineId: Long): String =
        routineDao.getExercisesForRoutine(routineId)
            .mapNotNull { exerciseDao.getById(it.exerciseId)?.name }
            .joinToString(", ")

    suspend fun addExercise(
        routineId: Long,
        exerciseId: Long,
    ): Long {
        val sortOrder = routineDao.getExercisesForRoutine(routineId).size
        return routineDao.insertExercise(
            RoutineExerciseEntity(
                routineId = routineId,
                exerciseId = exerciseId,
                sortOrder = sortOrder,
                supersetGroupId = null,
                restSec = null,
                notes = null,
            ),
        )
    }

    suspend fun removeExercise(routineExerciseId: Long) {
        val exercise = routineDao.getExerciseById(routineExerciseId) ?: return
        routineDao.getSetsForExercise(routineExerciseId).forEach { routineDao.deleteSet(it) }
        routineDao.deleteExercise(exercise)
    }

    suspend fun updateSetTargets(
        setId: Long,
        targetRepsMin: Int?,
        targetRepsMax: Int?,
        targetWeightKg: Double?,
    ) {
        val set = routineDao.getSetById(setId) ?: return
        routineDao.updateSet(
            set.copy(targetRepsMin = targetRepsMin, targetRepsMax = targetRepsMax, targetWeightKg = targetWeightKg),
        )
    }

    suspend fun removeSet(setId: Long) {
        val set = routineDao.getSetById(setId) ?: return
        routineDao.deleteSet(set)
    }

    suspend fun addSet(routineExerciseId: Long): Long {
        val sortOrder = routineDao.getSetsForExercise(routineExerciseId).size
        return routineDao.insertSet(
            RoutineSetEntity(
                routineExerciseId = routineExerciseId,
                sortOrder = sortOrder,
                setType = SetType.NORMAL,
                targetRepsMin = null,
                targetRepsMax = null,
                targetWeightKg = null,
                targetRpe = null,
            ),
        )
    }

    /** Creates a new in-progress workout pre-filled from the routine's exercise/set targets
     * (spec §5.2 "start from routine"). Target reps/weight seed the row; the set is left
     * unchecked, same as any other set entered in the active-workout flow. */
    suspend fun startWorkoutFromRoutine(
        routineId: Long,
        nowMs: Long,
        localDate: Int,
    ): Long? {
        val routine = routineDao.getRoutineById(routineId) ?: return null
        val workoutId =
            workoutDao.insert(
                WorkoutEntity(
                    routineId = routineId,
                    name = routine.name,
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
        routineDao.getExercisesForRoutine(routineId).forEach { re ->
            val workoutExerciseId =
                workoutDao.insertExercise(
                    WorkoutExerciseEntity(
                        workoutId = workoutId,
                        exerciseId = re.exerciseId,
                        sortOrder = re.sortOrder,
                        supersetGroupId = null,
                        notes = re.notes,
                        restSec = re.restSec,
                    ),
                )
            routineDao.getSetsForExercise(re.id).forEach { rs ->
                workoutDao.insertSet(
                    WorkoutSetEntity(
                        workoutExerciseId = workoutExerciseId,
                        sortOrder = rs.sortOrder,
                        setType = rs.setType,
                        weightKg = rs.targetWeightKg,
                        reps = rs.targetRepsMax ?: rs.targetRepsMin,
                        distanceM = null,
                        durationSec = null,
                        rpe = rs.targetRpe,
                        rir = null,
                        isCompleted = false,
                        completedAt = null,
                        estimated1RmKg = null,
                    ),
                )
            }
        }
        routineDao.updateRoutine(routine.copy(lastPerformedAt = nowMs))
        return workoutId
    }

    /** Reads a workout's logged exercises/sets and writes them as a new routine template
     * (spec §5.4 "save as routine"), using each set's actual values as the new target. */
    suspend fun saveWorkoutAsRoutine(
        workoutId: Long,
        folderId: Long?,
        name: String,
        nowMs: Long,
    ): Long? {
        val workout = workoutDao.getById(workoutId) ?: return null
        val newRoutineId =
            routineDao.insertRoutine(
                RoutineEntity(
                    folderId = folderId,
                    name = name,
                    notes = null,
                    sortOrder = 0,
                    createdAt = nowMs,
                    updatedAt = nowMs,
                    lastPerformedAt = null,
                ),
            )
        workoutDao.getExercisesForWorkout(workoutId).forEach { we ->
            val newExerciseId =
                routineDao.insertExercise(
                    RoutineExerciseEntity(
                        routineId = newRoutineId,
                        exerciseId = we.exerciseId,
                        sortOrder = we.sortOrder,
                        supersetGroupId = null,
                        restSec = we.restSec,
                        notes = we.notes,
                    ),
                )
            workoutDao.getSetsForExercise(we.id).forEach { set ->
                routineDao.insertSet(
                    RoutineSetEntity(
                        routineExerciseId = newExerciseId,
                        sortOrder = set.sortOrder,
                        setType = set.setType,
                        targetRepsMin = set.reps,
                        targetRepsMax = set.reps,
                        targetWeightKg = set.weightKg,
                        targetRpe = set.rpe,
                    ),
                )
            }
        }
        return newRoutineId
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

private fun RoutineFolderEntity.toModel() = RoutineFolder(id = id, name = name, sortOrder = sortOrder)

private fun RoutineEntity.toModel() =
    Routine(
        id = id,
        folderId = folderId,
        name = name,
        notes = notes,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastPerformedAt = lastPerformedAt,
    )

private fun RoutineExerciseEntity.toModel() =
    io.github.ntufar.kroton.model.RoutineExercise(
        id = id,
        routineId = routineId,
        exerciseId = exerciseId,
        sortOrder = sortOrder,
        supersetGroupId = supersetGroupId,
        restSec = restSec,
        notes = notes,
    )

private fun RoutineSetEntity.toModel() =
    io.github.ntufar.kroton.model.RoutineSet(
        id = id,
        routineExerciseId = routineExerciseId,
        sortOrder = sortOrder,
        setType = setType,
        targetRepsMin = targetRepsMin,
        targetRepsMax = targetRepsMax,
        targetWeightKg = targetWeightKg,
        targetRpe = targetRpe,
    )
