package io.github.ntufar.kroton.model

data class MeasurementType(
    val id: Long = 0,
    val key: String,
    val displayName: String,
    val unitKind: UnitKind,
    val isBuiltin: Boolean,
    val isEnabled: Boolean,
    val sortOrder: Int,
    val decimals: Int,
)

data class MeasurementEntry(
    val id: Long = 0,
    val typeId: Long,
    val value: Double,
    val recordedAt: Long,
    val localDate: Int,
    val note: String? = null,
    val profileId: Long? = null,
)

data class ProgressPhoto(
    val id: Long = 0,
    val recordedAt: Long,
    val localDate: Int,
    val fileName: String,
    val pose: PhotoPose,
    val note: String? = null,
)

object BuiltinMeasurementKeys {
    val ALL =
        listOf(
            "body_weight", "body_fat_pct", "neck", "shoulders", "chest", "waist",
            "abdomen", "hips", "bicep_left", "bicep_right", "forearm_left",
            "forearm_right", "thigh_left", "thigh_right", "calf_left", "calf_right",
            "resting_hr",
        )
}
