package io.github.ntufar.kroton.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

const val KROTON_DATABASE_FILE_NAME = "kroton.db"
const val KROTON_DATABASE_VERSION = 1

@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseSecondaryMuscleEntity::class,
        RoutineFolderEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        RoutineSetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        MeasurementTypeEntity::class,
        MeasurementEntryEntity::class,
        ProgressPhotoEntity::class,
        PersonalRecordEntity::class,
        RepMaxEntity::class,
        PlateInventoryEntity::class,
        BarInventoryEntity::class,
        UserProfileEntity::class,
    ],
    version = KROTON_DATABASE_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(KrotonDatabaseConstructor::class)
abstract class KrotonDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutDao(): WorkoutDao
}

// androidx.room's KSP compiler generates the platform-specific `actual` bodies
// for this object per source set (androidMain, jvmMain, ...) — do not hand-write them.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object KrotonDatabaseConstructor : RoomDatabaseConstructor<KrotonDatabase>
