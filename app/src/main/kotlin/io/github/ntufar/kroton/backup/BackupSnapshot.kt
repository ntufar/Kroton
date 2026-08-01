package io.github.ntufar.kroton.backup

import android.content.Context
import java.io.File

/** The single file Auto Backup is allowed to see (spec §6.7) — a gzipped JSON snapshot, never
 * the raw SQLite files or photos (both excluded via `data_extraction_rules.xml`/`backup_rules.xml`).
 * Shared between `KrotonBackupAgent` (writes it before a backup) and `KrotonApplication` (restores
 * from it on first launch after a reinstall, then deletes it). */
object BackupSnapshot {
    private const val DIR_NAME = "backup_snapshot"
    private const val FILE_NAME = "snapshot.json.gz"

    fun file(context: Context): File = File(context.filesDir, "$DIR_NAME/$FILE_NAME").apply { parentFile?.mkdirs() }
}
