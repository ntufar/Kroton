package io.github.ntufar.kroton.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ntufar.kroton.domain.ActiveWorkoutSnapshot
import io.github.ntufar.kroton.domain.WorkoutRepository
import io.github.ntufar.kroton.domain.WorkoutSummary
import io.github.ntufar.kroton.model.Exercise
import io.github.ntufar.kroton.model.RecordType
import io.github.ntufar.kroton.model.SetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class WorkoutUiState(
    val isLoading: Boolean = true,
    val activeWorkout: ActiveWorkoutSnapshot? = null,
    val allExercises: List<Exercise> = emptyList(),
    val isExercisePickerOpen: Boolean = false,
    val lastEarnedRecordTypes: List<RecordType> = emptyList(),
    val summary: WorkoutSummary? = null,
)

class WorkoutViewModel(private val repository: WorkoutRepository) : ViewModel() {
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

    fun deleteSet(setId: Long) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            repository.deleteSet(setId)
            refresh(workoutId)
        }
    }

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
     * 1RM estimate, and checks live PRs (rest-timer UI itself is still TODO, see PROGRESS.md). */
    fun toggleSetCompletion(
        setId: Long,
        isCurrentlyCompleted: Boolean,
        weightKg: Double?,
        reps: Int?,
    ) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            if (isCurrentlyCompleted) {
                repository.uncompleteSet(setId)
            } else {
                repository.updateSetValues(setId, weightKg, reps)
                val earned = repository.completeSet(setId, System.currentTimeMillis())
                _uiState.update { it.copy(lastEarnedRecordTypes = earned) }
            }
            refresh(workoutId)
        }
    }

    fun finishWorkout(notes: String?) {
        val workoutId = _uiState.value.activeWorkout?.workout?.id ?: return
        viewModelScope.launch {
            val summary = repository.finishWorkout(workoutId, System.currentTimeMillis(), notes)
            _uiState.update { it.copy(summary = summary, activeWorkout = null) }
        }
    }

    fun dismissSummary() = _uiState.update { it.copy(summary = null) }

    private suspend fun refresh(workoutId: Long) {
        val snapshot = repository.getSnapshot(workoutId)
        _uiState.update { it.copy(isLoading = false, activeWorkout = snapshot) }
    }
}

private fun todayAsLocalDate(): Int = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
