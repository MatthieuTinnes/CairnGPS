package app.matthieu.cairngps.ui.compass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.ui.theme.CompassDialBorder
import app.matthieu.cairngps.ui.theme.CompassDialFill
import app.matthieu.cairngps.ui.theme.CompassTickMajor
import app.matthieu.cairngps.ui.theme.CompassTickMinor
import app.matthieu.cairngps.ui.theme.DarkBackground
import app.matthieu.cairngps.ui.theme.ValueMuted
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the compass rose rotated by `-heading` so its north mark points to real (magnetic or true)
 * north. There is no needle — matching the design (1c), the current heading is read from the
 * fixed numeric readout and the fixed amber index at the top; the rose underneath just carries
 * the tick marks, cardinal letters and degree numbers. When a navigation target is set, a green
 * dot on the ring marks its bearing.
 */
internal fun DrawScope.drawCompassRose(
    heading: Float,
    targetBearing: Float?,
    roseLabels: Array<String>,
    cardinalColor: Color,
    indexColor: Color,
    targetColor: Color,
    textMeasurer: TextMeasurer,
) {
    // Reserve a margin at the edge for the fixed heading index, so it sits clear of the ticks.
    val indexMargin = 16.dp.toPx()
    val radius = size.minDimension / 2f - indexMargin
    val center = Offset(size.width / 2f, size.height / 2f)

    drawDialBase(center, radius)

    rotate(degrees = -heading, pivot = center) {
        drawTicksAndLabels(center, radius, roseLabels, cardinalColor, indexColor, textMeasurer)
        // Rotates with the ticks: its screen position still tracks the true/magnetic bearing
        // regardless of which way the device currently points.
        drawTargetDot(center, radius, targetBearing, targetColor)
    }

    drawHeadingIndex(center, radius, indexColor)
}

/** Solid puck fill behind the whole dial, with a thin border — matching the design's flat circle. */
private fun DrawScope.drawDialBase(center: Offset, radius: Float) {
    drawCircle(color = CompassDialFill, radius = radius, center = center)
    drawCircle(
        color = CompassDialBorder,
        radius = radius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

/** Tick marks every 5°, longer/thicker and paired with a label (cardinal letter or degree) on the 30° marks. */
private fun DrawScope.drawTicksAndLabels(
    center: Offset,
    radius: Float,
    roseLabels: Array<String>,
    cardinalColor: Color,
    indexColor: Color,
    textMeasurer: TextMeasurer,
) {
    for (i in 0 until 72) {
        val deg = i * 5
        val isMajor = i % 6 == 0
        val rad = Math.toRadians(deg.toDouble())
        val sinV = sin(rad).toFloat()
        val cosV = cos(rad).toFloat()
        val outer = radius - 4.dp.toPx()
        val inner = outer - (if (isMajor) 18.dp.toPx() else 10.dp.toPx())
        drawLine(
            color = if (isMajor) CompassTickMajor else CompassTickMinor,
            start = Offset(center.x + inner * sinV, center.y - inner * cosV),
            end = Offset(center.x + outer * sinV, center.y - outer * cosV),
            strokeWidth = (if (isMajor) 2.5f else 1f).dp.toPx(),
        )

        // Cardinal letters at N/E/S/O (roseLabels order); the other 30° marks get their
        // degree number instead, smaller and muted.
        if (isMajor) {
            val cardinalIndex = deg / 90
            val isCardinal = deg % 90 == 0
            val labelRadius = radius - 42.dp.toPx()
            val x = center.x + labelRadius * sinV
            val y = center.y - labelRadius * cosV
            val layout = textMeasurer.measure(
                text = if (isCardinal) roseLabels[cardinalIndex] else deg.toString(),
                style = TextStyle(
                    color = if (deg == 0) indexColor else if (isCardinal) cardinalColor else ValueMuted,
                    fontSize = if (isCardinal) 19.sp else 11.sp,
                    fontWeight = if (isCardinal) FontWeight.Bold else FontWeight.Medium,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
            )
        }
    }
}

/** Target bearing marker: a small filled dot on the ring, haloed with the background color for contrast. */
private fun DrawScope.drawTargetDot(center: Offset, radius: Float, targetBearing: Float?, targetColor: Color) {
    if (targetBearing == null) return
    val rad = Math.toRadians(targetBearing.toDouble())
    val tickRadius = radius - 4.dp.toPx()
    val dotCenter = Offset(
        center.x + tickRadius * sin(rad).toFloat(),
        center.y - tickRadius * cos(rad).toFloat(),
    )
    drawCircle(color = DarkBackground, radius = 8.dp.toPx(), center = dotCenter)
    drawCircle(color = targetColor, radius = 7.dp.toPx(), center = dotCenter)
}

/**
 * Fixed heading index, drawn last so it stays on top of the ticks. Points down at the ring from
 * the reserved margin.
 */
private fun DrawScope.drawHeadingIndex(center: Offset, radius: Float, indexColor: Color) {
    drawPath(
        Path().apply {
            moveTo(center.x, center.y - radius)
            lineTo(center.x - 11.dp.toPx(), center.y - radius - 14.dp.toPx())
            lineTo(center.x + 11.dp.toPx(), center.y - radius - 14.dp.toPx())
            close()
        },
        color = indexColor,
    )
}
