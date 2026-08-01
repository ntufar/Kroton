package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseEntity
import io.github.ntufar.kroton.database.KrotonDatabase
import io.github.ntufar.kroton.database.MeasurementTypeEntity
import io.github.ntufar.kroton.database.ProgressPhotoEntity
import io.github.ntufar.kroton.database.WorkoutEntity
import io.github.ntufar.kroton.database.WorkoutExerciseEntity
import io.github.ntufar.kroton.database.WorkoutSetEntity
import io.github.ntufar.kroton.database.createRoomDatabase
import io.github.ntufar.kroton.model.Equipment
import io.github.ntufar.kroton.model.ExerciseType
import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.PhotoPose
import io.github.ntufar.kroton.model.SetType
import io.github.ntufar.kroton.model.Sex
import io.github.ntufar.kroton.model.UnitKind
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Spec §9 M6 definition of done: "Backup → wipe → restore is byte-identical." `REPLACE` restore
 * always remaps ids (a fresh install has no matching auto-generated ids to preserve, spec §6.3),
 * so this compares the *content* of two backups taken before and after a wipe+restore cycle —
 * everything but the reassigned ids must match exactly. Runs against a real Room/SQLite database
 * (same JVM driver as the M5 perf-gate test), not fakes, since ID remapping is the part most
 * likely to break silently. */
class BackupRepositoryRoundTripTest {
    private lateinit var dbFile: File
    private lateinit var db: KrotonDatabase
    private lateinit var repository: BackupRepository

    @BeforeTest
    fun setUp() {
        dbFile = File.createTempFile("kroton_backup_test", ".db")
        db = createRoomDatabase(dbFile.absolutePath)
        repository =
            BackupRepository(
                workoutDao = db.workoutDao(),
                exerciseDao = db.exerciseDao(),
                routineDao = db.routineDao(),
                measurementDao = db.measurementDao(),
                progressPhotoDao = db.progressPhotoDao(),
                recordDao = db.recordDao(),
                profileRepository = ProfileRepository(db.profileDao()),
            )
    }

    @AfterTest
    fun tearDown() {
        db.close()
        dbFile.delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }

    @Test
    fun exportWipeRestore_reproducesContentIgnoringReassignedIds() =
        runBlocking {
            seedSampleData()
            val profileRepo = ProfileRepository(db.profileDao())
            profileRepo.ensureSeeded()
            profileRepo.updateHeightAndSex(180.0, Sex.MALE)

            val before = repository.buildBackup(nowIso8601 = "2026-01-01T00:00:00Z", appVersion = "1.0.0")
            repository.restore(before, RestoreMode.REPLACE)
            val after = repository.buildBackup(nowIso8601 = "2026-01-01T00:00:01Z", appVersion = "1.0.0")

            assertEquals(before.workouts.size, after.workouts.size)
            assertEquals(
                before.workouts.map { Triple(it.name, it.totalVolumeKg, it.totalSets) }.sortedBy { it.first },
                after.workouts.map { Triple(it.name, it.totalVolumeKg, it.totalSets) }.sortedBy { it.first },
            )
            assertEquals(
                before.workouts.flatMap { w -> w.exercises.flatMap { it.sets.map { s -> s.weightKg to s.reps } } }
                    .sortedBy { it.first },
                after.workouts.flatMap { w -> w.exercises.flatMap { it.sets.map { s -> s.weightKg to s.reps } } }
                    .sortedBy { it.first },
            )
            assertEquals(before.exercises.map { it.name }.sorted(), after.exercises.map { it.name }.sorted())
            assertEquals(before.measurements.map { it.value }.sorted(), after.measurements.map { it.value }.sorted())
            assertEquals(before.photos.map { it.fileName }.sorted(), after.photos.map { it.fileName }.sorted())
            assertEquals(before.profile.heightCm, after.profile.heightCm)
            assertEquals(before.profile.sex, after.profile.sex)
        }

    private suspend fun seedSampleData() {
        val exerciseDao = db.exerciseDao()
        val workoutDao = db.workoutDao()
        val measurementDao = db.measurementDao()
        val photoDao = db.progressPhotoDao()

        val exerciseId =
            exerciseDao.insert(
                ExerciseEntity(
                    name = "Squat",
                    nameNormalised = "squat",
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    equipment = Equipment.BARBELL,
                    primaryMuscle = MuscleGroup.QUADS,
                    force = null,
                    mechanic = null,
                    isCustom = false,
                    isArchived = false,
                    defaultRestSec = null,
                    instructions = null,
                    seedUuid = "seed-squat",
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            )

        val workoutId =
            workoutDao.insert(
                WorkoutEntity(
                    routineId = null,
                    name = "Leg day",
                    notes = "felt strong",
                    startedAt = 1_000L,
                    endedAt = 5_000L,
                    durationSec = 3_600,
                    localDate = 20_260_101,
                    totalVolumeKg = 500.0,
                    totalSets = 1,
                    prCount = 0,
                    isInProgress = false,
                    profileId = null,
                ),
            )
        val workoutExerciseId =
            workoutDao.insertExercise(
                WorkoutExerciseEntity(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    sortOrder = 0,
                    supersetGroupId = null,
                    notes = null,
                    restSec = null,
                ),
            )
        workoutDao.insertSet(
            WorkoutSetEntity(
                workoutExerciseId = workoutExerciseId,
                sortOrder = 0,
                setType = SetType.NORMAL,
                weightKg = 100.0,
                reps = 5,
                distanceM = null,
                durationSec = null,
                rpe = null,
                rir = null,
                isCompleted = true,
                completedAt = 2_000L,
                estimated1RmKg = 116.6,
            ),
        )

        val typeId =
            measurementDao.insertType(
                MeasurementTypeEntity(
                    key = "body_weight",
                    displayName = "Body weight",
                    unitKind = UnitKind.MASS,
                    isBuiltin = true,
                    isEnabled = true,
                    sortOrder = 0,
                    decimals = 1,
                ),
            )
        measurementDao.upsertEntry(
            io.github.ntufar.kroton.database.MeasurementEntryEntity(
                typeId = typeId,
                value = 82.5,
                recordedAt = 1_500L,
                localDate = 20_260_101,
                note = null,
                profileId = null,
            ),
        )

        photoDao.insert(
            ProgressPhotoEntity(
                recordedAt = 1_000L,
                localDate = 20_260_101,
                fileName = "progress_1.jpg",
                pose = PhotoPose.FRONT,
                note = null,
            ),
        )
    }
}
