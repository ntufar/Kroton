package io.github.ntufar.kroton.feature.stats

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.domain.ConsistencyStats
import io.github.ntufar.kroton.domain.StatsRangePreset
import io.github.ntufar.kroton.domain.WeeklyHardSets
import org.koin.androidx.compose.koinViewModel

// No muscle-map heatmap tile here (spec §5.7's sixth surface): blocked upstream on the §7.3/§11.5
// écorché art-sourcing decision (trace vs commission), not forgotten. `weeklyHardSets` already
// computes the muscle-credited data the heatmap would consume once the asset lands.
@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { RangeSelector(selected = uiState.range, onSelect = viewModel::setRange) }
        item { ConsistencySection(uiState.consistency) }
        item { VolumeSection(uiState = uiState, viewModel = viewModel) }
        item { WeeklyHardSetsSection(uiState.weeklyHardSets) }
        item { StrengthSection(uiState = uiState, viewModel = viewModel) }
        item { BodySection(uiState = uiState, viewModel = viewModel) }
    }
}

@Composable
private fun RangeSelector(
    selected: StatsRangePreset,
    onSelect: (StatsRangePreset) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf(
            StatsRangePreset.ONE_MONTH to "1M",
            StatsRangePreset.THREE_MONTHS to "3M",
            StatsRangePreset.SIX_MONTHS to "6M",
            StatsRangePreset.ONE_YEAR to "1Y",
            StatsRangePreset.ALL to "All",
        ).forEach { (preset, label) ->
            FilterChip(selected = selected == preset, onClick = { onSelect(preset) }, label = { Text(label) })
            Spacer(Modifier.height(0.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(
    title: String,
    onLongPressExportCsv: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth().let { base ->
                if (onLongPressExportCsv != null) {
                    base.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onLongPressExportCsv,
                    )
                } else {
                    base
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            content()
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ConsistencySection(consistency: ConsistencyStats?) {
    SectionCard(title = "Consistency") {
        if (consistency == null) return@SectionCard
        val avgDurationMin = consistency.averageDurationSec / SEC_PER_MIN
        Text("Streak: ${consistency.currentStreakDays} days · avg duration $avgDurationMin min")
        val weeks = consistency.workoutsPerIsoWeek.entries.sortedBy { it.key }
        BarChart(values = weeks.map { it.value.toDouble() })
    }
}

@Composable
private fun VolumeSection(
    uiState: StatsUiState,
    viewModel: StatsViewModel,
) {
    val context = LocalContext.current
    SectionCard(title = "Volume", onLongPressExportCsv = { shareCsv(context, viewModel.csvFor(ChartKind.VOLUME)) }) {
        val volume = uiState.volume ?: return@SectionCard
        LineChart(values = volume.totalVolumeByDay.map { it.value })
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            DonutChart(
                slices = volume.volumeShareByMuscle.entries.map { it.key.name to it.value },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WeeklyHardSetsSection(weeklyHardSets: WeeklyHardSets?) {
    SectionCard(title = "Weekly hard sets per muscle") {
        if (weeklyHardSets == null) return@SectionCard
        val totalsByWeek = weeklyHardSets.setsByIsoWeekAndMuscle.entries.sortedBy { it.key }
        BarChart(values = totalsByWeek.map { (_, muscles) -> muscles.values.sum() })
        Text("Reference: 10 sets/week maintenance, 20 sets/week upper productive range")
    }
}

@Composable
private fun StrengthSection(
    uiState: StatsUiState,
    viewModel: StatsViewModel,
) {
    SectionCard(title = "Strength") {
        Row(modifier = Modifier.fillMaxWidth()) {
            uiState.allExercises.take(EXERCISE_PICKER_LIMIT).forEach { exercise ->
                FilterChip(
                    selected = exercise.id in uiState.selectedExerciseIds,
                    onClick = { viewModel.toggleExercise(exercise.id) },
                    label = { Text(exercise.name) },
                )
            }
        }
        uiState.strengthSeries.forEach { series ->
            val values = series.estimated1RmByDay.map { it.value }
            if (values.isNotEmpty()) {
                Text(series.exerciseName)
                LineChart(values = normalise(values))
            }
        }
    }
}

@Composable
private fun BodySection(
    uiState: StatsUiState,
    viewModel: StatsViewModel,
) {
    val context = LocalContext.current
    SectionCard(title = "Body", onLongPressExportCsv = { shareCsv(context, viewModel.csvFor(ChartKind.BODY_WEIGHT)) }) {
        val body = uiState.body ?: return@SectionCard
        Text("Weight (raw + 7-day EMA)")
        LineChart(values = body.weightEmaByDay.map { it.value })
        if (body.bodyFatByDay.isNotEmpty()) {
            Text("Body fat %")
            LineChart(values = body.bodyFatByDay.map { it.value })
        }
        if (body.circumferencesByTypeKey.isNotEmpty()) {
            Text("Circumferences tracked: ${body.circumferencesByTypeKey.keys.joinToString(", ")}")
        }
    }
}

private fun normalise(values: List<Double>): List<Double> {
    val first = values.first().takeIf { it != 0.0 } ?: return values
    return values.map { it / first * NORMALISE_BASE }
}

private fun shareCsv(
    context: android.content.Context,
    csv: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_TEXT, csv)
        }
    context.startActivity(Intent.createChooser(intent, "Export chart data"))
}

private const val SEC_PER_MIN = 60
private const val EXERCISE_PICKER_LIMIT = 20
private const val NORMALISE_BASE = 100.0
