package io.github.ntufar.kroton.database

import androidx.room.TypeConverter
import io.github.ntufar.kroton.model.Equipment
import io.github.ntufar.kroton.model.ExerciseType
import io.github.ntufar.kroton.model.Force
import io.github.ntufar.kroton.model.Mechanic
import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.OneRmFormula
import io.github.ntufar.kroton.model.PhotoPose
import io.github.ntufar.kroton.model.RecordType
import io.github.ntufar.kroton.model.SetType
import io.github.ntufar.kroton.model.Sex
import io.github.ntufar.kroton.model.UnitKind

class Converters {
    @TypeConverter
    fun fromMuscleGroup(value: MuscleGroup): String = value.name

    @TypeConverter
    fun toMuscleGroup(value: String): MuscleGroup = MuscleGroup.valueOf(value)

    @TypeConverter
    fun fromExerciseType(value: ExerciseType): String = value.name

    @TypeConverter
    fun toExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)

    @TypeConverter
    fun fromEquipment(value: Equipment): String = value.name

    @TypeConverter
    fun toEquipment(value: String): Equipment = Equipment.valueOf(value)

    @TypeConverter
    fun fromForce(value: Force?): String? = value?.name

    @TypeConverter
    fun toForce(value: String?): Force? = value?.let { Force.valueOf(it) }

    @TypeConverter
    fun fromMechanic(value: Mechanic?): String? = value?.name

    @TypeConverter
    fun toMechanic(value: String?): Mechanic? = value?.let { Mechanic.valueOf(it) }

    @TypeConverter
    fun fromSetType(value: SetType): String = value.name

    @TypeConverter
    fun toSetType(value: String): SetType = SetType.valueOf(value)

    @TypeConverter
    fun fromRecordType(value: RecordType): String = value.name

    @TypeConverter
    fun toRecordType(value: String): RecordType = RecordType.valueOf(value)

    @TypeConverter
    fun fromOneRmFormula(value: OneRmFormula): String = value.name

    @TypeConverter
    fun toOneRmFormula(value: String): OneRmFormula = OneRmFormula.valueOf(value)

    @TypeConverter
    fun fromUnitKind(value: UnitKind): String = value.name

    @TypeConverter
    fun toUnitKind(value: String): UnitKind = UnitKind.valueOf(value)

    @TypeConverter
    fun fromPhotoPose(value: PhotoPose): String = value.name

    @TypeConverter
    fun toPhotoPose(value: String): PhotoPose = PhotoPose.valueOf(value)

    @TypeConverter
    fun fromSex(value: Sex?): String? = value?.name

    @TypeConverter
    fun toSex(value: String?): Sex? = value?.let { Sex.valueOf(it) }
}
