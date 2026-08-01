package io.github.ntufar.kroton.feature.routines

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.model.Routine
import io.github.ntufar.kroton.model.RoutineFolder
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.TimeUnit

@Composable
fun RoutinesScreen(
    modifier: Modifier = Modifier,
    onWorkoutStarted: (Long) -> Unit = {},
    viewModel: RoutinesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.startedWorkoutId) {
        uiState.startedWorkoutId?.let {
            onWorkoutStarted(it)
            viewModel.consumeStartedWorkout()
        }
    }

    var showNewRoutineDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewRoutineDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New routine")
            }
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            RoutineList(modifier = Modifier.padding(padding), uiState = uiState, viewModel = viewModel)
        }
    }

    if (showNewRoutineDialog) {
        NewRoutineDialog(
            folders = uiState.folders,
            onCreate = { folderId, name ->
                viewModel.createRoutine(folderId, name)
                showNewRoutineDialog = false
            },
            onDismiss = { showNewRoutineDialog = false },
        )
    }

    uiState.editorDetail?.let { detail ->
        RoutineEditorSheet(detail = detail, uiState = uiState, viewModel = viewModel)
    }
}

@Composable
private fun RoutineList(
    modifier: Modifier = Modifier,
    uiState: RoutineListUiState,
    viewModel: RoutinesViewModel,
) {
    val ungrouped = uiState.routines.filter { it.folderId == null }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        items(uiState.folders, key = { "folder-${it.id}" }) { folder ->
            FolderSection(
                folder = folder,
                routines = uiState.routines.filter { it.folderId == folder.id },
                exerciseSummaries = uiState.exerciseSummaries,
                viewModel = viewModel,
            )
        }
        items(ungrouped, key = { "routine-${it.id}" }) { routine ->
            RoutineCard(
                routine = routine,
                exerciseSummary = uiState.exerciseSummaries[routine.id].orEmpty(),
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun FolderSection(
    folder: RoutineFolder,
    routines: List<Routine>,
    exerciseSummaries: Map<Long, String>,
    viewModel: RoutinesViewModel,
) {
    var expanded by remember(folder.id) { mutableStateOf(true) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(folder.name, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { viewModel.deleteFolder(folder.id) }) { Text("Delete folder") }
        }
        if (expanded) {
            routines.forEach { routine ->
                RoutineCard(
                    routine = routine,
                    exerciseSummary = exerciseSummaries[routine.id].orEmpty(),
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    exerciseSummary: String,
    viewModel: RoutinesViewModel,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(routine.name, fontWeight = FontWeight.Bold)
                if (exerciseSummary.isNotBlank()) Text(exerciseSummary)
                Text(lastPerformedLabel(routine.lastPerformedAt))
            }
            RoutineCardOverflow(
                routine = routine,
                exerciseSummary = exerciseSummary,
                viewModel = viewModel,
                context = context,
            )
        }
    }
}

@Composable
private fun RoutineCardOverflow(
    routine: Routine,
    exerciseSummary: String,
    viewModel: RoutinesViewModel,
    context: android.content.Context,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.startRoutine(routine.id) }) { Text("Start") }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Routine options")
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = {
                menuOpen = false
                viewModel.openEditor(routine.id)
            })
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = {
                    menuOpen = false
                    viewModel.duplicateRoutine(routine.id)
                },
            )
            DropdownMenuItem(
                text = { Text("Share as text") },
                onClick = {
                    menuOpen = false
                    shareRoutineAsText(context, routine.name, exerciseSummary)
                },
            )
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                menuOpen = false
                viewModel.deleteRoutine(routine.id)
            })
        }
    }
}

private fun shareRoutineAsText(
    context: android.content.Context,
    name: String,
    exerciseSummary: String,
) {
    val text = "$name\n$exerciseSummary"
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(intent, "Share routine"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewRoutineDialog(
    folders: List<RoutineFolder>,
    onCreate: (Long?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf<Long?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("New routine", fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = {
                name = it
            }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            if (folders.isNotEmpty()) {
                Text("Folder", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                folders.forEach { folder ->
                    TextButton(onClick = { folderId = folder.id }) {
                        Text(if (folderId == folder.id) "${folder.name} ✓" else folder.name)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { if (name.isNotBlank()) onCreate(folderId, name) }) { Text("Create") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineEditorSheet(
    detail: io.github.ntufar.kroton.domain.RoutineDetail,
    uiState: RoutineListUiState,
    viewModel: RoutinesViewModel,
) {
    ModalBottomSheet(onDismissRequest = viewModel::closeEditor, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(detail.routine.name, fontWeight = FontWeight.Bold)
            detail.exercises.forEach { exerciseDetail ->
                RoutineExerciseEditorRow(exerciseDetail = exerciseDetail, viewModel = viewModel)
            }
            TextButton(onClick = { viewModel.setExercisePickerOpen(true) }) { Text("Add exercise") }
        }
    }

    if (uiState.isExercisePickerOpen) {
        RoutineExercisePickerSheet(
            exercises = uiState.allExercises,
            onPick = viewModel::addExerciseToEditor,
            onDismiss = { viewModel.setExercisePickerOpen(false) },
        )
    }
}

@Composable
private fun RoutineExerciseEditorRow(
    exerciseDetail: io.github.ntufar.kroton.domain.RoutineExerciseDetail,
    viewModel: RoutinesViewModel,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(exerciseDetail.exerciseName, fontWeight = FontWeight.Bold)
                TextButton(onClick = { viewModel.removeExerciseFromEditor(exerciseDetail.routineExercise.id) }) {
                    Text("Remove")
                }
            }
            exerciseDetail.sets.forEach { set ->
                RoutineSetEditorRow(set = set, viewModel = viewModel)
            }
            TextButton(onClick = { viewModel.addSetToEditor(exerciseDetail.routineExercise.id) }) { Text("Add set") }
        }
    }
}

@Composable
private fun RoutineSetEditorRow(
    set: io.github.ntufar.kroton.model.RoutineSet,
    viewModel: RoutinesViewModel,
) {
    var repsText by remember(set.id) { mutableStateOf(set.targetRepsMax?.toString() ?: "") }
    var weightText by remember(set.id) { mutableStateOf(set.targetWeightKg?.toString() ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = weightText,
            onValueChange = {
                weightText = it
                viewModel.updateSetTargets(set.id, repsText.toIntOrNull(), repsText.toIntOrNull(), it.toDoubleOrNull())
            },
            label = { Text("kg") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = repsText,
            onValueChange = {
                repsText = it
                viewModel.updateSetTargets(set.id, it.toIntOrNull(), it.toIntOrNull(), weightText.toDoubleOrNull())
            },
            label = { Text("Reps") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        IconButton(onClick = { viewModel.removeSetFromEditor(set.id) }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Remove set")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineExercisePickerSheet(
    exercises: List<io.github.ntufar.kroton.model.Exercise>,
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
                exercises.filter {
                    it.nameNormalised.contains(
                        normalisedQuery,
                    )
                }
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
            LazyColumn(modifier = Modifier.height(EXERCISE_LIST_HEIGHT_DP.dp)) {
                items(filtered, key = { it.id }) { exercise ->
                    TextButton(onClick = { onPick(exercise.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(exercise.name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun lastPerformedLabel(lastPerformedAt: Long?): String {
    if (lastPerformedAt == null) return "Never performed"
    val daysAgo = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastPerformedAt)
    return when {
        daysAgo <= 0 -> "Today"
        daysAgo == 1L -> "Yesterday"
        else -> "$daysAgo days ago"
    }
}

private const val EXERCISE_LIST_HEIGHT_DP = 400
