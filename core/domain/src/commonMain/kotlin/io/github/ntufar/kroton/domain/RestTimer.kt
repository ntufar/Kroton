package io.github.ntufar.kroton.domain

import kotlinx.coroutines.flow.StateFlow

data class RestTimerState(
    val totalSec: Int,
    val remainingSec: Int,
)

/** Platform-backed rest timer (Android: a foreground service so it survives the screen locking). */
interface RestTimerController {
    val state: StateFlow<RestTimerState?>

    fun start(totalSec: Int)

    fun adjust(deltaSec: Int)

    fun skip()
}

const val DEFAULT_REST_SEC = 90
