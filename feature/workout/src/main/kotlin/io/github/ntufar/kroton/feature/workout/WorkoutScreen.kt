package io.github.ntufar.kroton.feature.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.domain.RestTimerState
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private const val MILLIS_PER_SECOND = 1000L
private const val TICK_MS = 1000L
private const val REST_ADJUST_STEP_SEC = 15
private const val SUPERSET_ACCENT_ARGB = 0xFF14548C.toInt()

@Composable
fun WorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.keepScreenOn && uiState.activeWorkout != null) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    when {
        uiState.isLoading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        uiState.activeWorkout != null ->
            ActiveWorkoutContent(modifier = modifier, uiState = uiState, viewModel = viewModel)
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
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
) {
    val snapshot = uiState.activeWorkout!!
    var elapsedSec by remember(snapshot.workout.id) { mutableStateOf(0) }
    LaunchedEffect(snapshot.workout.id) {
        while (true) {
            elapsedSec = ((System.currentTimeMillis() - snapshot.workout.startedAt) / MILLIS_PER_SECOND).toInt()
            delay(TICK_MS)
        }
    }

    RequestNotificationPermissionEffect()
    val snackbarHostState = remember { SnackbarHostState() }
    UndoSnackbarEffect(uiState.lastDeletedSet, snackbarHostState, viewModel)

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ActiveWorkoutHeader(
                elapsedSec = elapsedSec,
                totalVolumeKg = snapshot.workout.totalVolumeKg,
                totalSets = snapshot.workout.totalSets,
                state =
                    HeaderState(uiState.selectionModeActive, uiState.selectedExerciseIds.size, uiState.keepScreenOn),
                actions =
                    HeaderActions(
                        onFinish = viewModel::finishWorkout,
                        onToggleSelectionMode = viewModel::toggleSelectionMode,
                        onGroupSelected = viewModel::groupSelectedAsSuperset,
                        onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
                    ),
            )
            uiState.restTimer?.let { timer ->
                RestTimerBar(
                    timer = timer,
                    onAdjust = { viewModel.restTimerAction(it) },
                    onSkip = { viewModel.restTimerAction(null) },
                )
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(snapshot.exercises, key = { it.workoutExercise.id }) { exercise ->
                    ExerciseSection(
                        exercise = exercise,
                        viewModel = viewModel,
                        selectionModeActive = uiState.selectionModeActive,
                        isSelected = exercise.workoutExercise.id in uiState.selectedExerciseIds,
                    )
                }
                item { AddExerciseButton(onClick = { viewModel.setExercisePickerOpen(true) }) }
            }
        }
    }

    ActiveWorkoutDialogs(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun ActiveWorkoutDialogs(
    uiState: WorkoutUiState,
    viewModel: WorkoutViewModel,
) {
    if (uiState.isExercisePickerOpen) {
        ExercisePickerSheet(
            exercises = uiState.allExercises,
            onPick = viewModel::addExercise,
            onDismiss = {
                viewModel.setExercisePickerOpen(false)
                if (uiState.overflow?.isReplacing == true) viewModel.closeOverflow()
            },
        )
    }

    uiState.overflow?.let { overflow ->
        if (!overflow.isReplacing) ExerciseOverflowSheet(overflow = overflow, viewModel = viewModel)
    }
}

@Composable
private fun AddExerciseButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(16.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text("Add exercise")
    }
}

@Composable
private fun RequestNotificationPermissionEffect() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun UndoSnackbarEffect(
    lastDeletedSet: DeletedSetSnapshot?,
    snackbarHostState: SnackbarHostState,
    viewModel: WorkoutViewModel,
) {
    LaunchedEffect(lastDeletedSet) {
        if (lastDeletedSet != null) {
            val result = snackbarHostState.showSnackbar("Set deleted", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteSet() else viewModel.dismissUndo()
        }
    }
}

private data class HeaderState(
    val selectionModeActive: Boolean,
    val selectedCount: Int,
    val keepScreenOn: Boolean,
)

private data class HeaderActions(
    val onFinish: () -> Unit,
    val onToggleSelectionMode: () -> Unit,
    val onGroupSelected: () -> Unit,
    val onToggleKeepScreenOn: () -> Unit,
)

@Composable
private fun ActiveWorkoutHeader(
    elapsedSec: Int,
    totalVolumeKg: Double,
    totalSets: Int,
    state: HeaderState,
    actions: HeaderActions,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(formatElapsed(elapsedSec), fontWeight = FontWeight.Bold)
                Text("${totalVolumeKg.toInt()} kg · $totalSets sets")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onToggleSelectionMode) {
                    Icon(
                        Icons.Filled.Groups,
                        contentDescription = "Group as superset",
                        tint = if (state.selectionModeActive) Color(SUPERSET_ACCENT_ARGB) else Color.Unspecified,
                    )
                }
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (state.keepScreenOn) "Keep screen on ✓" else "Keep screen on") },
                        onClick = {
                            actions.onToggleKeepScreenOn()
                            menuOpen = false
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = actions.onFinish) { Text("Finish") }
            }
        }
        if (state.selectionModeActive) {
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.selectedCount} selected")
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = actions.onGroupSelected, enabled = state.selectedCount >= MIN_SUPERSET_SIZE) {
                    Text("Group as superset")
                }
            }
        }
    }
}

@Composable
private fun RestTimerBar(
    timer: RestTimerState,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Rest: ${formatElapsed(timer.remainingSec)}", fontWeight = FontWeight.Bold)
        Row {
            TextButton(onClick = { onAdjust(-REST_ADJUST_STEP_SEC) }) { Text("−15s") }
            TextButton(onClick = { onAdjust(REST_ADJUST_STEP_SEC) }) { Text("+15s") }
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    }
}
