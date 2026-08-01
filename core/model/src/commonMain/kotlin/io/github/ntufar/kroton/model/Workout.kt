package io.github.ntufar.kroton.model

data class Workout(
    val id: Long = 0,
    val routineId: Long? = null,
    val name: String,
    val notes: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSec: Int,
    val localDate: Int,
    val totalVolumeKg: Double = 0.0,
    val totalSets: Int = 0,
    val prCount: Int = 0,
    val isInProgress: Boolean,
    val profileId: Long? = null,
)

data class WorkoutExercise(
    val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val supersetGroupId: Long? = null,
    val notes: String? = null,
    val restSec: Int? = null,
)

data class WorkoutSet(
    val id: Long = 0,
    val workoutExerciseId: Long,
    val sortOrder: Int,
    val setType: SetType,
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
