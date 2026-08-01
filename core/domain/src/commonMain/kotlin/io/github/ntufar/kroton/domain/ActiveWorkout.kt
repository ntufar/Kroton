package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.RecordType
import io.github.ntufar.kroton.model.Workout
import io.github.ntufar.kroton.model.WorkoutExercise
import io.github.ntufar.kroton.model.WorkoutSet

data class ActiveWorkoutSnapshot(
    val workout: Workout,
    val exercises: List<ActiveWorkoutExercise>,
)

data class ActiveWorkoutExercise(
    val workoutExercise: WorkoutExercise,
    val exerciseId: Long,
    val exerciseName: String,
    val sets: List<ActiveWorkoutSet>,
)

data class ActiveWorkoutSet(
    val set: WorkoutSet,
    val previousWeightKg: Double?,
    val previousReps: Int?,
    val prRecordTypes: List<RecordType>,
)

data class WorkoutSummary(
    val durationSec: Int,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val prCount: Int,
    val muscleBreakdown: Map<MuscleGroup, Double> = emptyMap(),
)
