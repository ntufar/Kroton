package io.github.ntufar.kroton.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ntufar.kroton.domain.ActiveWorkoutSnapshot
import io.github.ntufar.kroton.domain.DEFAULT_REST_SEC
import io.github.ntufar.kroton.domain.PlateCalculator
import io.github.ntufar.kroton.domain.PlateCalculatorResult
import io.github.ntufar.kroton.domain.RestTimerController
import io.github.ntufar.kroton.domain.RestTimerState
import io.github.ntufar.kroton.domain.WorkoutRepository
import io.github.ntufar.kroton.domain.WorkoutSummary
import io.github.ntufar.kroton.model.Exercise
import io.github.ntufar.kroton.model.RecordType
import io.github.ntufar.kroton.model.SetType
import io.github.ntufar.kroton.model.WorkoutSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ExerciseOverflowState(
    val workoutExerciseId: Long,
    val notesDraft: String,
    val restSecDraft: String,
    val isReplacing: Boolean = false,
    val inlineHistory: List<WorkoutSet> = emptyList(),
    val plateTargetKg: String = "",
    val plateResult: PlateCalculatorResult? = null,
)

data class DeletedSetSnapshot(
    val workoutExerciseId: Long,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
)

data class WorkoutUiState(
    val isLoading: Boolean = true,
    val activeWorkout: ActiveWorkoutSnapshot? = null,
    val allExercises: List<Exercise> = emptyList(),
    val isExercisePickerOpen: Boolean = false,
    val lastEarnedRecordTypes: List<RecordType> = emptyList(),
    val summary: WorkoutSummary? = null,
    val restTimer: RestTimerState? = null,
    val finishNotes: String = "",
    val selectionModeActive: Boolean = false,
    val selectedExerciseIds: Set<Long> = emptySet(),
    val keepScreenOn: Boolean = false,
    val overflow: ExerciseOverflowState? = null,
    val lastDeletedSet: DeletedSetSnapshot? = null,
)

// The active-workout screen is spec'd as the single critical screen (§5.3) with many small,
// independent interactions (supersets, overflow menu, undo, ...); splitting into several
// ViewModels would fragment one StateFlow<WorkoutUiState> the UI depends on for no real benefit.
@Suppress("TooManyFunctions")
class WorkoutViewModel(
    private val repository: WorkoutRepository,
    private val restTimerController: RestTimerController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val id = repository.getInProgressWorkoutId()
            if (id != null) refresh(id) else _uiState.update { it.copy(isLoading = false) }
        }
        viewModelScope.launch {
            repository.observeExercises().collect { list ->
                _uiState.update { it.copy(allExercises = list) }
            }
        }
        viewModelScope.launch {
            restTimerController.state.collect { timer ->
                _uiState.update { it.copy(restTimer = timer) }
            }
        }
    }

    /** Pass a delta to nudge the running rest timer, or null to skip it entirely. */
    fun restTimerAction(deltaSec: Int?) {
        if (deltaSec == null) restTimerController.skip() else restTimerController.adjust(deltaSec)
    }

    fun startEmptyWorkout() {
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            val id = repository.startEmptyWorkout(nowMs = nowMs, localDate = todayAsLocalDate())
            refresh(id)
        }
    }

    fun setExercisePickerOpen(open: Boolean) = _uiState.update { it.copy(isExercisePickerOpen = open) }

    fun addExercise(exerciseId: Long) {
        val overflow = _uiState.value.overflow
        if (overflow != null && overflow.isReplacing) {
            viewModelScope.launch {
                repository.replaceExercise(overflow.workoutExerciseId, exerciseId)
                _uiState.update { it.copy(isExercisePickerOpen = false, overflow = null) }
                refreshCurrent()
            }
            return
        }
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            repository.addExercise(workoutId, exerciseId)
            _uiState.update { it.copy(isExercisePickerOpen = false) }
            refresh(workoutId)
        }
    }

    fun addSet(workoutExerciseId: Long) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            repository.addSet(workoutExerciseId)
            refresh(workoutId)
        }
    }

    fun deleteSet(set: WorkoutSet) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            repository.deleteSet(set.id)
            _uiState.update {
                it.copy(
                    lastDeletedSet =
                        DeletedSetSnapshot(
                            workoutExerciseId = set.workoutExerciseId,
                            setType = set.setType,
                            weightKg = set.weightKg,
                            reps = set.reps,
                        ),
                )
            }
            refresh(workoutId)
        }
    }

    /** Recreates the deleted row with its weight/reps/type, unchecked — a fresh, honest undo
     * rather than attempting to reconstruct completion timestamps or PR state. */
    fun undoDeleteSet() {
        val deleted = _uiState.value.lastDeletedSet ?: return
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            val newSetId = repository.addSet(deleted.workoutExerciseId)
            repository.updateSetValues(newSetId, deleted.weightKg, deleted.reps)
            if (deleted.setType != SetType.NORMAL) repository.setType(newSetId, deleted.setType)
            _uiState.update { it.copy(lastDeletedSet = null) }
            refresh(workoutId)
        }
    }

    fun dismissUndo() = _uiState.update { it.copy(lastDeletedSet = null) }

    fun changeSetType(
        setId: Long,
        setType: SetType,
    ) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            repository.setType(setId, setType)
            refresh(workoutId)
        }
    }

    /** Toggles a set's completion. On completion: persists the row's current values, computes the
     * 1RM estimate, checks live PRs, and starts the rest timer (per §5.3, automatic — no "save" step). */
    fun toggleSetCompletion(
        setId: Long,
        isCurrentlyCompleted: Boolean,
        weightKg: Double?,
        reps: Int?,
    ) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        val restSec =
            _uiState.value.activeWorkout?.exercises
                ?.firstOrNull { exercise -> exercise.sets.any { it.set.id == setId } }
                ?.workoutExercise?.restSec ?: DEFAULT_REST_SEC
        viewModelScope.launch {
            if (isCurrentlyCompleted) {
                repository.uncompleteSet(setId)
            } else {
                repository.updateSetValues(setId, weightKg, reps)
                val earned = repository.completeSet(setId, System.currentTimeMillis())
                _uiState.update { it.copy(lastEarnedRecordTypes = earned) }
                restTimerController.start(restSec)
            }
            refresh(workoutId)
        }
    }

    fun setFinishNotes(text: String) = _uiState.update { it.copy(finishNotes = text) }

    fun finishWorkout() {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        val notes = _uiState.value.finishNotes.ifBlank { null }
        viewModelScope.launch {
            val summary = repository.finishWorkout(workoutId, System.currentTimeMillis(), notes)
            _uiState.update { it.copy(summary = summary, activeWorkout = null, finishNotes = "") }
        }
    }

    fun dismissSummary() = _uiState.update { it.copy(summary = null) }

    fun toggleKeepScreenOn() = _uiState.update { it.copy(keepScreenOn = !it.keepScreenOn) }

    // --- Supersets (multi-select grouping) ---

    fun toggleSelectionMode() =
        _uiState.update {
            it.copy(selectionModeActive = !it.selectionModeActive, selectedExerciseIds = emptySet())
        }

    fun toggleExerciseSelected(workoutExerciseId: Long) =
        _uiState.update {
            val selected = it.selectedExerciseIds
            it.copy(
                selectedExerciseIds =
                    if (workoutExerciseId in selected) selected - workoutExerciseId else selected + workoutExerciseId,
            )
        }

    fun groupSelectedAsSuperset() {
        val ids = _uiState.value.selectedExerciseIds.toList()
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            repository.groupAsSuperset(ids)
            _uiState.update { it.copy(selectionModeActive = false, selectedExerciseIds = emptySet()) }
            refresh(workoutId)
        }
    }

    fun ungroupSuperset(workoutExerciseId: Long) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            repository.ungroupSuperset(workoutExerciseId)
            refresh(workoutId)
        }
    }

    fun moveExercise(
        workoutExerciseId: Long,
        deltaIndex: Int,
    ) {
        val snapshot = _uiState.value.activeWorkout ?: return
        val ids = snapshot.exercises.map { it.workoutExercise.id }.toMutableList()
        val index = ids.indexOf(workoutExerciseId)
        val target = index + deltaIndex
        if (index < 0 || target < 0 || target >= ids.size) return
        ids.add(target, ids.removeAt(index))
        viewModelScope.launch {
            repository.reorderExercises(snapshot.workout.id, ids)
            refresh(snapshot.workout.id)
        }
    }

    // --- Per-exercise overflow (replace / notes / rest time / plate calculator / inline history) ---

    fun openOverflow(workoutExerciseId: Long) {
        val exercise =
            _uiState.value.activeWorkout?.exercises?.firstOrNull { it.workoutExercise.id == workoutExerciseId }
                ?: return
        viewModelScope.launch {
            val history = repository.getInlineHistory(exercise.exerciseId, exercise.workoutExercise.workoutId)
            _uiState.update {
                it.copy(
                    overflow =
                        ExerciseOverflowState(
                            workoutExerciseId = workoutExerciseId,
                            notesDraft = exercise.workoutExercise.notes.orEmpty(),
                            restSecDraft = exercise.workoutExercise.restSec?.toString().orEmpty(),
                            inlineHistory = history,
                        ),
                )
            }
        }
    }

    fun closeOverflow() = _uiState.update { it.copy(overflow = null) }

    fun startReplacingExercise() {
        _uiState.update { it.copy(overflow = it.overflow?.copy(isReplacing = true), isExercisePickerOpen = true) }
    }

    fun updateOverflowNotesDraft(text: String) =
        _uiState.update { it.copy(overflow = it.overflow?.copy(notesDraft = text)) }

    fun saveOverflowNotes() {
        val overflow = _uiState.value.overflow ?: return
        viewModelScope.launch {
            repository.updateExerciseNotes(overflow.workoutExerciseId, overflow.notesDraft.ifBlank { null })
            refreshCurrent()
            closeOverflow()
        }
    }

    fun updateOverflowRestSecDraft(text: String) =
        _uiState.update { it.copy(overflow = it.overflow?.copy(restSecDraft = text)) }

    fun saveOverflowRestSec() {
        val overflow = _uiState.value.overflow ?: return
        viewModelScope.launch {
            repository.updateExerciseRestSec(overflow.workoutExerciseId, overflow.restSecDraft.toIntOrNull())
            refreshCurrent()
            closeOverflow()
        }
    }

    fun updateOverflowPlateTarget(text: String) {
        _uiState.update { it.copy(overflow = it.overflow?.copy(plateTargetKg = text)) }
        val targetKg = text.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val plates = repository.getPlateOptions()
            val barKg = repository.getDefaultBarKg()
            val result = PlateCalculator.solve(targetKg, barKg, plates)
            _uiState.update { it.copy(overflow = it.overflow?.copy(plateResult = result)) }
        }
    }

    private fun refreshCurrent() {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch { refresh(workoutId) }
    }

    private suspend fun refresh(workoutId: Long) {
        val snapshot = repository.getSnapshot(workoutId)
        _uiState.update { it.copy(isLoading = false, activeWorkout = snapshot) }
    }
}

private fun todayAsLocalDate(): Int = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
