package io.github.ntufar.kroton.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.ntufar.kroton.domain.BackupFileIo
import io.github.ntufar.kroton.domain.BackupRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

private const val KEEP_LAST_N = 7
private const val PREFS_NAME = "auto_backup"
private const val KEY_TREE_URI = "tree_uri"
private const val KEY_ENABLED = "enabled"

/** WorkManager daily job (spec §6.4): writes a `.kroton` snapshot to the user-chosen SAF tree,
 * keeping the last 7 and pruning older ones. Requires neither charging nor idle. */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val backupRepository: BackupRepository by inject()

    // Early-return guard clauses (not-enabled / no folder chosen / write failure) read clearer
    // here than threading a single result value through; each is a distinct "nothing to do" or
    // "ask WorkManager to retry" case.
    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return Result.success()
        val treeUriString = prefs.getString(KEY_TREE_URI, null) ?: return Result.success()
        val treeDoc = DocumentFile.fromTreeUri(applicationContext, android.net.Uri.parse(treeUriString))
        return if (treeDoc != null) writeBackupAndPrune(treeDoc) else Result.retry()
    }

    @Suppress("ReturnCount")
    private suspend fun writeBackupAndPrune(treeDoc: DocumentFile): Result {
        val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val backup = backupRepository.buildBackup(nowIso, appVersion())
        val fileName = "kroton-auto-${nowIso.replace(":", "-")}.kroton"
        val newFile = treeDoc.createFile("application/zip", fileName) ?: return Result.retry()
        val written =
            applicationContext.contentResolver.openOutputStream(newFile.uri)?.use {
                BackupFileIo.writeKrotonZip(backup, File(applicationContext.filesDir, "photos"), it)
                true
            }
        if (written != true) return Result.retry()
        pruneOldBackups(treeDoc)
        return Result.success()
    }

    private fun pruneOldBackups(treeDoc: DocumentFile) {
        val backups =
            treeDoc.listFiles()
                .filter { it.name?.startsWith("kroton-auto-") == true }
                .sortedByDescending { it.lastModified() }
        backups.drop(KEEP_LAST_N).forEach { it.delete() }
    }

    private fun appVersion(): String =
        runCatching {
            applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"

    companion object {
        const val WORK_NAME = "kroton-auto-backup"

        fun setEnabled(
            context: Context,
            enabled: Boolean,
            treeUri: android.net.Uri?,
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_ENABLED, enabled)
                treeUri?.let { putString(KEY_TREE_URI, it.toString()) }
            }.apply()
        }

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE,
            ).getBoolean(KEY_ENABLED, false)
    }
}
