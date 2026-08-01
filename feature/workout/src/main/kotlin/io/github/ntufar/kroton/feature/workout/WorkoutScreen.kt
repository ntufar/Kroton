package io.github.ntufar.kroton.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.domain.ActiveWorkoutExercise
import io.github.ntufar.kroton.domain.ActiveWorkoutSet
import io.github.ntufar.kroton.domain.ActiveWorkoutSnapshot
import io.github.ntufar.kroton.domain.WorkoutSummary
import io.github.ntufar.kroton.model.Exercise
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

@Composable
fun WorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        uiState.activeWorkout != null ->
            ActiveWorkoutContent(
                modifier = modifier,
                snapshot = uiState.activeWorkout!!,
                allExercises = uiState.allExercises,
                isExercisePickerOpen = uiState.isExercisePickerOpen,
                viewModel = viewModel,
            )
        else -> WorkoutHome(modifier = modifier, onStartEmptyWorkout = viewModel::startEmptyWorkout)
    }

    uiState.summary?.let { summary ->
        SummarySheet(summary = summary, onDismiss = viewModel::dismissSummary)
    }
}

@Composable
private fun WorkoutHome(
    modifier: Modifier = Modifier,
    onStartEmptyWorkout: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onStartEmptyWorkout) {
            Text("Start empty workout")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveWorkoutContent(
    modifier: Modifier = Modifier,
    snapshot: ActiveWorkoutSnapshot,
    allExercises: List<Exercise>,
    isExercisePickerOpen: Boolean,
    viewModel: WorkoutViewModel,
) {
    var elapsedSec by remember(snapshot.workout.id) { mutableStateOf(0) }
    LaunchedEffect(snapshot.workout.id) {
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - snapshot.workout.startedAt) / MILLIS_PER_SECOND).toInt()
            delay(TICK_MS)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ActiveWorkoutHeader(
            elapsedSec = elapsedSec,
            totalVolumeKg = snapshot.workout.totalVolumeKg,
            totalSets = snapshot.workout.totalSets,
            onFinish = { viewModel.finishWorkout(notes = null) },
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(snapshot.exercises, key = { it.workoutExercise.id }) { exercise ->
                ExerciseSection(exercise = exercise, viewModel = viewModel)
            }
            item {
                TextButton(onClick = { viewModel.setExercisePickerOpen(true) }, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add exercise")
                }
            }
        }
    }

    if (isExercisePickerOpen) {
        ExercisePickerSheet(
            exercises = allExercises,
            onPick = viewModel::addExercise,
            onDismiss = { viewModel.setExercisePickerOpen(false) },
        )
    }
}

@Composable
private fun ActiveWorkoutHeader(
    elapsedSec: Int,
    totalVolumeKg: Double,
    totalSets: Int,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(formatElapsed(elapsedSec), fontWeight = FontWeight.Bold)
            Text("${totalVolumeKg.toInt()} kg · $totalSets sets")
        }
        Button(onClick = onFinish) { Text("Finish") }
    }
}

@Composable
private fun ExerciseSection(
    exercise: ActiveWorkoutExercise,
    viewModel: WorkoutViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(exercise.exerciseName, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("#", modifier = Modifier.width(24.dp))
                Text("Previous", modifier = Modifier.weight(1f))
                Text("kg", modifier = Modifier.weight(1f))
                Text("Reps", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(72.dp))
            }
            exercise.sets.forEachIndexed { index, set ->
                SetRow(
                    index = index + 1,
                    set = set,
                    onToggleComplete = { weight, reps ->
                        viewModel.toggleSetCompletion(set.set.id, set.set.isCompleted, weight, reps)
                    },
                    onDelete = { viewModel.deleteSet(set.set.id) },
                )
            }
            TextButton(onClick = { viewModel.addSet(exercise.workoutExercise.id) }) {
                Text("Add set")
            }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: ActiveWorkoutSet,
    onToggleComplete: (Double?, Int?) -> Unit,
    onDelete: () -> Unit,
) {
    var weightText by remember(set.set.id) { mutableStateOf(set.set.weightKg?.let { formatWeight(it) } ?: "") }
    var repsText by remember(set.set.id) { mutableStateOf(set.set.reps?.toString() ?: "") }
    val previousWeightKg = set.previousWeightKg
    val previousReps = set.previousReps
    val previousLabel =
        if (previousWeightKg != null && previousReps != null) {
            "${formatWeight(previousWeightKg)}×$previousReps"
        } else {
            "—"
        }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(index.toString(), modifier = Modifier.width(24.dp))
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = {
                if (previousWeightKg != null) weightText = formatWeight(previousWeightKg)
                if (previousReps != null) repsText = previousReps.toString()
            },
        ) {
            Text(previousLabel)
        }
        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !set.set.isCompleted,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = { repsText = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !set.set.isCompleted,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (set.prRecordTypes.isNotEmpty()) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = "Personal record")
        }
        IconButton(onClick = { onToggleComplete(weightText.toDoubleOrNull(), repsText.toIntOrNull()) }) {
            Icon(Icons.Filled.Check, contentDescription = "Complete set")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete set")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
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
private fun SummarySheet(
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
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

private fun formatWeight(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun formatElapsed(totalSec: Int): String {
    val h = totalSec / SEC_PER_HOUR
    val m = (totalSec % SEC_PER_HOUR) / SEC_PER_MIN
    val s = totalSec % SEC_PER_MIN
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

private const val MILLIS_PER_SECOND = 1000L
private const val TICK_MS = 1000L
private const val SEC_PER_HOUR = 3600
private const val SEC_PER_MIN = 60
