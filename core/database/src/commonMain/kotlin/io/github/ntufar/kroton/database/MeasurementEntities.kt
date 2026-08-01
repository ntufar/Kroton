package io.github.ntufar.kroton.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.ntufar.kroton.model.PhotoPose
import io.github.ntufar.kroton.model.UnitKind

@Entity(tableName = "measurement_type")
data class MeasurementTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val displayName: String,
    val unitKind: UnitKind,
    val isBuiltin: Boolean,
    val isEnabled: Boolean,
    val sortOrder: Int,
    val decimals: Int,
)

@Entity(
    tableName = "measurement_entry",
    indices = [Index(value = ["typeId", "localDate"], unique = true)],
)
data class MeasurementEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val typeId: Long,
    val value: Double,
    val recordedAt: Long,
    val localDate: Int,
    val note: String?,
    val profileId: Long?,
)

@Entity(tableName = "progress_photo")
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAt: Long,
    val localDate: Int,
    val fileName: String,
    val pose: PhotoPose,
    val note: String?,
)
