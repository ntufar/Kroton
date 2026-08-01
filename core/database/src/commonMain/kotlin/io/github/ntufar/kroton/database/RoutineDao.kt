package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: RoutineFolderEntity): Long

    @Update
    suspend fun updateFolder(folder: RoutineFolderEntity)

    @Delete
    suspend fun deleteFolder(folder: RoutineFolderEntity)

    @Query("SELECT * FROM routine_folder ORDER BY sortOrder ASC")
    fun observeFolders(): Flow<List<RoutineFolderEntity>>

    @Query("SELECT * FROM routine_folder WHERE id = :id")
    suspend fun getFolderById(id: Long): RoutineFolderEntity?

    @Query("SELECT COUNT(*) FROM routine_folder")
    suspend fun countFolders(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: RoutineEntity)

    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun getRoutineById(id: Long): RoutineEntity?

    @Query("SELECT * FROM routine ORDER BY sortOrder ASC")
    fun observeAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT COUNT(*) FROM routine WHERE folderId IS :folderId")
    suspend fun countRoutinesInFolder(folderId: Long?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExercise(routineExercise: RoutineExerciseEntity): Long

    @Update
    suspend fun updateExercise(routineExercise: RoutineExerciseEntity)

    @Delete
    suspend fun deleteExercise(routineExercise: RoutineExerciseEntity)

    @Query("SELECT * FROM routine_exercise WHERE routineId = :routineId ORDER BY sortOrder ASC")
    suspend fun getExercisesForRoutine(routineId: Long): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercise WHERE id = :id")
    suspend fun getExerciseById(id: Long): RoutineExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSet(routineSet: RoutineSetEntity): Long

    @Update
    suspend fun updateSet(routineSet: RoutineSetEntity)

    @Delete
    suspend fun deleteSet(routineSet: RoutineSetEntity)

    @Query("SELECT * FROM routine_set WHERE id = :id")
    suspend fun getSetById(id: Long): RoutineSetEntity?

    @Query("SELECT * FROM routine_set WHERE routineExerciseId = :routineExerciseId ORDER BY sortOrder ASC")
    suspend fun getSetsForExercise(routineExerciseId: Long): List<RoutineSetEntity>
}
