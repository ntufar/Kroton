package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.github.ntufar.kroton.model.RecordType

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: PersonalRecordEntity): Long

    @Query(
        "SELECT * FROM personal_record WHERE exerciseId = :exerciseId AND recordType = :recordType " +
            "ORDER BY value DESC LIMIT 1",
    )
    suspend fun getBest(
        exerciseId: Long,
        recordType: RecordType,
    ): PersonalRecordEntity?

    @Query("SELECT * FROM personal_record WHERE workoutSetId = :workoutSetId")
    suspend fun getForSet(workoutSetId: Long): List<PersonalRecordEntity>

    @Query("DELETE FROM personal_record WHERE workoutSetId = :workoutSetId")
    suspend fun deleteForSet(workoutSetId: Long)

    @Query("SELECT COUNT(*) FROM personal_record WHERE workoutId = :workoutId")
    suspend fun countForWorkout(workoutId: Long): Int
}
