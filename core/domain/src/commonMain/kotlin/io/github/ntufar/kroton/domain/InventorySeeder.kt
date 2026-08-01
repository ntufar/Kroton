package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.BarInventoryEntity
import io.github.ntufar.kroton.database.InventoryDao
import io.github.ntufar.kroton.database.PlateInventoryEntity

/** Populates a common plate/bar set on first launch, editable afterwards in Settings. */
class InventorySeeder(private val inventoryDao: InventoryDao) {
    suspend fun seedIfEmpty() {
        if (inventoryDao.countPlates() == 0) {
            DEFAULT_PLATES_KG.forEach { (weightKg, count) ->
                inventoryDao.insertPlate(PlateInventoryEntity(plateKg = weightKg, count = count, isEnabled = true))
            }
        }
        if (inventoryDao.countBars() == 0) {
            inventoryDao.insertBar(BarInventoryEntity(name = "Barbell", weightKg = DEFAULT_BAR_KG, isDefault = true))
        }
    }

    private companion object {
        const val DEFAULT_BAR_KG = 20.0
        val DEFAULT_PLATES_KG =
            listOf(
                25.0 to 4,
                20.0 to 4,
                15.0 to 2,
                10.0 to 4,
                5.0 to 4,
                2.5 to 4,
                1.25 to 2,
            )
    }
}
