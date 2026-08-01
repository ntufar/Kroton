package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.ntufar.kroton.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE nameNormalised = :nameNormalised LIMIT 1")
    suspend fun getByNameNormalised(nameNormalised: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE seedUuid = :seedUuid LIMIT 1")
    suspend fun getBySeedUuid(seedUuid: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE isArchived = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE nameNormalised LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSecondaryMuscle(entity: ExerciseSecondaryMuscleEntity)

    @Query("SELECT muscle FROM exercise_secondary_muscle WHERE exerciseId = :exerciseId")
    suspend fun getSecondaryMuscles(exerciseId: Long): List<MuscleGroup>
}
