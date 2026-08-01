package io.github.ntufar.kroton

import android.app.Application
import io.github.ntufar.kroton.backup.AutoBackupScheduler
import io.github.ntufar.kroton.backup.BackupSnapshot
import io.github.ntufar.kroton.di.appModules
import io.github.ntufar.kroton.domain.BackupRepository
import io.github.ntufar.kroton.domain.ExerciseSeeder
import io.github.ntufar.kroton.domain.InventorySeeder
import io.github.ntufar.kroton.domain.MeasurementSeeder
import io.github.ntufar.kroton.domain.ProfileRepository
import io.github.ntufar.kroton.domain.RestoreMode
import io.github.ntufar.kroton.export.BackupJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.util.zip.GZIPInputStream

class KrotonApplication : Application() {
    private val exerciseSeeder: ExerciseSeeder by inject()
    private val inventorySeeder: InventorySeeder by inject()
    private val measurementSeeder: MeasurementSeeder by inject()
    private val profileRepository: ProfileRepository by inject()
    private val backupRepository: BackupRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KrotonApplication)
            modules(appModules)
        }
        CoroutineScope(Dispatchers.IO).launch {
            exerciseSeeder.seedIfEmpty(System.currentTimeMillis())
            inventorySeeder.seedIfEmpty()
            measurementSeeder.seedIfEmpty()
            profileRepository.ensureSeeded()
            restoreFromPlatformSnapshotIfPresent()
        }
        AutoBackupScheduler.ensureScheduled(this)
    }

    /** Spec §6.7: "On restore, the app finds the snapshot on first launch, imports it, and
     * deletes it." Platform Auto Backup restores files to their original path before this runs,
     * so a snapshot's mere presence on a fresh install means "restored, not yet imported". */
    private suspend fun restoreFromPlatformSnapshotIfPresent() {
        val file = BackupSnapshot.file(this)
        if (!file.exists()) return
        val json = GZIPInputStream(file.inputStream()).use { it.readBytes().decodeToString() }
        val backup = Json { ignoreUnknownKeys = true }.decodeFromString(BackupJson.serializer(), json)
        backupRepository.restore(backup, RestoreMode.REPLACE)
        file.delete()
    }
}
