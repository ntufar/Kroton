package io.github.ntufar.kroton.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ntufar.kroton.domain.BackupFileIo
import io.github.ntufar.kroton.domain.BackupRepository
import io.github.ntufar.kroton.domain.ImportPreview
import io.github.ntufar.kroton.domain.ImportRepository
import io.github.ntufar.kroton.domain.RestoreMode
import io.github.ntufar.kroton.export.CsvExportBuilder
import io.github.ntufar.kroton.export.CsvParser
import io.github.ntufar.kroton.export.HevyImport
import io.github.ntufar.kroton.export.ImportedWorkoutRow
import io.github.ntufar.kroton.export.StrongImport
import io.github.ntufar.kroton.export.XlsxWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ImportSource { HEVY, STRONG }

data class SettingsUiState(
    val statusMessage: String? = null,
    val lastExportFile: File? = null,
    val pendingImportRows: List<ImportedWorkoutRow>? = null,
    val importPreview: ImportPreview? = null,
    val restorePreview: io.github.ntufar.kroton.export.BackupJson? = null,
)

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val importRepository: ImportRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun exportXlsx(context: Context) {
        viewModelScope.launch {
            val backup = backupRepository.buildBackup(nowIso8601(), appVersion(context))
            val file = exportFile(context, "kroton-export.xlsx")
            file.outputStream().use { XlsxWriter.write(backup, it) }
            _uiState.update { it.copy(lastExportFile = file, statusMessage = "XLSX exported") }
        }
    }

    fun exportJsonBackup(context: Context) {
        viewModelScope.launch {
            val backup = backupRepository.buildBackup(nowIso8601(), appVersion(context))
            val file = exportFile(context, "kroton-backup.kroton")
            val photosDir = File(context.filesDir, "photos")
            file.outputStream().use { BackupFileIo.writeKrotonZip(backup, photosDir, it) }
            _uiState.update { it.copy(lastExportFile = file, statusMessage = "Backup exported") }
        }
    }

    fun exportCsvBundle(context: Context) {
        viewModelScope.launch {
            val backup = backupRepository.buildBackup(nowIso8601(), appVersion(context))
            val file = exportFile(context, "kroton-export-csv.zip")
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("Sets.csv"))
                zip.write(CsvExportBuilder.setsCsv(backup).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("Workouts.csv"))
                zip.write(CsvExportBuilder.workoutsCsv(backup).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            _uiState.update { it.copy(lastExportFile = file, statusMessage = "CSV exported") }
        }
    }

    fun pickImportFile(
        context: Context,
        uri: Uri,
        source: ImportSource,
    ) {
        viewModelScope.launch {
            val stream = context.contentResolver.openInputStream(uri) ?: return@launch
            val text = stream.bufferedReader().use { it.readText() }
            val parsedRows = CsvParser.parseRows(text)
            val rows = if (source == ImportSource.HEVY) HevyImport.parse(parsedRows) else StrongImport.parse(parsedRows)
            val preview = importRepository.preview(rows)
            _uiState.update { it.copy(pendingImportRows = rows, importPreview = preview) }
        }
    }

    fun confirmImport() {
        val rows = _uiState.value.pendingImportRows ?: return
        viewModelScope.launch {
            val result = importRepository.import(rows, System.currentTimeMillis())
            _uiState.update {
                it.copy(
                    pendingImportRows = null,
                    importPreview = null,
                    statusMessage = "Imported ${result.workoutsImported} workouts, ${result.setsImported} sets",
                )
            }
        }
    }

    fun cancelImport() = _uiState.update { it.copy(pendingImportRows = null, importPreview = null) }

    fun pickRestoreFile(
        context: Context,
        uri: Uri,
    ) {
        viewModelScope.launch {
            val photosTemp = File(context.cacheDir, "restore_photos").apply { mkdirs() }
            val backup =
                context.contentResolver.openInputStream(uri)?.use {
                    BackupFileIo.readKrotonZip(it, photosTemp)
                } ?: return@launch
            _uiState.update { it.copy(restorePreview = backup) }
        }
    }

    fun confirmRestore(
        context: Context,
        mode: RestoreMode,
    ) {
        val backup = _uiState.value.restorePreview ?: return
        viewModelScope.launch {
            // Safety snapshot before restore (spec §6.3) so the user can roll back one tap.
            val rollbackBackup = backupRepository.buildBackup(nowIso8601(), appVersion(context))
            val rollbackFile = exportFile(context, "kroton-pre-restore-safety.kroton")
            rollbackFile.outputStream().use {
                BackupFileIo.writeKrotonZip(
                    rollbackBackup,
                    File(context.filesDir, "photos"),
                    it,
                )
            }

            val photosTemp = File(context.cacheDir, "restore_photos")
            if (photosTemp.exists()) {
                val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
                photosTemp.listFiles()?.forEach { it.copyTo(File(photosDir, it.name), overwrite = true) }
            }
            val summary = backupRepository.restore(backup, mode)
            val message = "Restored ${summary.workoutsImported} workouts (safety snapshot: ${rollbackFile.name})"
            _uiState.update { it.copy(restorePreview = null, statusMessage = message) }
        }
    }

    fun cancelRestore() = _uiState.update { it.copy(restorePreview = null) }

    fun dismissStatus() = _uiState.update { it.copy(statusMessage = null) }

    private fun exportFile(
        context: Context,
        name: String,
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, name)
    }

    private fun nowIso8601(): String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
}
