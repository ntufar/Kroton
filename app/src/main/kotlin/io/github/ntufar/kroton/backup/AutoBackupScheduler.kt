package io.github.ntufar.kroton.backup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Enqueues (or no-ops if already scheduled) the daily auto-backup job — spec §6.4 requires
 * neither charging nor idle, so no `Constraints` are attached. Safe to call on every app start;
 * `KEEP` means an already-scheduled job isn't reset. */
object AutoBackupScheduler {
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(AutoBackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
