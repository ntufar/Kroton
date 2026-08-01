package io.github.ntufar.kroton.backup

import android.app.backup.BackupAgentHelper
import android.app.backup.FullBackupDataOutput
import io.github.ntufar.kroton.domain.BackupRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

/**
 * A heavy user at 60,000 sets is a few hundred KB gzipped (spec §6.7) — comfortably inside the
 * 25 MB Auto Backup quota the raw SQLite files are not. `onFullBackup` refreshes the snapshot
 * immediately before delegating to the default full-backup implementation, which then only sees
 * this file plus whatever `data_extraction_rules.xml`/`backup_rules.xml` don't exclude.
 */
class KrotonBackupAgent : BackupAgentHelper(), KoinComponent {
    private val backupRepository: BackupRepository by inject()

    override fun onFullBackup(data: FullBackupDataOutput) {
        writeSnapshot()
        super.onFullBackup(data)
    }

    private fun writeSnapshot() {
        val nowIso = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val appVersion =
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "1.0.0"
        val backup = runBlocking { backupRepository.buildBackup(nowIso, appVersion) }
        val json =
            kotlinx.serialization.json.Json.encodeToString(
                io.github.ntufar.kroton.export.BackupJson.serializer(),
                backup,
            )
        GZIPOutputStream(FileOutputStream(BackupSnapshot.file(this))).use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }
}
