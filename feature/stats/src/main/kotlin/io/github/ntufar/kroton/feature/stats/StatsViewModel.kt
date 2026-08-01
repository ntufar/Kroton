package io.github.ntufar.kroton.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ntufar.kroton.domain.BodyStats
import io.github.ntufar.kroton.domain.ConsistencyStats
import io.github.ntufar.kroton.domain.StatsRangePreset
import io.github.ntufar.kroton.domain.StatsRepository
import io.github.ntufar.kroton.domain.StrengthSeries
import io.github.ntufar.kroton.domain.VolumeStats
import io.github.ntufar.kroton.domain.WeeklyHardSets
import io.github.ntufar.kroton.domain.WorkoutRepository
import io.github.ntufar.kroton.model.Exercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_STRENGTH_EXERCISES = 5

data class StatsUiState(
    val isLoading: Boolean = true,
    val range: StatsRangePreset = StatsRangePreset.THREE_MONTHS,
    val consistency: ConsistencyStats? = null,
    val volume: VolumeStats? = null,
    val weeklyHardSets: WeeklyHardSets? = null,
    val body: BodyStats? = null,
    val allExercises: List<Exercise> = emptyList(),
    val selectedExerciseIds: List<Long> = emptyList(),
    val strengthSeries: List<StrengthSeries> = emptyList(),
)

class StatsViewModel(
    private val statsRepository: StatsRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            workoutRepository.observeExercises().collect { list -> _uiState.update { it.copy(allExercises = list) } }
        }
        refresh()
    }

    fun setRange(preset: StatsRangePreset) {
        _uiState.update { it.copy(range = preset) }
        refresh()
    }

    fun toggleExercise(exerciseId: Long) {
        _uiState.update {
            val current = it.selectedExerciseIds
            val next =
                when {
                    exerciseId in current -> current - exerciseId
                    current.size < MAX_STRENGTH_EXERCISES -> current + exerciseId
                    else -> current
                }
            it.copy(selectedExerciseIds = next)
        }
        refreshStrength()
    }

    private fun refresh() {
        viewModelScope.launch {
            val range = statsRepository.resolveRange(_uiState.value.range, System.currentTimeMillis())
            val consistency = statsRepository.consistencyStats(range)
            val volume = statsRepository.volumeStats(range)
            val weeklyHardSets = statsRepository.weeklyHardSets(range)
            val body = statsRepository.bodyStats(range)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    consistency = consistency,
                    volume = volume,
                    weeklyHardSets = weeklyHardSets,
                    body = body,
                )
            }
        }
        refreshStrength()
    }

    private fun refreshStrength() {
        viewModelScope.launch {
            val range = statsRepository.resolveRange(_uiState.value.range, System.currentTimeMillis())
            val series = statsRepository.strengthSeries(_uiState.value.selectedExerciseIds, range)
            _uiState.update { it.copy(strengthSeries = series) }
        }
    }

    fun csvFor(chart: ChartKind): String {
        val state = _uiState.value
        return when (chart) {
            ChartKind.VOLUME -> statsRepository.csvForDayValues(state.volume?.totalVolumeByDay.orEmpty(), "volume_kg")
            ChartKind.BODY_WEIGHT -> statsRepository.csvForDayValues(state.body?.weightByDay.orEmpty(), "weight_kg")
            ChartKind.BODY_FAT -> statsRepository.csvForDayValues(state.body?.bodyFatByDay.orEmpty(), "body_fat_pct")
        }
    }
}

enum class ChartKind { VOLUME, BODY_WEIGHT, BODY_FAT }
