package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.MeasurementDao
import io.github.ntufar.kroton.database.MeasurementEntryEntity
import io.github.ntufar.kroton.database.MeasurementTypeEntity
import io.github.ntufar.kroton.database.ProgressPhotoDao
import io.github.ntufar.kroton.database.ProgressPhotoEntity
import io.github.ntufar.kroton.model.MeasurementEntry
import io.github.ntufar.kroton.model.MeasurementType
import io.github.ntufar.kroton.model.PhotoPose
import io.github.ntufar.kroton.model.ProgressPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val CM_PER_M = 100.0
private const val SPARKLINE_POINTS = 14

/** Owns measurement types/entries, progress-photo metadata, and derived body-composition metrics
 * (spec §5.6, §4.4). Derived values are always computed alongside — never in place of — a
 * manually entered value. */
class MeasurementRepository(
    private val measurementDao: MeasurementDao,
    private val progressPhotoDao: ProgressPhotoDao,
    private val profileRepository: ProfileRepository,
) {
    fun observeEnabledTypes(): Flow<List<MeasurementType>> =
        measurementDao.observeEnabledTypes().map { list -> list.map { it.toModel() } }

    fun observeAllTypes(): Flow<List<MeasurementType>> =
        measurementDao.observeAllTypes().map { list -> list.map { it.toModel() } }

    suspend fun createCustomType(
        displayName: String,
        unitKind: io.github.ntufar.kroton.model.UnitKind,
        decimals: Int,
        nowMs: Long,
    ): Long {
        val sortOrder = measurementDao.countTypes()
        return measurementDao.insertType(
            MeasurementTypeEntity(
                key = "custom_$nowMs",
                displayName = displayName,
                unitKind = unitKind,
                isBuiltin = false,
                isEnabled = true,
                sortOrder = sortOrder,
                decimals = decimals,
            ),
        )
    }

    suspend fun setTypeEnabled(
        typeId: Long,
        enabled: Boolean,
    ) {
        val type = measurementDao.getTypeById(typeId) ?: return
        measurementDao.updateType(type.copy(isEnabled = enabled))
    }

    suspend fun reorderTypes(orderedTypeIds: List<Long>) {
        orderedTypeIds.forEachIndexed { index, id ->
            val type = measurementDao.getTypeById(id) ?: return@forEachIndexed
            measurementDao.updateType(type.copy(sortOrder = index))
        }
    }

    /** Quick-add / backdated add are the same write: the unique `(typeId, localDate)` index
     * (spec §3.6) means a second entry on the same day replaces the first rather than
     * duplicating, which is what both the "today card" and a corrected backdate expect. */
    suspend fun addOrReplaceEntry(
        typeId: Long,
        value: Double,
        recordedAt: Long,
        localDate: Int,
        note: String? = null,
    ): Long =
        measurementDao.upsertEntry(
            MeasurementEntryEntity(
                typeId = typeId,
                value = value,
                recordedAt = recordedAt,
                localDate = localDate,
                note = note,
                profileId = null,
            ),
        )

    suspend fun updateEntry(
        entryId: Long,
        value: Double,
        note: String?,
    ) {
        val entry = measurementDao.getEntryById(entryId) ?: return
        measurementDao.updateEntry(entry.copy(value = value, note = note))
    }

    suspend fun deleteEntry(entryId: Long) {
        val entry = measurementDao.getEntryById(entryId) ?: return
        measurementDao.deleteEntry(entry)
    }

    fun observeEntries(typeId: Long): Flow<List<MeasurementEntry>> =
        measurementDao.observeEntriesForType(typeId).map { list -> list.map { it.toModel() } }

    suspend fun summary(
        type: MeasurementType,
        sparklinePoints: Int = SPARKLINE_POINTS,
    ): MeasurementSummary {
        val recent = measurementDao.getRecentEntries(type.id, sparklinePoints)
        val latest = recent.firstOrNull()?.value
        val previous = recent.getOrNull(1)?.value
        return MeasurementSummary(
            type = type,
            latestValue = latest,
            deltaSinceLast = if (latest != null && previous != null) latest - previous else null,
            sparkline = recent.reversed().map { it.value },
        )
    }

    fun observePhotos(): Flow<List<ProgressPhoto>> =
        progressPhotoDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun addPhoto(
        fileName: String,
        pose: PhotoPose,
        note: String?,
        recordedAt: Long,
        localDate: Int,
    ): Long =
        progressPhotoDao.insert(
            ProgressPhotoEntity(
                recordedAt = recordedAt,
                localDate = localDate,
                fileName = fileName,
                pose = pose,
                note = note,
            ),
        )

    suspend fun deletePhoto(photoId: Long) {
        val photo = progressPhotoDao.getById(photoId) ?: return
        progressPhotoDao.delete(photo)
    }

    /** Navy BF%, lean/fat mass, FFMI and BMI from the profile's height/sex and each input's
     * nearest-in-time measurement entry (spec §4.1's "nearest in time" convention, reused here).
     * Returns null only when there's no profile or no bodyweight entry at all — individual
     * derived fields degrade to null independently when their own inputs are missing. */
    suspend fun derivedMetrics(nowMs: Long): DerivedMetrics? {
        val profile = profileRepository.get() ?: return null
        val heightCm = profile.heightCm ?: return null
        val sex = profile.sex ?: return null
        val heightM = heightCm / CM_PER_M

        val weightKg = nearestValue("body_weight", nowMs) ?: return null
        val waistCm = nearestValue("waist", nowMs)
        val neckCm = nearestValue("neck", nowMs)
        val hipsCm = nearestValue("hips", nowMs)

        val inputs = mutableMapOf("weight_kg" to weightKg, "height_cm" to heightCm)
        waistCm?.let { inputs["waist_cm"] = it }
        neckCm?.let { inputs["neck_cm"] = it }
        hipsCm?.let { inputs["hips_cm"] = it }

        val bodyFatPercent =
            if (waistCm != null && neckCm != null) {
                BodyCompositionCalculator.navyBodyFatPercent(sex, waistCm, neckCm, heightCm, hipsCm)
            } else {
                null
            }
        val leanMassKg = bodyFatPercent?.let { BodyCompositionCalculator.leanMassKg(weightKg, it) }
        val fatMassKg = bodyFatPercent?.let { BodyCompositionCalculator.fatMassKg(weightKg, it) }
        val ffmi = leanMassKg?.let { BodyCompositionCalculator.ffmi(it, heightM) }
        val normalisedFfmi = ffmi?.let { BodyCompositionCalculator.normalisedFfmi(it, heightM) }
        val bmi = BodyCompositionCalculator.bmi(weightKg, heightM)

        val weightType = measurementDao.getTypeByKey("body_weight")
        val recentWeights =
            weightType?.let { measurementDao.getRecentEntries(it.id, EMA_WINDOW_DAYS).reversed().map { e -> e.value } }
                .orEmpty()
        val bodyweightEma = BodyCompositionCalculator.exponentialMovingAverage(recentWeights).lastOrNull()

        return DerivedMetrics(
            navyBodyFatPercent = bodyFatPercent,
            leanMassKg = leanMassKg,
            fatMassKg = fatMassKg,
            ffmi = ffmi,
            normalisedFfmi = normalisedFfmi,
            bmi = bmi,
            bodyweightEma = bodyweightEma,
            inputsUsed = inputs,
        )
    }

    private suspend fun nearestValue(
        typeKey: String,
        nowMs: Long,
    ): Double? {
        val type = measurementDao.getTypeByKey(typeKey) ?: return null
        return measurementDao.getNearestEntry(type.id, nowMs)?.value
    }

    private companion object {
        const val EMA_WINDOW_DAYS = 30
    }
}

private fun MeasurementTypeEntity.toModel() =
    MeasurementType(
        id = id,
        key = key,
        displayName = displayName,
        unitKind = unitKind,
        isBuiltin = isBuiltin,
        isEnabled = isEnabled,
        sortOrder = sortOrder,
        decimals = decimals,
    )

private fun MeasurementEntryEntity.toModel() =
    MeasurementEntry(
        id = id,
        typeId = typeId,
        value = value,
        recordedAt = recordedAt,
        localDate = localDate,
        note = note,
        profileId = profileId,
    )

private fun ProgressPhotoEntity.toModel() =
    ProgressPhoto(
        id = id,
        recordedAt = recordedAt,
        localDate = localDate,
        fileName = fileName,
        pose = pose,
        note = note,
    )
