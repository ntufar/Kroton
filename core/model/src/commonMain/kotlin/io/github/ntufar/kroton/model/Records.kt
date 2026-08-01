package io.github.ntufar.kroton.model

data class PersonalRecord(
    val id: Long = 0,
    val exerciseId: Long,
    val recordType: RecordType,
    val value: Double,
    val workoutSetId: Long? = null,
    val workoutId: Long,
    val achievedAt: Long,
)

data class RepMax(
    val exerciseId: Long,
    val reps: Int,
    val weightKg: Double,
    val workoutSetId: Long,
    val achievedAt: Long,
)

data class PlateInventory(
    val id: Long = 0,
    val plateKg: Double,
    val count: Int,
    val isEnabled: Boolean,
)

data class BarInventory(
    val id: Long = 0,
    val name: String,
    val weightKg: Double,
    val isDefault: Boolean,
)

data class UserProfile(
    val id: Long = 0,
    val heightCm: Double? = null,
    val birthDate: Int? = null,
    val sex: Sex? = null,
    val weightUnit: String = "KG",
    val lengthUnit: String = "CM",
    val distanceUnit: String = "KM",
    val defaultRestSec: Int = 90,
    val firstDayOfWeek: Int = 1,
    val oneRmFormula: OneRmFormula = OneRmFormula.EPLEY,
    val theme: String = "DARK",
    val dynamicColour: Boolean = true,
    val countWarmupsInVolume: Boolean = false,
    val secondaryMuscleCredit: Double = 0.5,
)
