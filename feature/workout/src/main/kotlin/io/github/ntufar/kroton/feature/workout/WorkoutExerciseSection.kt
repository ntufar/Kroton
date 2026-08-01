package io.github.ntufar.kroton.feature.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.domain.ActiveWorkoutExercise
import io.github.ntufar.kroton.domain.ActiveWorkoutSet
import io.github.ntufar.kroton.model.SetType

private const val SUPERSET_BORDER_WIDTH_DP = 3
private const val STEPPER_BUTTON_HEIGHT_DP = 20

@Composable
internal fun ExerciseSection(
    exercise: ActiveWorkoutExercise,
    viewModel: WorkoutViewModel,
    selectionModeActive: Boolean,
    isSelected: Boolean,
) {
    val supersetColor = exercise.workoutExercise.supersetGroupId?.let { supersetColorFor(it) }
    val cardModifier =
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).let { base ->
            if (supersetColor != null) base.border(SUPERSET_BORDER_WIDTH_DP.dp, supersetColor) else base
        }
    Card(modifier = cardModifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            ExerciseSectionHeader(
                exercise = exercise,
                viewModel = viewModel,
                selectionModeActive = selectionModeActive,
                isSelected = isSelected,
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("#", modifier = Modifier.width(24.dp))
                Text("Previous", modifier = Modifier.weight(1f))
                Text("kg", modifier = Modifier.weight(1.4f))
                Text("Reps", modifier = Modifier.weight(1.4f))
                Spacer(Modifier.width(72.dp))
            }
            exercise.sets.forEachIndexed { index, set ->
                SetRow(
                    index = index + 1,
                    set = set,
                    onToggleComplete = { weight, reps ->
                        viewModel.toggleSetCompletion(set.set.id, set.set.isCompleted, weight, reps)
                    },
                    onDelete = { viewModel.deleteSet(set.set) },
                    onChangeType = { type -> viewModel.changeSetType(set.set.id, type) },
                )
            }
            TextButton(onClick = { viewModel.addSet(exercise.workoutExercise.id) }) {
                Text("Add set")
            }
        }
    }
}

@Composable
private fun ExerciseSectionHeader(
    exercise: ActiveWorkoutExercise,
    viewModel: WorkoutViewModel,
    selectionModeActive: Boolean,
    isSelected: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionModeActive) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { viewModel.toggleExerciseSelected(exercise.workoutExercise.id) },
                )
            }
            Text(exercise.exerciseName, fontWeight = FontWeight.Bold)
        }
        if (!selectionModeActive) {
            Row {
                if (exercise.workoutExercise.supersetGroupId != null) {
                    TextButton(onClick = { viewModel.ungroupSuperset(exercise.workoutExercise.id) }) {
                        Text("Ungroup")
                    }
                }
                IconButton(onClick = { viewModel.openOverflow(exercise.workoutExercise.id) }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetRow(
    index: Int,
    set: ActiveWorkoutSet,
    onToggleComplete: (Double?, Int?) -> Unit,
    onDelete: () -> Unit,
    onChangeType: (SetType) -> Unit,
) {
    var weightText by remember(set.set.id) { mutableStateOf(set.set.weightKg?.let { formatWeight(it) } ?: "") }
    var repsText by remember(set.set.id) { mutableStateOf(set.set.reps?.toString() ?: "") }
    var typeMenuOpen by remember(set.set.id) { mutableStateOf(false) }
    val previousWeightKg = set.previousWeightKg
    val previousReps = set.previousReps
    val previousLabel = previousSetLabel(previousWeightKg, previousReps)

    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onDelete()
    }

    SwipeToDismissBox(state = dismissState, backgroundContent = { SetRowDeleteBackground() }) {
        Box {
            SetRowFields(
                index = index,
                set = set,
                weightText = weightText,
                onWeightChange = { weightText = it },
                repsText = repsText,
                onRepsChange = { repsText = it },
                previousLabel = previousLabel,
                onFillPrevious = {
                    if (previousWeightKg != null) weightText = formatWeight(previousWeightKg)
                    if (previousReps != null) repsText = previousReps.toString()
                },
                onLongPress = { typeMenuOpen = true },
                onToggleComplete = { onToggleComplete(weightText.toDoubleOrNull(), repsText.toIntOrNull()) },
                onDelete = onDelete,
            )
            SetTypeMenu(
                expanded = typeMenuOpen,
                onDismiss = { typeMenuOpen = false },
                onChangeType = onChangeType,
                onDelete = onDelete,
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SetRowFields(
    index: Int,
    set: ActiveWorkoutSet,
    weightText: String,
    onWeightChange: (String) -> Unit,
    repsText: String,
    onRepsChange: (String) -> Unit,
    previousLabel: String,
    onFillPrevious: () -> Unit,
    onLongPress: () -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth().pointerInput(set.set.id) {
                detectTapGestures(onLongPress = { onLongPress() })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (set.set.setType == SetType.NORMAL) index.toString() else set.set.setType.name.take(1),
            modifier = Modifier.width(24.dp),
        )
        TextButton(modifier = Modifier.weight(1f), onClick = onFillPrevious) {
            Text(previousLabel)
        }
        NumericStepperField(
            value = weightText,
            onValueChange = onWeightChange,
            enabled = !set.set.isCompleted,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1.4f),
        )
        NumericStepperField(
            value = repsText,
            onValueChange = onRepsChange,
            enabled = !set.set.isCompleted,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1.4f),
        )
        SetRowActions(hasPr = set.prRecordTypes.isNotEmpty(), onComplete = onToggleComplete, onDelete = onDelete)
    }
}

@Composable
private fun SetRowDeleteBackground() {
    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer)) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Delete set",
            modifier = Modifier.padding(horizontal = 16.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun previousSetLabel(
    previousWeightKg: Double?,
    previousReps: Int?,
): String =
    if (previousWeightKg != null && previousReps != null) {
        "${formatWeight(previousWeightKg)}×$previousReps"
    } else {
        "—"
    }

@Composable
private fun SetRowActions(
    hasPr: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    if (hasPr) {
        Icon(Icons.Filled.EmojiEvents, contentDescription = "Personal record")
    }
    IconButton(onClick = onComplete) {
        Icon(Icons.Filled.Check, contentDescription = "Complete set")
    }
    IconButton(onClick = onDelete) {
        Icon(Icons.Filled.Delete, contentDescription = "Delete set")
    }
}

@Composable
private fun SetTypeMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onChangeType: (SetType) -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SetType.entries.forEach { type ->
            DropdownMenuItem(
                text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                onClick = {
                    onChangeType(type)
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                onDelete()
                onDismiss()
            },
        )
    }
}

@Composable
private fun NumericStepperField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    val step = if (keyboardType == KeyboardType.Number) 1.0 else WEIGHT_STEP_KG
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        )
        if (enabled) {
            Column {
                IconButton(
                    onClick = { onValueChange(formatWeight((value.toDoubleOrNull() ?: 0.0) + step)) },
                    modifier = Modifier.height(STEPPER_BUTTON_HEIGHT_DP.dp),
                ) { Text("+") }
                IconButton(
                    onClick = {
                        onValueChange(formatWeight(((value.toDoubleOrNull() ?: 0.0) - step).coerceAtLeast(0.0)))
                    },
                    modifier = Modifier.height(STEPPER_BUTTON_HEIGHT_DP.dp),
                ) { Text("−") }
            }
        }
    }
}
