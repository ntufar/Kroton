package io.github.ntufar.kroton.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ntufar.kroton.domain.RoutineDetail
import io.github.ntufar.kroton.domain.RoutineRepository
import io.github.ntufar.kroton.model.Exercise
import io.github.ntufar.kroton.model.Routine
import io.github.ntufar.kroton.model.RoutineFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class RoutineListUiState(
    val isLoading: Boolean = true,
    val folders: List<RoutineFolder> = emptyList(),
    val routines: List<Routine> = emptyList(),
    val exerciseSummaries: Map<Long, String> = emptyMap(),
    val editorDetail: RoutineDetail? = null,
    val isExercisePickerOpen: Boolean = false,
    val allExercises: List<Exercise> = emptyList(),
    val startedWorkoutId: Long? = null,
)

// Folder/routine CRUD, the routine editor, and start/save round trips are all small, independent
// interactions on one screen's StateFlow — see the same rationale on WorkoutViewModel.
@Suppress("TooManyFunctions")
class RoutinesViewModel(private val repository: RoutineRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutineListUiState())
    val uiState: StateFlow<RoutineListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeFolders(),
                repository.observeRoutines(),
            ) { folders, routines -> folders to routines }
                .collect { (folders, routines) ->
                    val summaries = routines.associate { it.id to repository.exerciseSummaryLine(it.id) }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            folders = folders,
                            routines = routines,
                            exerciseSummaries = summaries,
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.observeExercises().collect { list -> _uiState.update { it.copy(allExercises = list) } }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { repository.createFolder(name) }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch { repository.deleteFolder(folderId) }
    }

    fun createRoutine(
        folderId: Long?,
        name: String,
    ) {
        viewModelScope.launch { repository.createRoutine(folderId, name, System.currentTimeMillis()) }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch { repository.deleteRoutine(routineId) }
    }

    fun duplicateRoutine(routineId: Long) {
        viewModelScope.launch { repository.duplicateRoutine(routineId, System.currentTimeMillis()) }
    }

    fun startRoutine(routineId: Long) {
        viewModelScope.launch {
            val workoutId =
                repository.startWorkoutFromRoutine(
                    routineId,
                    System.currentTimeMillis(),
                    todayAsLocalDate(),
                )
            _uiState.update { it.copy(startedWorkoutId = workoutId) }
        }
    }

    fun consumeStartedWorkout() = _uiState.update { it.copy(startedWorkoutId = null) }

    fun openEditor(routineId: Long) {
        viewModelScope.launch {
            val detail = repository.getRoutineDetail(routineId)
            _uiState.update { it.copy(editorDetail = detail) }
        }
    }

    fun closeEditor() = _uiState.update { it.copy(editorDetail = null) }

    fun setExercisePickerOpen(open: Boolean) = _uiState.update { it.copy(isExercisePickerOpen = open) }

    fun addExerciseToEditor(exerciseId: Long) {
        val routineId = _uiState.value.editorDetail?.routine?.id ?: return
        viewModelScope.launch {
            repository.addExercise(routineId, exerciseId)
            _uiState.update { it.copy(isExercisePickerOpen = false) }
            openEditor(routineId)
        }
    }

    fun removeExerciseFromEditor(routineExerciseId: Long) {
        val routineId = _uiState.value.editorDetail?.routine?.id ?: return
        viewModelScope.launch {
            repository.removeExercise(routineExerciseId)
            openEditor(routineId)
        }
    }

    fun addSetToEditor(routineExerciseId: Long) {
        val routineId = _uiState.value.editorDetail?.routine?.id ?: return
        viewModelScope.launch {
            repository.addSet(routineExerciseId)
            openEditor(routineId)
        }
    }

    fun updateSetTargets(
        setId: Long,
        repsMin: Int?,
        repsMax: Int?,
        weightKg: Double?,
    ) {
        val routineId = _uiState.value.editorDetail?.routine?.id ?: return
        viewModelScope.launch {
            repository.updateSetTargets(setId, repsMin, repsMax, weightKg)
            openEditor(routineId)
        }
    }

    fun removeSetFromEditor(setId: Long) {
        val routineId = _uiState.value.editorDetail?.routine?.id ?: return
        viewModelScope.launch {
            repository.removeSet(setId)
            openEditor(routineId)
        }
    }

    fun saveWorkoutAsRoutine(
        workoutId: Long,
        folderId: Long?,
        name: String,
    ) {
        viewModelScope.launch { repository.saveWorkoutAsRoutine(workoutId, folderId, name, System.currentTimeMillis()) }
    }
}

private fun todayAsLocalDate(): Int = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
