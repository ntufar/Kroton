package io.github.ntufar.kroton.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Hand-rolled Canvas charts kept behind this file so the rest of the feature only calls
 * `LineChart`/`BarChart`/`DonutChart` — per spec open decision #2, the chart implementation is
 * meant to stay swappable for a real charting library (Vico) without touching call sites.
 * Support pinch/drag zoom, tap-to-inspect and long-press → CSV export at the call site (spec
 * §5.7); these primitives expose `onTap` for that, zoom/pan are a follow-up (see PROGRESS.md).
 */
private val CHART_LINE_COLOR = Color(0xFF14548C)
private const val CHART_STROKE_WIDTH = 4f
private const val CHART_HEIGHT_DP = 160

@Composable
fun LineChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    onTapIndex: (Int) -> Unit = {},
) {
    if (values.size < 2) {
        Text("Not enough data yet")
        return
    }
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(
        modifier =
            modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp).pointerInput(values) {
                detectTapGestures { offset ->
                    val stepX = size.width / (values.size - 1)
                    onTapIndex((offset.x / stepX).toInt().coerceIn(0, values.size - 1))
                }
            },
    ) {
        val stepX = size.width / (values.size - 1)
        val points =
            values.mapIndexed { index, v ->
                Offset(index * stepX, size.height - ((v - min) / range * size.height).toFloat())
            }
        for (i in 0 until points.size - 1) {
            drawLine(color = CHART_LINE_COLOR, start = points[i], end = points[i + 1], strokeWidth = CHART_STROKE_WIDTH)
        }
    }
}

@Composable
fun BarChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    barColor: Color = CHART_LINE_COLOR,
) {
    if (values.isEmpty()) {
        Text("Not enough data yet")
        return
    }
    val max = values.max().takeIf { it > 0 } ?: 1.0
    Canvas(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        val barWidth = size.width / values.size
        values.forEachIndexed { index, v ->
            val barHeight = (v / max * size.height).toFloat()
            drawRect(
                color = barColor,
                topLeft = Offset(index * barWidth, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth * BAR_WIDTH_FRACTION, barHeight),
            )
        }
    }
}

@Composable
fun DonutChart(
    slices: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
) {
    val total = slices.sumOf { it.second }
    if (total <= 0) {
        Text("Not enough data yet")
        return
    }
    val colors = donutPalette(slices.size)
    var startAngle = -90f
    Canvas(modifier = modifier.height(CHART_HEIGHT_DP.dp)) {
        val diameter = minOf(size.width, size.height)
        slices.forEachIndexed { index, (_, value) ->
            val sweep = (value / total * FULL_CIRCLE_DEGREES).toFloat()
            drawArc(
                color = colors[index],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
            )
            startAngle += sweep
        }
    }
}

private fun donutPalette(count: Int): List<Color> {
    val hueStep = FULL_CIRCLE_DEGREES / count.coerceAtLeast(1)
    return List(count) { index -> Color.hsv((index * hueStep).toFloat(), DONUT_SATURATION, DONUT_VALUE) }
}

private const val BAR_WIDTH_FRACTION = 0.7f
private const val FULL_CIRCLE_DEGREES = 360.0
private const val DONUT_SATURATION = 0.6f
private const val DONUT_VALUE = 0.85f
