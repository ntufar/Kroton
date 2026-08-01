package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseDao
import io.github.ntufar.kroton.database.ExerciseEntity
import io.github.ntufar.kroton.database.ExerciseSecondaryMuscleEntity

/** Populates the exercise catalogue on first launch. No-op once any exercise row exists. */
class ExerciseSeeder(private val exerciseDao: ExerciseDao) {
    suspend fun seedIfEmpty(nowMs: Long) {
        if (exerciseDao.count() > 0) return

        SeededExercises.all.forEach { seed ->
            val exerciseId =
                exerciseDao.insert(
                    ExerciseEntity(
                        name = seed.name,
                        nameNormalised = normaliseExerciseName(seed.name),
                        exerciseType = seed.exerciseType,
                        equipment = seed.equipment,
                        primaryMuscle = seed.primaryMuscle,
                        force = seed.force,
                        mechanic = seed.mechanic,
                        isCustom = false,
                        isArchived = false,
                        defaultRestSec = seed.defaultRestSec,
                        instructions = null,
                        seedUuid = seed.seedUuid,
                        createdAt = nowMs,
                        updatedAt = nowMs,
                    ),
                )
            seed.secondaryMuscles.forEach { muscle ->
                exerciseDao.insertSecondaryMuscle(ExerciseSecondaryMuscleEntity(exerciseId, muscle))
            }
        }
    }
}
