package io.github.ntufar.kroton.feature.workout.resttimer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.ntufar.kroton.domain.RestTimerController
import io.github.ntufar.kroton.domain.RestTimerState
import kotlinx.coroutines.flow.StateFlow

class AndroidRestTimerController(private val appContext: Context) : RestTimerController {
    override val state: StateFlow<RestTimerState?> get() = RestTimerService.state

    override fun start(totalSec: Int) {
        val intent =
            Intent(appContext, RestTimerService::class.java)
                .setAction(RestTimerService.ACTION_START)
                .putExtra(RestTimerService.EXTRA_TOTAL_SEC, totalSec)
        ContextCompat.startForegroundService(appContext, intent)
    }

    override fun adjust(deltaSec: Int) {
        if (state.value == null) return
        appContext.startService(
            Intent(appContext, RestTimerService::class.java)
                .setAction(RestTimerService.ACTION_ADJUST)
                .putExtra(RestTimerService.EXTRA_DELTA_SEC, deltaSec),
        )
    }

    override fun skip() {
        if (state.value == null) return
        appContext.startService(
            Intent(appContext, RestTimerService::class.java).setAction(RestTimerService.ACTION_SKIP),
        )
    }
}
