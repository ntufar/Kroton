package io.github.ntufar.kroton.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlate(plate: PlateInventoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBar(bar: BarInventoryEntity): Long

    @Query("SELECT * FROM plate_inventory WHERE isEnabled = 1 ORDER BY plateKg DESC")
    suspend fun getEnabledPlates(): List<PlateInventoryEntity>

    @Query("SELECT * FROM bar_inventory WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultBar(): BarInventoryEntity?

    @Query("SELECT COUNT(*) FROM plate_inventory")
    suspend fun countPlates(): Int

    @Query("SELECT COUNT(*) FROM bar_inventory")
    suspend fun countBars(): Int
}
