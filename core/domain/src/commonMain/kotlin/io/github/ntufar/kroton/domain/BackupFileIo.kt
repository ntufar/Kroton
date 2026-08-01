package io.github.ntufar.kroton.domain

import io.github.ntufar.kroton.export.BackupJson
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val json =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

/** `.kroton` = a ZIP of `backup.json` + `photos/` (spec §6.3) — the actual on-disk backup file
 * format. `BackupRepository` only knows the `BackupJson` tree; this is where it becomes bytes. */
object BackupFileIo {
    fun writeKrotonZip(
        backup: BackupJson,
        photosDir: File,
        outputStream: OutputStream,
    ) {
        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(json.encodeToString(BackupJson.serializer(), backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (photosDir.exists()) {
                photosDir.listFiles()?.forEach { file ->
                    zip.putNextEntry(ZipEntry("photos/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Extracts `backup.json` and copies any `photos/` entries into [photosOutputDir]. */
    fun readKrotonZip(
        inputStream: InputStream,
        photosOutputDir: File,
    ): BackupJson {
        var backup: BackupJson? = null
        photosOutputDir.mkdirs()
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "backup.json" -> {
                        val bytes = zip.readBytes().decodeToString()
                        backup = json.decodeFromString(BackupJson.serializer(), bytes)
                    }
                    name.startsWith("photos/") && !entry.isDirectory ->
                        File(photosOutputDir, name.removePrefix("photos/")).outputStream().use {
                                out ->
                            zip.copyTo(out)
                        }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return requireNotNull(backup) { "backup.json missing from .kroton archive" }
    }
}
