package io.github.ntufar.kroton.model

data class Exercise(
    val id: Long = 0,
    val name: String,
    val nameNormalised: String,
    val exerciseType: ExerciseType,
    val equipment: Equipment,
    val primaryMuscle: MuscleGroup,
    val force: Force?,
    val mechanic: Mechanic?,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val defaultRestSec: Int? = null,
    val instructions: String? = null,
    val seedUuid: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class ExerciseSecondaryMuscle(
    val exerciseId: Long,
    val muscle: MuscleGroup,
)
