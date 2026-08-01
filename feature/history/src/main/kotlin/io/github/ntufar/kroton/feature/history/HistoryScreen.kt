package io.github.ntufar.kroton.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.ntufar.kroton.domain.HistoryHeaderStats
import io.github.ntufar.kroton.model.Workout
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onWorkoutDuplicated: (Long) -> Unit = {},
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.duplicatedWorkoutId) {
        uiState.duplicatedWorkoutId?.let {
            onWorkoutDuplicated(it)
            viewModel.consumeDuplicatedWorkout()
        }
    }

    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            HistoryHeader(stats = uiState.headerStats, onToggleCalendar = viewModel::toggleCalendarView)
            if (uiState.calendarViewActive) {
                CalendarView(trainedDates = uiState.trainedDates)
            } else {
                WorkoutList(workouts = uiState.workouts, onOpen = viewModel::openDetail)
            }
        }
    }

    uiState.detail?.let { detail ->
        WorkoutDetailSheet(detail = detail, isEditing = uiState.isEditingDetail, viewModel = viewModel)
    }
}

@Composable
private fun HistoryHeader(
    stats: HistoryHeaderStats?,
    onToggleCalendar: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("${stats?.workoutsThisWeek ?: 0} this week · ${stats?.workoutsThisMonth ?: 0} this month")
            Text("Streak: ${stats?.currentStreakDays ?: 0} days · ${(stats?.totalVolumeKg ?: 0.0).toInt()} kg total")
        }
        IconButton(onClick = onToggleCalendar) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = "Toggle calendar view")
        }
    }
}

@Composable
private fun WorkoutList(
    workouts: List<Workout>,
    onOpen: (Long) -> Unit,
) {
    val grouped = workouts.groupBy { it.localDate / MONTH_GROUP_DIVISOR }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        grouped.forEach { (monthKey, monthWorkouts) ->
            item(key = "month-$monthKey") { Text(monthLabel(monthKey), fontWeight = FontWeight.Bold) }
            items(monthWorkouts, key = { it.id }) { workout -> WorkoutRow(workout = workout, onOpen = onOpen) }
        }
    }
}

@Composable
private fun WorkoutRow(
    workout: Workout,
    onOpen: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        TextButton(onClick = { onOpen(workout.id) }, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Text(workout.name, fontWeight = FontWeight.Bold)
                Text(dateLabel(workout.startedAt))
                Text("${workout.totalVolumeKg.toInt()} kg · ${workout.totalSets} sets · ${workout.prCount} PRs")
            }
        }
    }
}

@Composable
private fun CalendarView(trainedDates: Set<Int>) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Trained days this month", fontWeight = FontWeight.Bold)
        Text(trainedDates.sorted().joinToString(", ") { it.toString().takeLast(2) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutDetailSheet(
    detail: ActiveWorkoutSnapshot,
    isEditing: Boolean,
    viewModel: HistoryViewModel,
) {
    var showSaveAsRoutineDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = viewModel::closeDetail, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            WorkoutDetailHeader(
                detail = detail,
                isEditing = isEditing,
                viewModel = viewModel,
                onSaveAsRoutine = { showSaveAsRoutineDialog = true },
            )
            detail.exercises.forEach { exercise ->
                DetailExerciseSection(exercise = exercise, isEditing = isEditing, viewModel = viewModel)
            }
        }
    }

    if (showSaveAsRoutineDialog) {
        SaveAsRoutineDialog(
            onSave = { name ->
                viewModel.saveAsRoutine(detail.workout.id, name)
                showSaveAsRoutineDialog = false
            },
            onDismiss = { showSaveAsRoutineDialog = false },
        )
    }
}

@Composable
private fun WorkoutDetailHeader(
    detail: ActiveWorkoutSnapshot,
    isEditing: Boolean,
    viewModel: HistoryViewModel,
    onSaveAsRoutine: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(detail.workout.name, fontWeight = FontWeight.Bold)
        Row {
            TextButton(onClick = viewModel::toggleEditMode) { Text(if (isEditing) "Done" else "Edit") }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                ) { Icon(Icons.Filled.MoreVert, contentDescription = "Options") }
                WorkoutDetailMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    onDuplicate = { viewModel.duplicateAsNewWorkout(detail.workout.id) },
                    onSaveAsRoutine = onSaveAsRoutine,
                    onDelete = { viewModel.deleteWorkout(detail.workout.id) },
                )
            }
        }
    }
}

@Composable
private fun WorkoutDetailMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDuplicate: () -> Unit,
    onSaveAsRoutine: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Duplicate as new workout") }, onClick = {
            onDismiss()
            onDuplicate()
        })
        DropdownMenuItem(text = { Text("Save as routine") }, onClick = {
            onDismiss()
            onSaveAsRoutine()
        })
        DropdownMenuItem(text = { Text("Delete") }, onClick = {
            onDismiss()
            onDelete()
        })
    }
}

@Composable
private fun DetailExerciseSection(
    exercise: ActiveWorkoutExercise,
    isEditing: Boolean,
    viewModel: HistoryViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(exercise.exerciseName, fontWeight = FontWeight.Bold)
            exercise.sets.forEach { set -> DetailSetRow(set = set, isEditing = isEditing, viewModel = viewModel) }
        }
    }
}

@Composable
private fun DetailSetRow(
    set: ActiveWorkoutSet,
    isEditing: Boolean,
    viewModel: HistoryViewModel,
) {
    var weightText by remember(set.set.id) { mutableStateOf(set.set.weightKg?.toString() ?: "") }
    var repsText by remember(set.set.id) { mutableStateOf(set.set.reps?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (isEditing) {
            DetailSetEditFields(
                weightText = weightText,
                onWeightChange = {
                    weightText = it
                    viewModel.editSetValues(set.set.id, it.toDoubleOrNull(), repsText.toIntOrNull())
                },
                repsText = repsText,
                onRepsChange = {
                    repsText = it
                    viewModel.editSetValues(set.set.id, weightText.toDoubleOrNull(), it.toIntOrNull())
                },
            )
        } else {
            Text("${weightText.ifBlank { "—" }} kg × ${repsText.ifBlank { "—" }}", modifier = Modifier.weight(1f))
        }
        if (set.prRecordTypes.isNotEmpty()) Text("PR")
    }
}

@Composable
private fun RowScope.DetailSetEditFields(
    weightText: String,
    onWeightChange: (String) -> Unit,
    repsText: String,
    onRepsChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = weightText,
        onValueChange = onWeightChange,
        label = { Text("kg") },
        modifier = Modifier.weight(1f),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    OutlinedTextField(
        value = repsText,
        onValueChange = onRepsChange,
        label = { Text("Reps") },
        modifier = Modifier.weight(1f),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveAsRoutineDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Save as routine", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Routine name") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { if (name.isNotBlank()) onSave(name) }) { Text("Save") }
        }
    }
}

private fun dateLabel(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

private fun monthLabel(monthKey: Int): String {
    val year = monthKey / MONTH_KEY_YEAR_DIVISOR
    val month = monthKey % MONTH_KEY_YEAR_DIVISOR
    return YearMonth.of(year, month).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}

private const val MONTH_GROUP_DIVISOR = 100
private const val MONTH_KEY_YEAR_DIVISOR = 100
