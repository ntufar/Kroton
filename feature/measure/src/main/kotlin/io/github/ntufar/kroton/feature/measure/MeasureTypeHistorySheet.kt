package io.github.ntufar.kroton.feature.measure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.ntufar.kroton.model.MeasurementEntry
import io.github.ntufar.kroton.model.MeasurementType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasureTypeHistorySheet(
    type: MeasurementType,
    entries: List<MeasurementEntry>,
    viewModel: MeasureViewModel,
) {
    ModalBottomSheet(onDismissRequest = viewModel::closeTypeHistory, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(type.displayName, fontWeight = FontWeight.Bold)
            BackdatedAddRow(typeId = type.id, viewModel = viewModel)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(HISTORY_LIST_HEIGHT_DP.dp)) {
                items(entries, key = { it.id }) { entry -> HistoryEntryRow(entry = entry, viewModel = viewModel) }
            }
        }
    }
}

@Composable
private fun BackdatedAddRow(
    typeId: Long,
    viewModel: MeasureViewModel,
) {
    var value by remember(typeId) { mutableStateOf("") }
    var dateText by remember(typeId) { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text("Value") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Date") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                val v = value.toDoubleOrNull() ?: return@TextButton
                val parsed = runCatching { LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                val date = parsed ?: return@TextButton
                val recordedAt = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val localDate = date.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toInt()
                viewModel.addBackdatedEntry(typeId, v, recordedAt, localDate)
                value = ""
            },
        ) { Text("Add") }
    }
}

@Composable
private fun HistoryEntryRow(
    entry: MeasurementEntry,
    viewModel: MeasureViewModel,
) {
    var value by remember(entry.id) { mutableStateOf(entry.value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(dateLabel(entry.recordedAt), modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                it.toDoubleOrNull()?.let { v -> viewModel.updateEntry(entry.id, v) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { viewModel.deleteEntry(entry.id) }) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
        }
    }
}

private fun dateLabel(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))

private const val HISTORY_LIST_HEIGHT_DP = 300
