package io.github.ntufar.kroton.feature.measure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import io.github.ntufar.kroton.model.MeasurementType
import io.github.ntufar.kroton.model.UnitKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasureManageTypesSheet(
    allTypes: List<MeasurementType>,
    viewModel: MeasureViewModel,
) {
    ModalBottomSheet(
        onDismissRequest = { viewModel.setManageTypesOpen(false) },
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Manage measurement types", fontWeight = FontWeight.Bold)
            allTypes.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = type.isEnabled, onCheckedChange = { viewModel.setTypeEnabled(type.id, it) })
                    Text(type.displayName, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            NewCustomTypeRow(viewModel = viewModel)
        }
    }
}

@Composable
private fun NewCustomTypeRow(viewModel: MeasureViewModel) {
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("New custom type") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                if (name.isNotBlank()) {
                    viewModel.createCustomType(name, UnitKind.LENGTH)
                    name = ""
                }
            },
        ) { Text("Add") }
    }
}
