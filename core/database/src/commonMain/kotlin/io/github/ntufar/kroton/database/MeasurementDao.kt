package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertType(type: MeasurementTypeEntity): Long

    @Update
    suspend fun updateType(type: MeasurementTypeEntity)

    @Query("SELECT * FROM measurement_type WHERE id = :id")
    suspend fun getTypeById(id: Long): MeasurementTypeEntity?

    @Query("SELECT * FROM measurement_type WHERE key = :key")
    suspend fun getTypeByKey(key: String): MeasurementTypeEntity?

    @Query("SELECT * FROM measurement_type ORDER BY sortOrder ASC")
    fun observeAllTypes(): Flow<List<MeasurementTypeEntity>>

    @Query("SELECT * FROM measurement_type WHERE isEnabled = 1 ORDER BY sortOrder ASC")
    fun observeEnabledTypes(): Flow<List<MeasurementTypeEntity>>

    @Query("SELECT COUNT(*) FROM measurement_type")
    suspend fun countTypes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: MeasurementEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: MeasurementEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: MeasurementEntryEntity)

    @Query("SELECT * FROM measurement_entry WHERE id = :id")
    suspend fun getEntryById(id: Long): MeasurementEntryEntity?

    @Query("SELECT * FROM measurement_entry WHERE typeId = :typeId ORDER BY localDate DESC")
    fun observeEntriesForType(typeId: Long): Flow<List<MeasurementEntryEntity>>

    @Query("SELECT * FROM measurement_entry WHERE typeId = :typeId ORDER BY localDate DESC LIMIT :limit")
    suspend fun getRecentEntries(
        typeId: Long,
        limit: Int,
    ): List<MeasurementEntryEntity>

    @Query(
        "SELECT * FROM measurement_entry WHERE typeId = :typeId " +
            "ORDER BY ABS(recordedAt - :nearRecordedAt) ASC LIMIT 1",
    )
    suspend fun getNearestEntry(
        typeId: Long,
        nearRecordedAt: Long,
    ): MeasurementEntryEntity?

    @Query("SELECT * FROM measurement_entry")
    suspend fun getAllEntries(): List<MeasurementEntryEntity>

    @Query("DELETE FROM measurement_entry")
    suspend fun clearEntries()
}
