package io.github.ntufar.kroton.export

import kotlinx.serialization.Serializable

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
)

@Serializable
data class BackupExercise(val id: Long, val name: String)

@Serializable
data class BackupRoutineFolder(val id: Long, val name: String)

@Serializable
data class BackupRoutine(val id: Long, val name: String)

@Serializable
data class BackupWorkout(val id: Long, val name: String, val startedAt: Long)

@Serializable
data class BackupMeasurementType(val id: Long, val key: String)

@Serializable
data class BackupMeasurement(val id: Long, val typeId: Long, val value: Double)

@Serializable
data class BackupRecord(val id: Long, val exerciseId: Long)

@Serializable
data class BackupPhoto(val id: Long, val fileName: String)
