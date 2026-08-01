package io.github.ntufar.kroton.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ntufar.kroton.model.Equipment
import io.github.ntufar.kroton.model.ExerciseType
import io.github.ntufar.kroton.model.Force
import io.github.ntufar.kroton.model.Mechanic
import io.github.ntufar.kroton.model.MuscleGroup

@Entity(
    tableName = "exercise",
    indices = [Index(value = ["nameNormalised"])],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameNormalised: String,
    val exerciseType: ExerciseType,
    val equipment: Equipment,
    val primaryMuscle: MuscleGroup,
    val force: Force?,
    val mechanic: Mechanic?,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val defaultRestSec: Int?,
    val instructions: String?,
    val seedUuid: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "exercise_secondary_muscle",
    primaryKeys = ["exerciseId", "muscle"],
    indices = [Index(value = ["exerciseId"])],
)
data class ExerciseSecondaryMuscleEntity(
    val exerciseId: Long,
    val muscle: MuscleGroup,
)
