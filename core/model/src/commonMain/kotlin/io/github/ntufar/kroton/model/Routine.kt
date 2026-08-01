package io.github.ntufar.kroton.model

data class RoutineFolder(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int,
)

data class Routine(
    val id: Long = 0,
    val folderId: Long? = null,
    val name: String,
    val notes: String? = null,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPerformedAt: Long? = null,
)

data class RoutineExercise(
    val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val supersetGroupId: Long? = null,
    val restSec: Int? = null,
    val notes: String? = null,
)

data class RoutineSet(
    val id: Long = 0,
    val routineExerciseId: Long,
    val sortOrder: Int,
    val setType: SetType,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetWeightKg: Double? = null,
    val targetRpe: Double? = null,
)
