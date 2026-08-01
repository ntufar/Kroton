package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Single-row table (spec keeps a nullable `profile_id` for future multi-profile support, §11.4,
 * but v1 has exactly one profile) — every query targets id = 1. */
@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: UserProfileEntity): Long

    @Update
    suspend fun update(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfileEntity?>
}
