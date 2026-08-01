package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.MeasurementDao
import io.github.ntufar.kroton.database.MeasurementTypeEntity
import io.github.ntufar.kroton.model.BuiltinMeasurementKeys
import io.github.ntufar.kroton.model.UnitKind

private data class BuiltinType(val key: String, val displayName: String, val unitKind: UnitKind, val decimals: Int)

private val BUILTIN_TYPES =
    listOf(
        BuiltinType("body_weight", "Body weight", UnitKind.MASS, 1),
        BuiltinType("body_fat_pct", "Body fat %", UnitKind.PERCENT, 1),
        BuiltinType("neck", "Neck", UnitKind.LENGTH, 1),
        BuiltinType("shoulders", "Shoulders", UnitKind.LENGTH, 1),
        BuiltinType("chest", "Chest", UnitKind.LENGTH, 1),
        BuiltinType("waist", "Waist", UnitKind.LENGTH, 1),
        BuiltinType("abdomen", "Abdomen", UnitKind.LENGTH, 1),
        BuiltinType("hips", "Hips", UnitKind.LENGTH, 1),
        BuiltinType("bicep_left", "Bicep (left)", UnitKind.LENGTH, 1),
        BuiltinType("bicep_right", "Bicep (right)", UnitKind.LENGTH, 1),
        BuiltinType("forearm_left", "Forearm (left)", UnitKind.LENGTH, 1),
        BuiltinType("forearm_right", "Forearm (right)", UnitKind.LENGTH, 1),
        BuiltinType("thigh_left", "Thigh (left)", UnitKind.LENGTH, 1),
        BuiltinType("thigh_right", "Thigh (right)", UnitKind.LENGTH, 1),
        BuiltinType("calf_left", "Calf (left)", UnitKind.LENGTH, 1),
        BuiltinType("calf_right", "Calf (right)", UnitKind.LENGTH, 1),
        BuiltinType("resting_hr", "Resting heart rate", UnitKind.COUNT, 0),
    )

/** Populates the builtin measurement types (spec §3, `BuiltinMeasurementKeys`) on first launch.
 * No-op once any type row exists — matches `ExerciseSeeder`'s pattern. */
class MeasurementSeeder(private val measurementDao: MeasurementDao) {
    suspend fun seedIfEmpty() {
        if (measurementDao.countTypes() > 0) return
        check(BUILTIN_TYPES.map { it.key }.toSet() == BuiltinMeasurementKeys.ALL.toSet()) {
            "MeasurementSeeder's builtin list has drifted from BuiltinMeasurementKeys.ALL"
        }
        BUILTIN_TYPES.forEachIndexed { index, builtin ->
            measurementDao.insertType(
                MeasurementTypeEntity(
                    key = builtin.key,
                    displayName = builtin.displayName,
                    unitKind = builtin.unitKind,
                    isBuiltin = true,
                    isEnabled = builtin.key == "body_weight" || builtin.key == "body_fat_pct",
                    sortOrder = index,
                    decimals = builtin.decimals,
                ),
            )
        }
    }
}
