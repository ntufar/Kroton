package io.github.ntufar.kroton.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.ntufar.kroton.domain.RestoreMode
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { AutoBackupSection() }
        item { Spacer(Modifier.height(16.dp)) }
        item { ExportSection(onExport = { format -> triggerExport(viewModel, context, format) }) }
        item { Spacer(Modifier.height(16.dp)) }
        item { ShareLastExportSection(uiState = uiState) }
        item { Spacer(Modifier.height(16.dp)) }
        item { ImportSection(viewModel = viewModel) }
        item { Spacer(Modifier.height(16.dp)) }
        item { RestoreSection(viewModel = viewModel) }
        uiState.statusMessage?.let { message -> item { Text(message) } }
    }

    uiState.importPreview?.let { preview ->
        ImportPreviewSheet(preview = preview, onConfirm = viewModel::confirmImport, onCancel = viewModel::cancelImport)
    }
    uiState.restorePreview?.let { backup ->
        RestorePreviewSheet(
            workoutCount = backup.workouts.size,
            onConfirm = { mode -> viewModel.confirmRestore(context, mode) },
            onCancel = viewModel::cancelRestore,
        )
    }
}

private const val AUTO_BACKUP_PREFS_NAME = "auto_backup"
private const val AUTO_BACKUP_KEY_TREE_URI = "tree_uri"
private const val AUTO_BACKUP_KEY_ENABLED = "enabled"

@Composable
private fun AutoBackupSection() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(AUTO_BACKUP_PREFS_NAME, android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(AUTO_BACKUP_KEY_ENABLED, false)) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            prefs.edit().putString(
                AUTO_BACKUP_KEY_TREE_URI,
                uri.toString(),
            ).putBoolean(AUTO_BACKUP_KEY_ENABLED, true).apply()
            enabled = true
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Auto-backup", fontWeight = FontWeight.Bold)
        Text(if (enabled) "Daily backup enabled" else "Choose a folder to enable daily backups")
        Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text("Choose backup folder") }
        if (enabled) {
            TextButton(onClick = {
                prefs.edit().putBoolean(AUTO_BACKUP_KEY_ENABLED, false).apply()
                enabled = false
            }) {
                Text("Disable auto-backup")
            }
        }
    }
}

private enum class ExportFormat { XLSX, JSON, CSV }

private fun triggerExport(
    viewModel: SettingsViewModel,
    context: android.content.Context,
    format: ExportFormat,
) {
    when (format) {
        ExportFormat.XLSX -> viewModel.exportXlsx(context)
        ExportFormat.JSON -> viewModel.exportJsonBackup(context)
        ExportFormat.CSV -> viewModel.exportCsvBundle(context)
    }
}

@Composable
private fun ExportSection(onExport: (ExportFormat) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Export", fontWeight = FontWeight.Bold)
        Text(
            "The XLSX report is the pivot-ready spreadsheet; the .kroton file is the lossless backup used for restore.",
        )
        Button(
            onClick = { onExport(ExportFormat.XLSX) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Export XLSX report") }
        Button(onClick = {
            onExport(ExportFormat.JSON)
        }, modifier = Modifier.fillMaxWidth()) { Text("Export full backup (.kroton)") }
        Button(onClick = {
            onExport(ExportFormat.CSV)
        }, modifier = Modifier.fillMaxWidth()) { Text("Export CSV (Sets + Workouts)") }
    }
}

@Composable
private fun ShareLastExportSection(uiState: SettingsUiState) {
    val context = LocalContext.current
    val file = uiState.lastExportFile ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Last export: ${file.name}")
        Button(onClick = { shareFile(context, file, "Share…") }, modifier = Modifier.fillMaxWidth()) { Text("Share…") }
        Button(
            onClick = { shareFile(context, file, "Save to…", saveInstead = true) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save to…")
        }
        Button(onClick = { emailFile(context, file) }, modifier = Modifier.fillMaxWidth()) { Text("Email to myself") }
    }
}

private fun shareFile(
    context: android.content.Context,
    file: java.io.File,
    chooserTitle: String,
    saveInstead: Boolean = false,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val mimeType = mimeTypeFor(file.name)
    if (saveInstead) {
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType
                putExtra(Intent.EXTRA_TITLE, file.name)
            }
        context.startActivity(intent)
        return
    }
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

private fun emailFile(
    context: android.content.Context,
    file: java.io.File,
) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeFor(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kroton export — ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val chooser = Intent.createChooser(intent, "Email export")
    context.startActivity(chooser)
}

private fun mimeTypeFor(fileName: String): String =
    when {
        fileName.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        fileName.endsWith(".zip") -> "application/zip"
        fileName.endsWith(".kroton") -> "application/zip"
        else -> "application/octet-stream"
    }

@Composable
private fun ImportSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var pendingSource by remember { mutableStateOf(ImportSource.HEVY) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.pickImportFile(context, it, pendingSource) }
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Import", fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                pendingSource = ImportSource.HEVY
                launcher.launch("text/*")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import from Hevy (CSV)") }
        Button(
            onClick = {
                pendingSource = ImportSource.STRONG
                launcher.launch("text/*")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import from Strong (CSV)") }
    }
}

@Composable
private fun RestoreSection(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.pickRestoreFile(context, it) }
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Restore from backup", fontWeight = FontWeight.Bold)
        Button(onClick = { launcher.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text("Choose .kroton file") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreviewSheet(
    preview: io.github.ntufar.kroton.domain.ImportPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Import preview", fontWeight = FontWeight.Bold)
            Text("${preview.workoutCount} workouts, ${preview.setCount} sets")
            if (preview.unmatchedExerciseNames.isNotEmpty()) {
                Text("New exercises to create: ${preview.unmatchedExerciseNames.joinToString(", ")}")
            }
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Import") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestorePreviewSheet(
    workoutCount: Int,
    onConfirm: (RestoreMode) -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Restore preview", fontWeight = FontWeight.Bold)
            Text("$workoutCount workouts in this backup. A safety snapshot of your current data is taken first.")
            Button(onClick = { onConfirm(RestoreMode.MERGE) }, modifier = Modifier.fillMaxWidth()) { Text("Merge") }
            Button(
                onClick = { onConfirm(RestoreMode.REPLACE) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Replace everything") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
