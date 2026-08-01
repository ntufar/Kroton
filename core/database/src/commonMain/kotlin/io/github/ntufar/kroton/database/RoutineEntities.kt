package io.github.ntufar.kroton.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ntufar.kroton.model.SetType

@Entity(tableName = "routine_folder")
data class RoutineFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "routine",
    indices = [Index(value = ["folderId"])],
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long?,
    val name: String,
    val notes: String?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastPerformedAt: Long?,
)

@Entity(
    tableName = "routine_exercise",
    indices = [Index(value = ["routineId"]), Index(value = ["exerciseId"])],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val supersetGroupId: Long?,
    val restSec: Int?,
    val notes: String?,
)

@Entity(
    tableName = "routine_set",
    indices = [Index(value = ["routineExerciseId"])],
)
data class RoutineSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineExerciseId: Long,
    val sortOrder: Int,
    val setType: SetType,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeightKg: Double?,
    val targetRpe: Double?,
)
