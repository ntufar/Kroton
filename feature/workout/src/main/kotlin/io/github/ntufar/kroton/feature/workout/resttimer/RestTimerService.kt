package io.github.ntufar.kroton.feature.workout.resttimer

import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.github.ntufar.kroton.domain.RestTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service driving the rest-timer countdown so it keeps running with the screen
 * locked. State is exposed via a companion [StateFlow] rather than binding, since the UI only
 * ever needs to observe it, never call into the service directly.
 */
class RestTimerService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val notifications by lazy { RestTimerNotifications(this) }
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> startCountdown(intent.getIntExtra(EXTRA_TOTAL_SEC, DEFAULT_TOTAL_SEC))
            ACTION_ADJUST -> adjust(intent.getIntExtra(EXTRA_DELTA_SEC, 0))
            ACTION_SKIP -> stopCountdown()
        }
        return START_NOT_STICKY
    }

    private fun startCountdown(totalSec: Int) {
        tickJob?.cancel()
        _state.value = RestTimerState(totalSec = totalSec, remainingSec = totalSec)
        startForeground(notifications.notificationId, notifications.build(_state.value!!))
        tickJob =
            scope.launch {
                while (_state.value != null && _state.value!!.remainingSec > 0) {
                    delay(TICK_MS)
                    val current = _state.value ?: break
                    val remaining = (current.remainingSec - 1).coerceAtLeast(0)
                    _state.value = current.copy(remainingSec = remaining)
                    notifications.post(_state.value!!)
                }
                if (_state.value != null) onCountdownFinished()
            }
    }

    private fun adjust(deltaSec: Int) {
        val current = _state.value ?: return
        val remaining = (current.remainingSec + deltaSec).coerceIn(0, MAX_REST_SEC)
        _state.value = current.copy(remainingSec = remaining, totalSec = maxOf(current.totalSec, remaining))
        notifications.post(_state.value!!)
        if (remaining == 0) onCountdownFinished()
    }

    private fun onCountdownFinished() {
        tickJob?.cancel()
        vibrate()
        playTone()
        stopCountdown()
    }

    private fun stopCountdown() {
        tickJob?.cancel()
        _state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        tickJob?.cancel()
        scope.cancel()
        _state.value = null
    }

    private fun vibrate() {
        val vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun playTone() {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION_MS)
        Handler(Looper.getMainLooper()).postDelayed({ toneGenerator.release() }, TONE_DURATION_MS.toLong())
    }

    companion object {
        const val ACTION_START = "io.github.ntufar.kroton.action.REST_TIMER_START"
        const val ACTION_ADJUST = "io.github.ntufar.kroton.action.REST_TIMER_ADJUST"
        const val ACTION_SKIP = "io.github.ntufar.kroton.action.REST_TIMER_SKIP"
        const val EXTRA_TOTAL_SEC = "totalSec"
        const val EXTRA_DELTA_SEC = "deltaSec"

        private const val TICK_MS = 1000L
        private const val VIBRATE_MS = 400L
        private const val TONE_DURATION_MS = 300
        private const val DEFAULT_TOTAL_SEC = 90
        private const val MAX_REST_SEC = 3600

        private val _state = MutableStateFlow<RestTimerState?>(null)
        val state: StateFlow<RestTimerState?> = _state
    }
}
