package io.github.ntufar.kroton.feature.measure

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.domain.DerivedMetrics
import io.github.ntufar.kroton.domain.MeasurementSummary
import io.github.ntufar.kroton.model.MeasurementType
import io.github.ntufar.kroton.model.Sex
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MeasureScreen(
    modifier: Modifier = Modifier,
    viewModel: MeasureViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item { TodayCard(types = uiState.enabledTypes, onQuickAdd = viewModel::quickAdd) }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Measurements", fontWeight = FontWeight.Bold)
                    TextButton(onClick = { viewModel.setManageTypesOpen(true) }) { Text("Manage") }
                }
            }
            items(uiState.enabledTypes, key = { it.id }) { type ->
                MeasurementTypeRow(
                    type = type,
                    summary = uiState.summaries[type.id],
                    onClick = { viewModel.openTypeHistory(type.id) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                DerivedMetricsCard(
                    derived = uiState.derived,
                    needsProfileSetup = uiState.needsProfileSetup,
                    onSetProfile = viewModel::setProfileHeightAndSex,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
            item { PhotosSection(uiState = uiState, viewModel = viewModel) }
        }
    }

    uiState.selectedTypeId?.let { typeId ->
        val type = uiState.allTypes.firstOrNull { it.id == typeId }
        if (type != null) {
            MeasureTypeHistorySheet(type = type, entries = uiState.entriesForSelectedType, viewModel = viewModel)
        }
    }

    if (uiState.isManageTypesOpen) {
        MeasureManageTypesSheet(allTypes = uiState.allTypes, viewModel = viewModel)
    }
}

@Composable
private fun TodayCard(
    types: List<MeasurementType>,
    onQuickAdd: (Long, Double, Long, Int) -> Unit,
) {
    val weightType = types.firstOrNull { it.key == "body_weight" }
    val bodyFatType = types.firstOrNull { it.key == "body_fat_pct" }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Today", fontWeight = FontWeight.Bold)
            weightType?.let { QuickAddRow(label = "Weight (kg)", type = it, onQuickAdd = onQuickAdd) }
            bodyFatType?.let { QuickAddRow(label = "Body fat %", type = it, onQuickAdd = onQuickAdd) }
        }
    }
}

@Composable
private fun QuickAddRow(
    label: String,
    type: MeasurementType,
    onQuickAdd: (Long, Double, Long, Int) -> Unit,
) {
    var value by remember(type.id) { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                value.toDoubleOrNull()?.let { onQuickAdd(type.id, it, System.currentTimeMillis(), todayAsLocalDate()) }
                value = ""
            },
        ) { Text("Add") }
    }
}

@Composable
private fun MeasurementTypeRow(
    type: MeasurementType,
    summary: MeasurementSummary?,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(type.displayName, fontWeight = FontWeight.Bold)
                    val latest = summary?.latestValue
                    val delta = summary?.deltaSinceLast
                    Text(
                        if (latest != null) {
                            "$latest${if (delta != null) " (Δ ${"%.1f".format(delta)})" else ""}"
                        } else {
                            "No entries yet"
                        },
                    )
                }
                Sparkline(
                    values = summary?.sparkline.orEmpty(),
                    modifier = Modifier.width(SPARKLINE_WIDTH_DP.dp).height(SPARKLINE_HEIGHT_DP.dp),
                )
            }
        }
    }
}

@Composable
private fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val points =
            values.mapIndexed { index, v ->
                Offset(index * stepX, size.height - ((v - min) / range * size.height).toFloat())
            }
        for (i in 0 until points.size - 1) {
            drawLine(
                color = Color(SPARKLINE_COLOR_ARGB),
                start = points[i],
                end = points[i + 1],
                strokeWidth = SPARKLINE_STROKE_WIDTH,
            )
        }
    }
}

@Composable
private fun DerivedMetricsCard(
    derived: DerivedMetrics?,
    needsProfileSetup: Boolean,
    onSetProfile: (Double, Sex) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Derived", fontWeight = FontWeight.Bold)
            if (needsProfileSetup) {
                ProfileSetupPrompt(onSetProfile = onSetProfile)
            } else if (derived == null) {
                Text("Add a body-weight entry to see derived metrics")
            } else {
                DerivedMetricsRows(derived)
            }
        }
    }
}

@Composable
private fun DerivedMetricsRows(derived: DerivedMetrics) {
    derived.bmi?.let { Text("BMI: ${"%.1f".format(it)} (derived from weight & height)") }
    derived.navyBodyFatPercent?.let { Text("Navy body fat: ${"%.1f".format(it)}% (derived from waist, neck, height)") }
    derived.leanMassKg?.let { Text("Lean mass: ${"%.1f".format(it)} kg (derived)") }
    derived.fatMassKg?.let { Text("Fat mass: ${"%.1f".format(it)} kg (derived)") }
    derived.ffmi?.let { Text("FFMI: ${"%.1f".format(it)} (derived)") }
    derived.bodyweightEma?.let { Text("7-day weight trend: ${"%.1f".format(it)} kg (derived)") }
}

@Composable
private fun ProfileSetupPrompt(onSetProfile: (Double, Sex) -> Unit) {
    var height by remember { mutableStateOf("") }
    var isMale by remember { mutableStateOf(true) }
    Text("Set height and sex to see derived metrics (used only for these calculations)")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = height,
            onValueChange = { height = it },
            label = { Text("Height (cm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { isMale = !isMale }) { Text(if (isMale) "Male" else "Female") }
        TextButton(
            onClick = { height.toDoubleOrNull()?.let { onSetProfile(it, if (isMale) Sex.MALE else Sex.FEMALE) } },
        ) {
            Text("Save")
        }
    }
}

private fun todayAsLocalDate(): Int = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()

private const val SPARKLINE_WIDTH_DP = 80
private const val SPARKLINE_HEIGHT_DP = 32
private val SPARKLINE_COLOR_ARGB = 0xFF14548C.toInt()
private const val SPARKLINE_STROKE_WIDTH = 4f
