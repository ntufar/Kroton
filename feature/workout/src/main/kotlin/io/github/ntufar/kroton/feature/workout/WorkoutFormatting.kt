package io.github.ntufar.kroton.feature.workout

import androidx.compose.ui.graphics.Color

internal const val WEIGHT_STEP_KG = 2.5
internal const val MIN_SUPERSET_SIZE = 2

private const val SEC_PER_HOUR = 3600
private const val SEC_PER_MIN = 60
private const val SUPERSET_HUE_BUCKETS = 6
private const val DEGREES_PER_CIRCLE = 360.0
private const val SUPERSET_SATURATION = 0.6f
private const val SUPERSET_VALUE = 0.8f

internal fun formatWeight(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

internal fun formatElapsed(totalSec: Int): String {
    val h = totalSec / SEC_PER_HOUR
    val m = (totalSec % SEC_PER_HOUR) / SEC_PER_MIN
    val s = totalSec % SEC_PER_MIN
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

internal fun supersetColorFor(groupId: Long): Color {
    val hue = (groupId.mod(SUPERSET_HUE_BUCKETS)) * (DEGREES_PER_CIRCLE / SUPERSET_HUE_BUCKETS)
    return Color.hsv(hue.toFloat(), SUPERSET_SATURATION, SUPERSET_VALUE)
}
