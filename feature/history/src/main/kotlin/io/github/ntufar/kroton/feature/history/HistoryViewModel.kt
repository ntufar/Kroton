package io.github.ntufar.kroton.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ntufar.kroton.domain.ActiveWorkoutSnapshot
import io.github.ntufar.kroton.domain.HistoryHeaderStats
import io.github.ntufar.kroton.domain.HistoryRepository
import io.github.ntufar.kroton.domain.RoutineRepository
import io.github.ntufar.kroton.domain.WorkoutRepository
import io.github.ntufar.kroton.model.Workout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class HistoryUiState(
    val isLoading: Boolean = true,
    val workouts: List<Workout> = emptyList(),
    val headerStats: HistoryHeaderStats? = null,
    val calendarViewActive: Boolean = false,
    val trainedDates: Set<Int> = emptySet(),
    val detail: ActiveWorkoutSnapshot? = null,
    val isEditingDetail: Boolean = false,
    val duplicatedWorkoutId: Long? = null,
)

@Suppress("TooManyFunctions")
class HistoryViewModel(
    private val historyRepository: HistoryRepository,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.observeFinishedWorkouts().collect { workouts ->
                val today = LocalDate.now()
                val stats =
                    historyRepository.headerStats(
                        finishedWorkouts = workouts,
                        weekStartLocalDate = asLocalDate(today.minusDays(today.dayOfWeek.value - 1L)),
                        monthStartLocalDate = asLocalDate(today.withDayOfMonth(1)),
                    )
                _uiState.update { it.copy(isLoading = false, workouts = workouts, headerStats = stats) }
            }
        }
        loadTrainedDatesForCurrentMonth()
    }

    private fun loadTrainedDatesForCurrentMonth() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val start = asLocalDate(today.withDayOfMonth(1))
            val end = asLocalDate(today.withDayOfMonth(today.lengthOfMonth()))
            val dates = historyRepository.getTrainedLocalDates(start, end)
            _uiState.update { it.copy(trainedDates = dates) }
        }
    }

    fun toggleCalendarView() = _uiState.update { it.copy(calendarViewActive = !it.calendarViewActive) }

    fun openDetail(workoutId: Long) {
        viewModelScope.launch {
            val snapshot = workoutRepository.getSnapshot(workoutId)
            _uiState.update { it.copy(detail = snapshot, isEditingDetail = false) }
        }
    }

    fun closeDetail() = _uiState.update { it.copy(detail = null, isEditingDetail = false) }

    fun toggleEditMode() = _uiState.update { it.copy(isEditingDetail = !it.isEditingDetail) }

    fun editSetValues(
        setId: Long,
        weightKg: Double?,
        reps: Int?,
    ) {
        val workoutId = _uiState.value.detail?.workout?.id ?: return
        viewModelScope.launch {
            workoutRepository.editCompletedSet(setId, weightKg, reps, System.currentTimeMillis())
            openDetail(workoutId)
        }
    }

    fun deleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            workoutRepository.deleteWorkout(workoutId)
            closeDetail()
        }
    }

    fun duplicateAsNewWorkout(workoutId: Long) {
        viewModelScope.launch {
            val newId =
                workoutRepository.duplicateAsNewWorkout(
                    workoutId,
                    System.currentTimeMillis(),
                    todayAsLocalDate(),
                )
            _uiState.update { it.copy(duplicatedWorkoutId = newId) }
        }
    }

    fun consumeDuplicatedWorkout() = _uiState.update { it.copy(duplicatedWorkoutId = null) }

    fun saveAsRoutine(
        workoutId: Long,
        name: String,
    ) {
        viewModelScope.launch {
            routineRepository.saveWorkoutAsRoutine(
                workoutId,
                null,
                name,
                System.currentTimeMillis(),
            )
        }
    }
}

private fun asLocalDate(date: LocalDate): Int = date.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()

private fun todayAsLocalDate(): Int = asLocalDate(LocalDate.now())
