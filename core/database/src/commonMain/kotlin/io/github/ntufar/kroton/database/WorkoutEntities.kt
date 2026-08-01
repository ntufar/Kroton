package io.github.ntufar.kroton.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ntufar.kroton.model.SetType

@Entity(
    tableName = "workout",
    indices = [Index(value = ["startedAt"]), Index(value = ["localDate"])],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long?,
    val name: String,
    val notes: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val durationSec: Int,
    val localDate: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val prCount: Int,
    val isInProgress: Boolean,
    val profileId: Long?,
)

@Entity(
    tableName = "workout_exercise",
    indices = [Index(value = ["workoutId"]), Index(value = ["exerciseId"])],
)
data class WorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val supersetGroupId: Long?,
    val notes: String?,
    val restSec: Int?,
)

@Entity(
    tableName = "workout_set",
    indices = [
        Index(value = ["workoutExerciseId"]),
        Index(value = ["estimated1RmKg"]),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutExerciseId: Long,
    val sortOrder: Int,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val distanceM: Double?,
    val durationSec: Int?,
    val rpe: Double?,
    val rir: Int?,
    val isCompleted: Boolean,
    val completedAt: Long?,
    val estimated1RmKg: Double?,
)
