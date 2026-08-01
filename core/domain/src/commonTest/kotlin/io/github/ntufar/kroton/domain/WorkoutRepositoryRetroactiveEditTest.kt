package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.ExerciseEntity
import io.github.ntufar.kroton.model.Equipment
import io.github.ntufar.kroton.model.ExerciseType
import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.RecordType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Spec §5.4: "Retroactive edits must recompute denormalised totals and re-derive affected PRs."
 * `personal_record` is append-only (§3), so this exercises the self-healing behaviour described
 * on `WorkoutRepository.editCompletedSet`: editing down a set that held the current best MAX_WEIGHT
 * PR must cause the ledger to fall back to the next-best surviving set, not just delete the PR. */
class WorkoutRepositoryRetroactiveEditTest {
    private class Fixture {
        val workoutDao = FakeWorkoutDao()
        val exerciseDao = FakeExerciseDao()
        val recordDao = FakeRecordDao()
        val inventoryDao = FakeInventoryDao()
        val repository = WorkoutRepository(workoutDao, exerciseDao, recordDao, inventoryDao)
    }

    @Test
    fun editingDownAPrSetPromotesTheNextBestSet() =
        runTest {
            val fixture = Fixture()
            val repository = fixture.repository
            val workoutDao = fixture.workoutDao
            val exerciseDao = fixture.exerciseDao
            val recordDao = fixture.recordDao
            val exerciseId =
                exerciseDao.insert(
                    ExerciseEntity(
                        name = "Bench Press",
                        nameNormalised = "bench press",
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        equipment = Equipment.BARBELL,
                        primaryMuscle = MuscleGroup.CHEST,
                        force = null,
                        mechanic = null,
                        isCustom = false,
                        isArchived = false,
                        defaultRestSec = null,
                        instructions = null,
                        seedUuid = null,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                )

            // Session 1: an 80kg set — the historical second-best once the 100kg PR is edited down.
            val workout1Id = repository.startEmptyWorkout(nowMs = 1_000L, localDate = 20260101)
            val workoutExercise1Id = repository.addExercise(workout1Id, exerciseId)
            val set1Id = repository.addSet(workoutExercise1Id)
            repository.updateSetValues(set1Id, weightKg = 80.0, reps = 5)
            repository.completeSet(set1Id, nowMs = 1_100L)

            // Session 2: a 100kg set — becomes the MAX_WEIGHT PR.
            val workout2Id = repository.startEmptyWorkout(nowMs = 2_000L, localDate = 20260102)
            val workoutExercise2Id = repository.addExercise(workout2Id, exerciseId)
            val set2Id = repository.addSet(workoutExercise2Id)
            repository.updateSetValues(set2Id, weightKg = 100.0, reps = 5)
            repository.completeSet(set2Id, nowMs = 2_100L)

            workoutDao.getById(workout1Id)?.let { workoutDao.update(it.copy(isInProgress = false)) }
            workoutDao.getById(workout2Id)?.let { workoutDao.update(it.copy(isInProgress = false)) }

            val bestBeforeEdit = requireNotNull(recordDao.getBest(exerciseId, RecordType.MAX_WEIGHT)).value
            assertEquals(100.0, bestBeforeEdit)

            // Retroactively edit the 100kg PR set down to 70kg — below the 80kg session-1 set.
            repository.editCompletedSet(set2Id, weightKg = 70.0, reps = 5, nowMs = 3_000L)

            val bestAfterEdit = requireNotNull(recordDao.getBest(exerciseId, RecordType.MAX_WEIGHT)).value
            assertEquals(80.0, bestAfterEdit, "the 80kg set from session 1 should become the new MAX_WEIGHT record")
        }
}
