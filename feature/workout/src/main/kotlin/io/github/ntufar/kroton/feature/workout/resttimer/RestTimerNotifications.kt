package io.github.ntufar.kroton.feature.workout.resttimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.ntufar.kroton.domain.RestTimerState

/** Builds and posts the ongoing rest-timer notification, kept separate from [RestTimerService]'s
 * lifecycle/countdown logic for cohesion. */
internal class RestTimerNotifications(private val context: Context) {
    fun post(state: RestTimerState) {
        ensureChannel()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, build(state))
    }

    val notificationId: Int get() = NOTIFICATION_ID

    fun build(state: RestTimerState): Notification {
        ensureChannel()
        val skipIntent = actionPendingIntent(RestTimerService.ACTION_SKIP, REQUEST_SKIP)
        val minusIntent =
            actionPendingIntent(RestTimerService.ACTION_ADJUST, REQUEST_MINUS, -ADJUST_STEP_SEC)
        val plusIntent =
            actionPendingIntent(RestTimerService.ACTION_ADJUST, REQUEST_PLUS, ADJUST_STEP_SEC)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Rest timer")
            .setContentText(formatSeconds(state.remainingSec))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "−15s", minusIntent)
            .addAction(0, "+15s", plusIntent)
            .addAction(0, "Skip", skipIntent)
            .build()
    }

    private fun actionPendingIntent(
        action: String,
        requestCode: Int,
        deltaSec: Int? = null,
    ): PendingIntent {
        val intent =
            Intent(context, RestTimerService::class.java).apply {
                this.action = action
                if (deltaSec != null) putExtra(RestTimerService.EXTRA_DELTA_SEC, deltaSec)
            }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Rest timer", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun formatSeconds(totalSec: Int): String {
        val m = totalSec / SEC_PER_MIN
        val s = totalSec % SEC_PER_MIN
        return "%d:%02d".format(m, s)
    }

    companion object {
        private const val CHANNEL_ID = "rest_timer"
        private const val NOTIFICATION_ID = 4201
        private const val ADJUST_STEP_SEC = 15
        private const val REQUEST_SKIP = 1
        private const val REQUEST_MINUS = 2
        private const val REQUEST_PLUS = 3
        private const val SEC_PER_MIN = 60
    }
}
