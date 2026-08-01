package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ProfileDao
import io.github.ntufar.kroton.database.UserProfileEntity
import io.github.ntufar.kroton.model.OneRmFormula
import io.github.ntufar.kroton.model.Sex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Owns the single-row `user_profile` table (spec §11.4: nullable `profile_id` elsewhere is
 * future multi-profile groundwork, but v1 has exactly one). Full settings read/write lands with
 * M6; this covers the minimum M4 needs — height/sex for derived body-composition metrics. */
class ProfileRepository(private val profileDao: ProfileDao) {
    fun observe(): Flow<io.github.ntufar.kroton.model.UserProfile?> = profileDao.observe().map { it?.toModel() }

    suspend fun get(): io.github.ntufar.kroton.model.UserProfile? = profileDao.get()?.toModel()

    suspend fun ensureSeeded() {
        if (profileDao.get() != null) return
        profileDao.insert(defaultProfile())
    }

    suspend fun updateHeightAndSex(
        heightCm: Double,
        sex: Sex,
    ) {
        val existing = profileDao.get() ?: defaultProfile()
        profileDao.update(existing.copy(heightCm = heightCm, sex = sex))
    }

    private fun defaultProfile() =
        UserProfileEntity(
            heightCm = null,
            birthDate = null,
            sex = null,
            weightUnit = "kg",
            lengthUnit = "cm",
            distanceUnit = "km",
            defaultRestSec = DEFAULT_REST_SEC,
            firstDayOfWeek = 1,
            oneRmFormula = OneRmFormula.EPLEY,
            theme = "dark",
            dynamicColour = true,
            countWarmupsInVolume = false,
            secondaryMuscleCredit = DEFAULT_SECONDARY_MUSCLE_CREDIT,
        )

    private companion object {
        const val DEFAULT_SECONDARY_MUSCLE_CREDIT = 0.5
    }
}

private fun UserProfileEntity.toModel() =
    io.github.ntufar.kroton.model.UserProfile(
        id = id,
        heightCm = heightCm,
        birthDate = birthDate,
        sex = sex,
        weightUnit = weightUnit,
        lengthUnit = lengthUnit,
        distanceUnit = distanceUnit,
        defaultRestSec = defaultRestSec,
        firstDayOfWeek = firstDayOfWeek,
        oneRmFormula = oneRmFormula,
        theme = theme,
        dynamicColour = dynamicColour,
        countWarmupsInVolume = countWarmupsInVolume,
        secondaryMuscleCredit = secondaryMuscleCredit,
    )
