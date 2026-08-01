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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkouts(workouts: List<WorkoutEntity>): List<Long>

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

    @Query("SELECT * FROM workout WHERE isInProgress = 0 ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<WorkoutEntity>>

    @Query("SELECT DISTINCT localDate FROM workout WHERE isInProgress = 0 AND localDate BETWEEN :startLocalDate AND :endLocalDate")
    suspend fun getTrainedLocalDates(
        startLocalDate: Int,
        endLocalDate: Int,
    ): List<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(workoutExercise: WorkoutExerciseEntity): Long

    /** Room batches a list-parameter `@Insert` into a single transaction — used by the M5
     * 60k-set performance-gate test to seed its synthetic dataset in bounded time. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercises(workoutExercises: List<WorkoutExerciseEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(workoutSet: WorkoutSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSets(workoutSets: List<WorkoutSetEntity>): List<Long>

    @Update
    suspend fun updateSet(workoutSet: WorkoutSetEntity)

    @Query("SELECT * FROM workout_exercise WHERE workoutId = :workoutId ORDER BY sortOrder ASC")
    suspend fun getExercisesForWorkout(workoutId: Long): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercise WHERE id = :id")
    suspend fun getExerciseById(id: Long): WorkoutExerciseEntity?

    @Update
    suspend fun updateExercise(workoutExercise: WorkoutExerciseEntity)

    @Query("SELECT * FROM workout_set WHERE workoutExerciseId = :workoutExerciseId ORDER BY sortOrder ASC")
    suspend fun getSetsForExercise(workoutExerciseId: Long): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_set WHERE id = :id")
    suspend fun getSetById(id: Long): WorkoutSetEntity?

    @Delete
    suspend fun deleteSet(workoutSet: WorkoutSetEntity)

    @Delete
    suspend fun deleteExercise(workoutExercise: WorkoutExerciseEntity)

    @Query(
        """
        SELECT ws.* FROM workout_set ws
        WHERE ws.workoutExerciseId = (
            SELECT we.id FROM workout_exercise we
            JOIN workout w ON we.workoutId = w.id
            WHERE we.exerciseId = :exerciseId AND w.id != :excludeWorkoutId AND w.isInProgress = 0
            ORDER BY w.startedAt DESC LIMIT 1
        )
        ORDER BY ws.sortOrder ASC
        """,
    )
    suspend fun getMostRecentSets(
        exerciseId: Long,
        excludeWorkoutId: Long,
    ): List<WorkoutSetEntity>
}
