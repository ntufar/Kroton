package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Delete
    suspend fun delete(workout: WorkoutEntity)

    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workout WHERE isInProgress = 1 LIMIT 1")
    suspend fun getInProgress(): WorkoutEntity?

    @Query("SELECT * FROM workout ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(workoutExercise: WorkoutExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(workoutSet: WorkoutSetEntity): Long

    @Update
    suspend fun updateSet(workoutSet: WorkoutSetEntity)

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY sortOrder ASC")
    suspend fun getExercisesForWorkout(workoutId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY sortOrder ASC")
    suspend fun getSetsForExercise(workoutExerciseId: Long): List<WorkoutSetEntity>
}
