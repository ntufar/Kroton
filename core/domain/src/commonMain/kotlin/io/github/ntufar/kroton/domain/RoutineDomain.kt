package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.model.Routine
import io.github.ntufar.kroton.model.RoutineExercise
import io.github.ntufar.kroton.model.RoutineSet

data class RoutineExerciseDetail(
    val routineExercise: RoutineExercise,
    val exerciseId: Long,
    val exerciseName: String,
    val sets: List<RoutineSet>,
)

data class RoutineDetail(
    val routine: Routine,
    val exercises: List<RoutineExerciseDetail>,
)
