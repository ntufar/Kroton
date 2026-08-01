package io.github.ntufar.kroton.export

import kotlinx.serialization.Serializable

/** Versioned, complete, lossless round-trip format (spec §6.3) — the actual backup; XLSX/CSV are
 * for analysis only and are derived from this same tree by `XlsxWriter`/`CsvWriter`. */
@Serializable
data class BackupJson(
    val format: String = "kroton-backup",
    val formatVersion: Int = 1,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val profile: BackupProfile,
    val exercises: List<BackupExercise> = emptyList(),
    val routineFolders: List<BackupRoutineFolder> = emptyList(),
    val routines: List<BackupRoutine> = emptyList(),
    val workouts: List<BackupWorkout> = emptyList(),
    val measurementTypes: List<BackupMeasurementType> = emptyList(),
    val measurements: List<BackupMeasurement> = emptyList(),
    val records: List<BackupRecord> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
)

@Serializable
data class BackupProfile(
    val heightCm: Double? = null,
    val birthDate: Int? = null,
    val sex: String? = null,
    val weightUnit: String,
    val lengthUnit: String,
    val distanceUnit: String,
    val defaultRestSec: Int,
    val firstDayOfWeek: Int,
    val oneRmFormula: String,
    val theme: String,
    val dynamicColour: Boolean,
    val countWarmupsInVolume: Boolean,
    val secondaryMuscleCredit: Double,
)

@Serializable
data class BackupExercise(
    val id: Long,
    val name: String,
    val nameNormalised: String,
    val exerciseType: String,
    val equipment: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String> = emptyList(),
    val force: String? = null,
    val mechanic: String? = null,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val defaultRestSec: Int? = null,
    val instructions: String? = null,
    val seedUuid: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupRoutineFolder(val id: Long, val name: String, val sortOrder: Int)

@Serializable
data class BackupRoutine(
    val id: Long,
    val folderId: Long? = null,
    val name: String,
    val notes: String? = null,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPerformedAt: Long? = null,
    val exercises: List<BackupRoutineExercise> = emptyList(),
)

@Serializable
data class BackupRoutineExercise(
    val id: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val supersetGroupId: Long? = null,
    val restSec: Int? = null,
    val notes: String? = null,
    val sets: List<BackupRoutineSet> = emptyList(),
)

@Serializable
data class BackupRoutineSet(
    val id: Long,
    val sortOrder: Int,
    val setType: String,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeightKg: Double? = null,
    val targetRpe: Double? = null,
)

@Serializable
data class BackupWorkout(
    val id: Long,
    val routineId: Long? = null,
    val name: String,
    val notes: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSec: Int,
    val localDate: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val prCount: Int,
    val isInProgress: Boolean,
    val exercises: List<BackupWorkoutExercise> = emptyList(),
)

@Serializable
data class BackupWorkoutExercise(
    val id: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val supersetGroupId: Long? = null,
    val notes: String? = null,
    val restSec: Int? = null,
    val sets: List<BackupWorkoutSet> = emptyList(),
)

@Serializable
data class BackupWorkoutSet(
    val id: Long,
    val sortOrder: Int,
    val setType: String,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val distanceM: Double? = null,
    val durationSec: Int? = null,
    val rpe: Double? = null,
    val rir: Int? = null,
    val isCompleted: Boolean,
    val completedAt: Long? = null,
    val estimated1RmKg: Double? = null,
)

@Serializable
data class BackupMeasurementType(
    val id: Long,
    val key: String,
    val displayName: String,
    val unitKind: String,
    val isBuiltin: Boolean,
    val isEnabled: Boolean,
    val sortOrder: Int,
    val decimals: Int,
)

@Serializable
data class BackupMeasurement(
    val id: Long,
    val typeId: Long,
    val value: Double,
    val recordedAt: Long,
    val localDate: Int,
    val note: String? = null,
)

@Serializable
data class BackupRecord(
    val id: Long,
    val exerciseId: Long,
    val recordType: String,
    val value: Double,
    val workoutSetId: Long? = null,
    val workoutId: Long,
    val achievedAt: Long,
)

@Serializable
data class BackupPhoto(
    val id: Long,
    val recordedAt: Long,
    val localDate: Int,
    val fileName: String,
    val pose: String,
    val note: String? = null,
)
