package io.github.ntufar.kroton.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.domain.WorkoutSummary
import io.github.ntufar.kroton.model.Exercise
import io.github.ntufar.kroton.model.WorkoutSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseOverflowSheet(
    overflow: ExerciseOverflowState,
    viewModel: WorkoutViewModel,
) {
    ModalBottomSheet(onDismissRequest = viewModel::closeOverflow, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Exercise options", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::startReplacingExercise, modifier = Modifier.fillMaxWidth()) {
                Text("Replace exercise")
            }
            OutlinedTextField(
                value = overflow.notesDraft,
                onValueChange = viewModel::updateOverflowNotesDraft,
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = viewModel::saveOverflowNotes) { Text("Save note") }
            OutlinedTextField(
                value = overflow.restSecDraft,
                onValueChange = viewModel::updateOverflowRestSecDraft,
                label = { Text("Rest seconds") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = viewModel::saveOverflowRestSec) { Text("Save rest time") }
            Spacer(Modifier.height(8.dp))
            Text("Plate calculator", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = overflow.plateTargetKg,
                onValueChange = viewModel::updateOverflowPlateTarget,
                label = { Text("Target weight (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            overflow.plateResult?.let { result ->
                Text("Per side: ${result.perSideKg.joinToString(", ") { formatWeight(it) }} kg")
                Text("Achieved: ${formatWeight(result.achievedKg)} kg (Δ ${formatWeight(result.deltaKg)})")
            }
            Spacer(Modifier.height(8.dp))
            if (overflow.inlineHistory.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Last session", fontWeight = FontWeight.Bold)
                }
                overflow.inlineHistory.forEach { set: WorkoutSet ->
                    val weight = set.weightKg
                    val reps = set.reps
                    if (weight != null && reps != null) {
                        Text("${formatWeight(weight)} kg × $reps")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePickerSheet(
    exercises: List<Exercise>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered =
        remember(exercises, query) {
            val normalisedQuery = query.trim().lowercase()
            if (normalisedQuery.isBlank()) {
                exercises
            } else {
                exercises.filter { it.nameNormalised.contains(normalisedQuery) }
            }
        }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search exercises") },
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(filtered, key = { it.id }) { exercise ->
                    TextButton(onClick = { onPick(exercise.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(exercise.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummarySheet(
    summary: WorkoutSummary,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Workout complete", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Duration: ${formatElapsed(summary.durationSec)}")
            Text("Volume: ${summary.totalVolumeKg.toInt()} kg")
            Text("Sets: ${summary.totalSets}")
            Text("PRs: ${summary.prCount}")
            if (summary.muscleBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Muscles worked", fontWeight = FontWeight.Bold)
                summary.muscleBreakdown.entries.sortedByDescending { it.value }.forEach { (muscle, volume) ->
                    Text("${muscle.name.lowercase().replace('_', ' ')}: ${volume.toInt()} kg")
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
