package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressPhotoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: ProgressPhotoEntity): Long

    @Delete
    suspend fun delete(photo: ProgressPhotoEntity)

    @Query("SELECT * FROM progress_photo WHERE id = :id")
    suspend fun getById(id: Long): ProgressPhotoEntity?

    @Query("SELECT * FROM progress_photo ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<ProgressPhotoEntity>>
}
