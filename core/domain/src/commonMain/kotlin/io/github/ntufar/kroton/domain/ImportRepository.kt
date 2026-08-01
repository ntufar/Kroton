package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseDao
import io.github.ntufar.kroton.database.ExerciseEntity
import io.github.ntufar.kroton.database.WorkoutDao
import io.github.ntufar.kroton.database.WorkoutEntity
import io.github.ntufar.kroton.database.WorkoutExerciseEntity
import io.github.ntufar.kroton.database.WorkoutSetEntity
import io.github.ntufar.kroton.export.ImportedWorkoutRow
import io.github.ntufar.kroton.model.Equipment
import io.github.ntufar.kroton.model.ExerciseType
import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.SetType
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class ImportPreview(
    val workoutCount: Int,
    val setCount: Int,
    val unmatchedExerciseNames: List<String>,
)

data class ImportResult(val workoutsImported: Int, val setsImported: Int, val exercisesCreated: Int)

/**
 * Writes Hevy/Strong CSV rows (already parsed by `HevyImport`/`StrongImport` in `core:export`)
 * into the DB (spec §6.6). Unmatched exercise names are auto-created as custom exercises rather
 * than routed through a manual mapping screen — a scope simplification versus the spec's
 * "fuzzy-suggest, or create as custom" mapping UI, documented in PROGRESS.md. Imported sets are
 * *not* run through live PR detection (`WorkoutRepository.completeSet`'s ledger logic) — a
 * bulk-historical import doesn't retroactively populate `personal_record`; that's a known
 * follow-up, not silently wrong data.
 */
class ImportRepository(
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
) {
    suspend fun preview(rows: List<ImportedWorkoutRow>): ImportPreview {
        val workouts = rows.groupBy { it.workoutTitle to it.rawStartTime }
        val unmatched =
            rows.map { it.exerciseName }.distinct().filter { name ->
                exerciseDao.getByNameNormalised(normaliseExerciseName(name)) == null
            }
        return ImportPreview(workoutCount = workouts.size, setCount = rows.size, unmatchedExerciseNames = unmatched)
    }

    suspend fun import(
        rows: List<ImportedWorkoutRow>,
        nowMs: Long,
    ): ImportResult {
        var exercisesCreated = 0
        val exerciseIdCache = mutableMapOf<String, Long>()

        suspend fun exerciseIdFor(name: String): Long {
            val normalised = normaliseExerciseName(name)
            exerciseIdCache[normalised]?.let { return it }
            val existing = exerciseDao.getByNameNormalised(normalised)
            val id =
                existing?.id ?: run {
                    exercisesCreated++
                    exerciseDao.insert(
                        ExerciseEntity(
                            name = name,
                            nameNormalised = normalised,
                            exerciseType = ExerciseType.WEIGHT_REPS,
                            equipment = Equipment.OTHER,
                            primaryMuscle = MuscleGroup.FULL_BODY,
                            force = null,
                            mechanic = null,
                            isCustom = true,
                            isArchived = false,
                            defaultRestSec = null,
                            instructions = null,
                            seedUuid = null,
                            createdAt = nowMs,
                            updatedAt = nowMs,
                        ),
                    )
                }
            exerciseIdCache[normalised] = id
            return id
        }

        var workoutsImported = 0
        var setsImported = 0
        rows.groupBy { it.workoutTitle to it.rawStartTime }.forEach { (key, workoutRows) ->
            val (title, rawStart) = key
            val startedAt = parseFlexibleDate(rawStart) ?: nowMs
            val endedAt = workoutRows.firstOrNull()?.rawEndTime?.let { parseFlexibleDate(it) }
            val localDate = epochMsToLocalDateInt(startedAt)
            val durationSec = endedAt?.let { ((it - startedAt) / MILLIS_PER_SECOND).toInt().coerceAtLeast(0) } ?: 0
            val workoutId =
                workoutDao.insert(
                    WorkoutEntity(
                        routineId = null,
                        name = title,
                        notes = null,
                        startedAt = startedAt,
                        endedAt = endedAt,
                        durationSec = durationSec,
                        localDate = localDate,
                        totalVolumeKg = 0.0,
                        totalSets = 0,
                        prCount = 0,
                        isInProgress = false,
                        profileId = null,
                    ),
                )
            workoutsImported++

            val exerciseGroups = workoutRows.groupBy { it.exerciseName }.entries
            exerciseGroups.forEachIndexed { exerciseSortOrder, (exerciseName, exerciseRows) ->
                val exerciseId = exerciseIdFor(exerciseName)
                val workoutExerciseId =
                    workoutDao.insertExercise(
                        WorkoutExerciseEntity(
                            workoutId = workoutId,
                            exerciseId = exerciseId,
                            sortOrder = exerciseSortOrder,
                            supersetGroupId = null,
                            notes = exerciseRows.firstOrNull()?.notes,
                            restSec = null,
                        ),
                    )
                exerciseRows.sortedBy { it.setIndex }.forEachIndexed { index, row ->
                    workoutDao.insertSet(
                        WorkoutSetEntity(
                            workoutExerciseId = workoutExerciseId,
                            sortOrder = index,
                            setType = mapSetType(row.setType),
                            weightKg = row.weightKg,
                            reps = row.reps,
                            distanceM = row.distanceM,
                            durationSec = row.durationSec,
                            rpe = row.rpe,
                            rir = null,
                            isCompleted = true,
                            completedAt = startedAt,
                            estimated1RmKg =
                                estimated1RmFor(row.weightKg, row.reps),
                        ),
                    )
                    setsImported++
                }
            }
        }
        return ImportResult(workoutsImported, setsImported, exercisesCreated)
    }

    private fun estimated1RmFor(
        weightKg: Double?,
        reps: Int?,
    ): Double? {
        if (weightKg == null || reps == null || reps !in 1..MAX_REPS_FOR_1RM) return null
        return OneRepMaxCalculator.estimate(weightKg, reps)
    }

    private fun mapSetType(raw: String?): SetType =
        when (raw?.lowercase()) {
            "warmup" -> SetType.WARMUP
            "drop", "dropset" -> SetType.DROP
            "failure" -> SetType.FAILURE
            else -> SetType.NORMAL
        }

    private fun epochMsToLocalDateInt(epochMs: Long): Int {
        val date = java.time.Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
        return date.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
    }

    private fun parseFlexibleDate(raw: String): Long? {
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "MM/dd/yyyy HH:mm", "yyyy-MM-dd")
        for (pattern in patterns) {
            try {
                val formatter = DateTimeFormatter.ofPattern(pattern)
                return if (pattern == "yyyy-MM-dd") {
                    java.time.LocalDate.parse(
                        raw,
                        formatter,
                    ).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } else {
                    LocalDateTime.parse(raw, formatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            } catch (e: DateTimeParseException) {
                continue
            }
        }
        return null
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val MAX_REPS_FOR_1RM = 12
    }
}
