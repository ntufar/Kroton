package io.github.ntufar.kroton.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ntufar.kroton.model.OneRmFormula
import io.github.ntufar.kroton.model.RecordType
import io.github.ntufar.kroton.model.Sex

@Entity(
    tableName = "personal_record",
    indices = [Index(value = ["exerciseId", "recordType", "achievedAt"])],
)
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val recordType: RecordType,
    val value: Double,
    val workoutSetId: Long?,
    val workoutId: Long,
    val achievedAt: Long,
)

@Entity(
    tableName = "rep_max",
    primaryKeys = ["exerciseId", "reps"],
)
data class RepMaxEntity(
    val exerciseId: Long,
    val reps: Int,
    val weightKg: Double,
    val workoutSetId: Long,
    val achievedAt: Long,
)

@Entity(tableName = "plate_inventory")
data class PlateInventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plateKg: Double,
    val count: Int,
    val isEnabled: Boolean,
)

@Entity(tableName = "bar_inventory")
data class BarInventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weightKg: Double,
    val isDefault: Boolean,
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val heightCm: Double?,
    val birthDate: Int?,
    val sex: Sex?,
    val weightUnit: String,
    val lengthUnit: String,
    val distanceUnit: String,
    val defaultRestSec: Int,
    val firstDayOfWeek: Int,
    val oneRmFormula: OneRmFormula,
    val theme: String,
    val dynamicColour: Boolean,
    val countWarmupsInVolume: Boolean,
    val secondaryMuscleCredit: Double,
)
