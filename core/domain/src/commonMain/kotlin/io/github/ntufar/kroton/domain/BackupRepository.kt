package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseDao
import io.github.ntufar.kroton.database.ExerciseSecondaryMuscleEntity
import io.github.ntufar.kroton.database.KROTON_DATABASE_VERSION
import io.github.ntufar.kroton.database.MeasurementDao
import io.github.ntufar.kroton.database.MeasurementEntryEntity
import io.github.ntufar.kroton.database.MeasurementTypeEntity
import io.github.ntufar.kroton.database.PersonalRecordEntity
import io.github.ntufar.kroton.database.ProgressPhotoDao
import io.github.ntufar.kroton.database.ProgressPhotoEntity
import io.github.ntufar.kroton.database.RecordDao
import io.github.ntufar.kroton.database.RoutineDao
import io.github.ntufar.kroton.database.RoutineEntity
import io.github.ntufar.kroton.database.RoutineExerciseEntity
import io.github.ntufar.kroton.database.RoutineFolderEntity
import io.github.ntufar.kroton.database.RoutineSetEntity
import io.github.ntufar.kroton.database.WorkoutDao
import io.github.ntufar.kroton.database.WorkoutEntity
import io.github.ntufar.kroton.database.WorkoutExerciseEntity
import io.github.ntufar.kroton.database.WorkoutSetEntity
import io.github.ntufar.kroton.export.BackupExercise
import io.github.ntufar.kroton.export.BackupJson
import io.github.ntufar.kroton.export.BackupMeasurement
import io.github.ntufar.kroton.export.BackupMeasurementType
import io.github.ntufar.kroton.export.BackupPhoto
import io.github.ntufar.kroton.export.BackupProfile
import io.github.ntufar.kroton.export.BackupRecord
import io.github.ntufar.kroton.export.BackupRoutine
import io.github.ntufar.kroton.export.BackupRoutineExercise
import io.github.ntufar.kroton.export.BackupRoutineFolder
import io.github.ntufar.kroton.export.BackupRoutineSet
import io.github.ntufar.kroton.export.BackupWorkout
import io.github.ntufar.kroton.export.BackupWorkoutExercise
import io.github.ntufar.kroton.export.BackupWorkoutSet
import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.PhotoPose
import io.github.ntufar.kroton.model.RecordType
import io.github.ntufar.kroton.model.SetType
import kotlinx.coroutines.flow.first

enum class RestoreMode { MERGE, REPLACE }

data class RestoreSummary(val workoutsImported: Int, val routinesImported: Int, val measurementsImported: Int)

/**
 * Builds and restores the §6.3 JSON backup tree. Restoring always remaps ids: a backup taken on
 * one install and restored onto another (or the same install after `REPLACE` wipes generated
 * data) never has matching auto-generated ids, so every cross-reference (exercise, routine,
 * workout, workout_set, measurement type) is rebuilt through an old-id → new-id map rather than
 * copied verbatim. Photo *files* are the caller's job (this only restores `progress_photo` rows,
 * keyed by `fileName`; the caller copies the actual bytes from the `.kroton` zip's `photos/`).
 */
class BackupRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val measurementDao: MeasurementDao,
    private val progressPhotoDao: ProgressPhotoDao,
    private val recordDao: RecordDao,
    private val profileRepository: ProfileRepository,
) {
    suspend fun buildBackup(
        nowIso8601: String,
        appVersion: String,
    ): BackupJson {
        val profile = profileRepository.get()
        val exercises =
            exerciseDao.observeAll().first().map { e ->
                BackupExercise(
                    id = e.id,
                    name = e.name,
                    nameNormalised = e.nameNormalised,
                    exerciseType = e.exerciseType.name,
                    equipment = e.equipment.name,
                    primaryMuscle = e.primaryMuscle.name,
                    secondaryMuscles = exerciseDao.getSecondaryMuscles(e.id).map { it.name },
                    force = e.force?.name,
                    mechanic = e.mechanic?.name,
                    isCustom = e.isCustom,
                    isArchived = e.isArchived,
                    defaultRestSec = e.defaultRestSec,
                    instructions = e.instructions,
                    seedUuid = e.seedUuid,
                    createdAt = e.createdAt,
                    updatedAt = e.updatedAt,
                )
            }
        val folders = routineDao.observeFolders().first().map { BackupRoutineFolder(it.id, it.name, it.sortOrder) }
        val routines =
            routineDao.observeAllRoutines().first().map { r ->
                BackupRoutine(
                    id = r.id,
                    folderId = r.folderId,
                    name = r.name,
                    notes = r.notes,
                    sortOrder = r.sortOrder,
                    createdAt = r.createdAt,
                    updatedAt = r.updatedAt,
                    lastPerformedAt = r.lastPerformedAt,
                    exercises =
                        routineDao.getExercisesForRoutine(r.id).map { re ->
                            BackupRoutineExercise(
                                id = re.id,
                                exerciseId = re.exerciseId,
                                sortOrder = re.sortOrder,
                                supersetGroupId = re.supersetGroupId,
                                restSec = re.restSec,
                                notes = re.notes,
                                sets =
                                    routineDao.getSetsForExercise(re.id).map { rs ->
                                        BackupRoutineSet(
                                            id = rs.id,
                                            sortOrder = rs.sortOrder,
                                            setType = rs.setType.name,
                                            targetRepsMin = rs.targetRepsMin,
                                            targetRepsMax = rs.targetRepsMax,
                                            targetWeightKg = rs.targetWeightKg,
                                            targetRpe = rs.targetRpe,
                                        )
                                    },
                            )
                        },
                )
            }
        val workouts =
            workoutDao.observeAll().first().map { w ->
                BackupWorkout(
                    id = w.id,
                    routineId = w.routineId,
                    name = w.name,
                    notes = w.notes,
                    startedAt = w.startedAt,
                    endedAt = w.endedAt,
                    durationSec = w.durationSec,
                    localDate = w.localDate,
                    totalVolumeKg = w.totalVolumeKg,
                    totalSets = w.totalSets,
                    prCount = w.prCount,
                    isInProgress = w.isInProgress,
                    exercises =
                        workoutDao.getExercisesForWorkout(w.id).map { we ->
                            BackupWorkoutExercise(
                                id = we.id,
                                exerciseId = we.exerciseId,
                                sortOrder = we.sortOrder,
                                supersetGroupId = we.supersetGroupId,
                                notes = we.notes,
                                restSec = we.restSec,
                                sets =
                                    workoutDao.getSetsForExercise(we.id).map { s ->
                                        BackupWorkoutSet(
                                            id = s.id,
                                            sortOrder = s.sortOrder,
                                            setType = s.setType.name,
                                            weightKg = s.weightKg,
                                            reps = s.reps,
                                            distanceM = s.distanceM,
                                            durationSec = s.durationSec,
                                            rpe = s.rpe,
                                            rir = s.rir,
                                            isCompleted = s.isCompleted,
                                            completedAt = s.completedAt,
                                            estimated1RmKg = s.estimated1RmKg,
                                        )
                                    },
                            )
                        },
                )
            }
        val types =
            measurementDao.observeAllTypes().first().map {
                BackupMeasurementType(
                    it.id,
                    it.key,
                    it.displayName,
                    it.unitKind.name,
                    it.isBuiltin,
                    it.isEnabled,
                    it.sortOrder,
                    it.decimals,
                )
            }
        val measurements =
            measurementDao.getAllEntries().map {
                BackupMeasurement(it.id, it.typeId, it.value, it.recordedAt, it.localDate, it.note)
            }
        val records =
            recordDao.getAll().map {
                BackupRecord(
                    it.id,
                    it.exerciseId,
                    it.recordType.name,
                    it.value,
                    it.workoutSetId,
                    it.workoutId,
                    it.achievedAt,
                )
            }
        val photos =
            progressPhotoDao.observeAll().first().map {
                BackupPhoto(it.id, it.recordedAt, it.localDate, it.fileName, it.pose.name, it.note)
            }
        return BackupJson(
            schemaVersion = KROTON_DATABASE_VERSION,
            appVersion = appVersion,
            exportedAt = nowIso8601,
            profile = profile.toBackup(),
            exercises = exercises,
            routineFolders = folders,
            routines = routines,
            workouts = workouts,
            measurementTypes = types,
            measurements = measurements,
            records = records,
            photos = photos,
        )
    }

    suspend fun restore(
        backup: BackupJson,
        mode: RestoreMode,
    ): RestoreSummary {
        if (mode == RestoreMode.REPLACE) wipeGeneratedData()

        val exerciseIdMap = reconcileExercises(backup.exercises)
        val typeIdMap = reconcileMeasurementTypes(backup.measurementTypes)
        val folderIdMap =
            backup.routineFolders.associate { f ->
                f.id to routineDao.insertFolder(RoutineFolderEntity(name = f.name, sortOrder = f.sortOrder))
            }

        val routineIdMap = mutableMapOf<Long, Long>()
        backup.routines.forEach { r -> restoreRoutine(r, exerciseIdMap, folderIdMap, routineIdMap) }

        val existingWorkoutKeys =
            if (mode == RestoreMode.MERGE) {
                workoutDao.observeAll().first().map { it.startedAt to it.name }.toSet()
            } else {
                emptySet()
            }
        val workoutIdMap = mutableMapOf<Long, Long>()
        val workoutSetIdMap = mutableMapOf<Long, Long>()
        var workoutsImported = 0
        backup.workouts.forEach { w ->
            if (mode == RestoreMode.MERGE && (w.startedAt to w.name) in existingWorkoutKeys) return@forEach
            restoreWorkout(w, exerciseIdMap, routineIdMap, workoutIdMap, workoutSetIdMap)
            workoutsImported++
        }

        var measurementsImported = 0
        backup.measurements.forEach { m ->
            val typeId = typeIdMap[m.typeId] ?: return@forEach
            measurementDao.upsertEntry(
                MeasurementEntryEntity(
                    typeId = typeId,
                    value = m.value,
                    recordedAt = m.recordedAt,
                    localDate = m.localDate,
                    note = m.note,
                    profileId = null,
                ),
            )
            measurementsImported++
        }

        backup.records.forEach { r ->
            val exId = exerciseIdMap[r.exerciseId] ?: return@forEach
            val workoutId = workoutIdMap[r.workoutId] ?: return@forEach
            recordDao.insert(
                PersonalRecordEntity(
                    exerciseId = exId,
                    recordType = RecordType.valueOf(r.recordType),
                    value = r.value,
                    workoutSetId = r.workoutSetId?.let { workoutSetIdMap[it] },
                    workoutId = workoutId,
                    achievedAt = r.achievedAt,
                ),
            )
        }

        backup.photos.forEach { p ->
            progressPhotoDao.insert(
                ProgressPhotoEntity(
                    recordedAt = p.recordedAt,
                    localDate = p.localDate,
                    fileName = p.fileName,
                    pose = PhotoPose.valueOf(p.pose),
                    note = p.note,
                ),
            )
        }

        profileRepository.update(backup.profile.toModel())

        return RestoreSummary(workoutsImported, routineIdMap.size, measurementsImported)
    }

    private suspend fun wipeGeneratedData() {
        workoutDao.clearSets()
        workoutDao.clearExercises()
        workoutDao.clearWorkouts()
        routineDao.clearSets()
        routineDao.clearExercises()
        routineDao.clearRoutines()
        routineDao.clearFolders()
        measurementDao.clearEntries()
        progressPhotoDao.clearAll()
        recordDao.clearAll()
    }

    private suspend fun reconcileExercises(exercises: List<BackupExercise>): Map<Long, Long> =
        exercises.associate { e ->
            val existing =
                e.seedUuid?.let { exerciseDao.getBySeedUuid(it) } ?: exerciseDao.getByNameNormalised(e.nameNormalised)
            val id =
                existing?.id ?: exerciseDao.insert(
                    io.github.ntufar.kroton.database.ExerciseEntity(
                        name = e.name,
                        nameNormalised = e.nameNormalised,
                        exerciseType = io.github.ntufar.kroton.model.ExerciseType.valueOf(e.exerciseType),
                        equipment = io.github.ntufar.kroton.model.Equipment.valueOf(e.equipment),
                        primaryMuscle = MuscleGroup.valueOf(e.primaryMuscle),
                        force = e.force?.let { io.github.ntufar.kroton.model.Force.valueOf(it) },
                        mechanic = e.mechanic?.let { io.github.ntufar.kroton.model.Mechanic.valueOf(it) },
                        isCustom = e.isCustom,
                        isArchived = e.isArchived,
                        defaultRestSec = e.defaultRestSec,
                        instructions = e.instructions,
                        seedUuid = e.seedUuid,
                        createdAt = e.createdAt,
                        updatedAt = e.updatedAt,
                    ),
                ).also { newId ->
                    e.secondaryMuscles.forEach { muscle ->
                        val entity = ExerciseSecondaryMuscleEntity(newId, MuscleGroup.valueOf(muscle))
                        exerciseDao.insertSecondaryMuscle(entity)
                    }
                }
            e.id to id
        }

    private suspend fun reconcileMeasurementTypes(types: List<BackupMeasurementType>): Map<Long, Long> =
        types.associate { t ->
            val existing = measurementDao.getTypeByKey(t.key)
            val id =
                existing?.id ?: measurementDao.insertType(
                    MeasurementTypeEntity(
                        key = t.key,
                        displayName = t.displayName,
                        unitKind = io.github.ntufar.kroton.model.UnitKind.valueOf(t.unitKind),
                        isBuiltin = t.isBuiltin,
                        isEnabled = t.isEnabled,
                        sortOrder = t.sortOrder,
                        decimals = t.decimals,
                    ),
                )
            t.id to id
        }

    private suspend fun restoreRoutine(
        r: BackupRoutine,
        exerciseIdMap: Map<Long, Long>,
        folderIdMap: Map<Long, Long>,
        routineIdMap: MutableMap<Long, Long>,
    ) {
        val newRoutineId =
            routineDao.insertRoutine(
                RoutineEntity(
                    folderId = r.folderId?.let { folderIdMap[it] },
                    name = r.name,
                    notes = r.notes,
                    sortOrder = r.sortOrder,
                    createdAt = r.createdAt,
                    updatedAt = r.updatedAt,
                    lastPerformedAt = r.lastPerformedAt,
                ),
            )
        routineIdMap[r.id] = newRoutineId
        r.exercises.forEach { re ->
            val exId = exerciseIdMap[re.exerciseId] ?: return@forEach
            val newReId =
                routineDao.insertExercise(
                    RoutineExerciseEntity(
                        routineId = newRoutineId,
                        exerciseId = exId,
                        sortOrder = re.sortOrder,
                        supersetGroupId = null,
                        restSec = re.restSec,
                        notes = re.notes,
                    ),
                )
            re.sets.forEach { rs ->
                routineDao.insertSet(
                    RoutineSetEntity(
                        routineExerciseId = newReId,
                        sortOrder = rs.sortOrder,
                        setType = SetType.valueOf(rs.setType),
                        targetRepsMin = rs.targetRepsMin,
                        targetRepsMax = rs.targetRepsMax,
                        targetWeightKg = rs.targetWeightKg,
                        targetRpe = rs.targetRpe,
                    ),
                )
            }
        }
    }

    private suspend fun restoreWorkout(
        w: BackupWorkout,
        exerciseIdMap: Map<Long, Long>,
        routineIdMap: Map<Long, Long>,
        workoutIdMap: MutableMap<Long, Long>,
        workoutSetIdMap: MutableMap<Long, Long>,
    ) {
        val newWorkoutId =
            workoutDao.insert(
                WorkoutEntity(
                    routineId = w.routineId?.let { routineIdMap[it] },
                    name = w.name,
                    notes = w.notes,
                    startedAt = w.startedAt,
                    endedAt = w.endedAt,
                    durationSec = w.durationSec,
                    localDate = w.localDate,
                    totalVolumeKg = w.totalVolumeKg,
                    totalSets = w.totalSets,
                    prCount = w.prCount,
                    isInProgress = w.isInProgress,
                    profileId = null,
                ),
            )
        workoutIdMap[w.id] = newWorkoutId
        w.exercises.forEach { we ->
            val exId = exerciseIdMap[we.exerciseId] ?: return@forEach
            val newWeId =
                workoutDao.insertExercise(
                    WorkoutExerciseEntity(
                        workoutId = newWorkoutId,
                        exerciseId = exId,
                        sortOrder = we.sortOrder,
                        supersetGroupId = null,
                        notes = we.notes,
                        restSec = we.restSec,
                    ),
                )
            we.sets.forEach { s ->
                val newSetId =
                    workoutDao.insertSet(
                        WorkoutSetEntity(
                            workoutExerciseId = newWeId,
                            sortOrder = s.sortOrder,
                            setType = SetType.valueOf(s.setType),
                            weightKg = s.weightKg,
                            reps = s.reps,
                            distanceM = s.distanceM,
                            durationSec = s.durationSec,
                            rpe = s.rpe,
                            rir = s.rir,
                            isCompleted = s.isCompleted,
                            completedAt = s.completedAt,
                            estimated1RmKg = s.estimated1RmKg,
                        ),
                    )
                workoutSetIdMap[s.id] = newSetId
            }
        }
    }
}

private fun io.github.ntufar.kroton.model.UserProfile?.toBackup(): BackupProfile {
    val profile = this
    return BackupProfile(
        heightCm = profile?.heightCm,
        birthDate = profile?.birthDate,
        sex = profile?.sex?.name,
        weightUnit = profile?.weightUnit ?: "kg",
        lengthUnit = profile?.lengthUnit ?: "cm",
        distanceUnit = profile?.distanceUnit ?: "km",
        defaultRestSec = profile?.defaultRestSec ?: DEFAULT_REST_SEC,
        firstDayOfWeek = profile?.firstDayOfWeek ?: 1,
        oneRmFormula = (profile?.oneRmFormula ?: io.github.ntufar.kroton.model.OneRmFormula.EPLEY).name,
        theme = profile?.theme ?: "dark",
        dynamicColour = profile?.dynamicColour ?: true,
        countWarmupsInVolume = profile?.countWarmupsInVolume ?: false,
        secondaryMuscleCredit = profile?.secondaryMuscleCredit ?: 0.5,
    )
}

private fun BackupProfile.toModel() =
    io.github.ntufar.kroton.model.UserProfile(
        heightCm = heightCm,
        birthDate = birthDate,
        sex = sex?.let { io.github.ntufar.kroton.model.Sex.valueOf(it) },
        weightUnit = weightUnit,
        lengthUnit = lengthUnit,
        distanceUnit = distanceUnit,
        defaultRestSec = defaultRestSec,
        firstDayOfWeek = firstDayOfWeek,
        oneRmFormula = io.github.ntufar.kroton.model.OneRmFormula.valueOf(oneRmFormula),
        theme = theme,
        dynamicColour = dynamicColour,
        countWarmupsInVolume = countWarmupsInVolume,
        secondaryMuscleCredit = secondaryMuscleCredit,
    )
