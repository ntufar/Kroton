package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.database.BarInventoryEntity
import io.github.ntufar.kroton.database.ExerciseDao
import io.github.ntufar.kroton.database.ExerciseEntity
import io.github.ntufar.kroton.database.ExerciseSecondaryMuscleEntity
import io.github.ntufar.kroton.database.InventoryDao
import io.github.ntufar.kroton.database.PersonalRecordEntity
import io.github.ntufar.kroton.database.PlateInventoryEntity
import io.github.ntufar.kroton.database.RecordDao
import io.github.ntufar.kroton.database.WorkoutDao
import io.github.ntufar.kroton.database.WorkoutEntity
import io.github.ntufar.kroton.database.WorkoutExerciseEntity
import io.github.ntufar.kroton.database.WorkoutSetEntity
import io.github.ntufar.kroton.model.MuscleGroup
import io.github.ntufar.kroton.model.RecordType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-rolled in-memory DAOs so `WorkoutRepository` can be unit-tested without a real Room
 * database — the repository only depends on these DAO interfaces, never on Room directly. */
class FakeWorkoutDao : WorkoutDao {
    private var nextId = 1L
    val workouts = mutableMapOf<Long, WorkoutEntity>()
    val exercises = mutableMapOf<Long, WorkoutExerciseEntity>()
    val sets = mutableMapOf<Long, WorkoutSetEntity>()
    private val allFlow = MutableStateFlow<List<WorkoutEntity>>(emptyList())

    private fun nextId() = nextId++

    override suspend fun insert(workout: WorkoutEntity): Long {
        val id = nextId()
        workouts[id] = workout.copy(id = id)
        allFlow.value = workouts.values.sortedByDescending { it.startedAt }
        return id
    }

    override suspend fun insertWorkouts(workouts: List<WorkoutEntity>): List<Long> = workouts.map { insert(it) }

    override suspend fun update(workout: WorkoutEntity) {
        workouts[workout.id] = workout
        allFlow.value = workouts.values.sortedByDescending { it.startedAt }
    }

    override suspend fun delete(workout: WorkoutEntity) {
        workouts.remove(workout.id)
        allFlow.value = workouts.values.sortedByDescending { it.startedAt }
    }

    override suspend fun getById(id: Long): WorkoutEntity? = workouts[id]

    override suspend fun getInProgress(): WorkoutEntity? = workouts.values.firstOrNull { it.isInProgress }

    override fun observeAll(): Flow<List<WorkoutEntity>> = allFlow

    override fun observeFinished(): Flow<List<WorkoutEntity>> = allFlow

    override suspend fun getTrainedLocalDates(
        startLocalDate: Int,
        endLocalDate: Int,
    ): List<Int> =
        workouts.values.filter { !it.isInProgress && it.localDate in startLocalDate..endLocalDate }
            .map { it.localDate }.distinct()

    override suspend fun insertExercise(workoutExercise: WorkoutExerciseEntity): Long {
        val id = nextId()
        exercises[id] = workoutExercise.copy(id = id)
        return id
    }

    override suspend fun insertExercises(workoutExercises: List<WorkoutExerciseEntity>): List<Long> =
        workoutExercises.map { insertExercise(it) }

    override suspend fun insertSet(workoutSet: WorkoutSetEntity): Long {
        val id = nextId()
        sets[id] = workoutSet.copy(id = id)
        return id
    }

    override suspend fun insertSets(workoutSets: List<WorkoutSetEntity>): List<Long> = workoutSets.map { insertSet(it) }

    override suspend fun updateSet(workoutSet: WorkoutSetEntity) {
        sets[workoutSet.id] = workoutSet
    }

    override suspend fun getExercisesForWorkout(workoutId: Long): List<WorkoutExerciseEntity> =
        exercises.values.filter { it.workoutId == workoutId }.sortedBy { it.sortOrder }

    override suspend fun getExerciseById(id: Long): WorkoutExerciseEntity? = exercises[id]

    override suspend fun updateExercise(workoutExercise: WorkoutExerciseEntity) {
        exercises[workoutExercise.id] = workoutExercise
    }

    override suspend fun getSetsForExercise(workoutExerciseId: Long): List<WorkoutSetEntity> =
        sets.values.filter { it.workoutExerciseId == workoutExerciseId }.sortedBy { it.sortOrder }

    override suspend fun getSetById(id: Long): WorkoutSetEntity? = sets[id]

    override suspend fun deleteSet(workoutSet: WorkoutSetEntity) {
        sets.remove(workoutSet.id)
    }

    override suspend fun deleteExercise(workoutExercise: WorkoutExerciseEntity) {
        exercises.remove(workoutExercise.id)
    }

    override suspend fun getMostRecentSets(
        exerciseId: Long,
        excludeWorkoutId: Long,
    ): List<WorkoutSetEntity> {
        val mostRecentWorkoutExercise =
            exercises.values
                .filter { it.exerciseId == exerciseId }
                .mapNotNull { we -> workouts[we.workoutId]?.let { w -> we to w } }
                .filter { (_, w) -> w.id != excludeWorkoutId && !w.isInProgress }
                .maxByOrNull { (_, w) -> w.startedAt }
                ?.first
        return mostRecentWorkoutExercise?.let { we -> getSetsForExercise(we.id) } ?: emptyList()
    }

    override suspend fun clearSets() {
        sets.clear()
    }

    override suspend fun clearExercises() {
        exercises.clear()
    }

    override suspend fun clearWorkouts() {
        workouts.clear()
        allFlow.value = emptyList()
    }
}

class FakeRecordDao : RecordDao {
    private var nextId = 1L
    val records = mutableMapOf<Long, PersonalRecordEntity>()

    override suspend fun insert(record: PersonalRecordEntity): Long {
        val id = nextId++
        records[id] = record.copy(id = id)
        return id
    }

    override suspend fun getBest(
        exerciseId: Long,
        recordType: RecordType,
    ): PersonalRecordEntity? =
        records.values.filter { it.exerciseId == exerciseId && it.recordType == recordType }.maxByOrNull { it.value }

    override suspend fun getForSet(workoutSetId: Long): List<PersonalRecordEntity> =
        records.values.filter { it.workoutSetId == workoutSetId }

    override suspend fun deleteForSet(workoutSetId: Long) {
        records.values.filter { it.workoutSetId == workoutSetId }.forEach { records.remove(it.id) }
    }

    override suspend fun countForWorkout(workoutId: Long): Int = records.values.count { it.workoutId == workoutId }

    override suspend fun getAll(): List<PersonalRecordEntity> = records.values.toList()

    override suspend fun clearAll() {
        records.clear()
    }
}

class FakeExerciseDao : ExerciseDao {
    private var nextId = 1L
    val exercises = mutableMapOf<Long, ExerciseEntity>()
    private val secondaryMuscles = mutableMapOf<Long, MutableList<MuscleGroup>>()

    override suspend fun insert(exercise: ExerciseEntity): Long {
        val id = nextId++
        exercises[id] = exercise.copy(id = id)
        return id
    }

    override suspend fun update(exercise: ExerciseEntity) {
        exercises[exercise.id] = exercise
    }

    override suspend fun delete(exercise: ExerciseEntity) {
        exercises.remove(exercise.id)
    }

    override suspend fun getById(id: Long): ExerciseEntity? = exercises[id]

    override suspend fun getByNameNormalised(nameNormalised: String): ExerciseEntity? =
        exercises.values.firstOrNull { it.nameNormalised == nameNormalised }

    override suspend fun getBySeedUuid(seedUuid: String): ExerciseEntity? =
        exercises.values.firstOrNull {
            it.seedUuid == seedUuid
        }

    override fun observeAll(): Flow<List<ExerciseEntity>> = MutableStateFlow(exercises.values.toList())

    override fun search(query: String): Flow<List<ExerciseEntity>> =
        MutableStateFlow(exercises.values.filter { it.nameNormalised.contains(query) })

    override suspend fun count(): Int = exercises.size

    override suspend fun insertSecondaryMuscle(entity: ExerciseSecondaryMuscleEntity) {
        secondaryMuscles.getOrPut(entity.exerciseId) { mutableListOf() }.add(entity.muscle)
    }

    override suspend fun getSecondaryMuscles(exerciseId: Long): List<MuscleGroup> =
        secondaryMuscles[exerciseId].orEmpty()
}

class FakeInventoryDao : InventoryDao {
    val plates = mutableListOf<PlateInventoryEntity>()
    val bars = mutableListOf<BarInventoryEntity>()

    override suspend fun insertPlate(plate: PlateInventoryEntity): Long {
        plates += plate
        return plates.size.toLong()
    }

    override suspend fun insertBar(bar: BarInventoryEntity): Long {
        bars += bar
        return bars.size.toLong()
    }

    override suspend fun getEnabledPlates(): List<PlateInventoryEntity> = plates.filter { it.isEnabled }

    override suspend fun getDefaultBar(): BarInventoryEntity? = bars.firstOrNull { it.isDefault }

    override suspend fun countPlates(): Int = plates.size

    override suspend fun countBars(): Int = bars.size
}
