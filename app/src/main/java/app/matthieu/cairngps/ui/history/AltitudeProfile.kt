package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.formatTimeOfDay
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.ui.theme.AchievementLabelGold
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.LightAchievementLabelGold
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.ValueMuted
import kotlin.math.abs
import kotlin.math.roundToInt

/** Screen-space x of the sample at [index]; a single-point track collapses to the left edge. */
private fun xForIndex(index: Int, trackSize: Int, width: Float): Float =
    if (trackSize > 1) index.toFloat() / (trackSize - 1) * width else 0f

/** Inverse of [xForIndex]: the sample nearest the touched x. */
private fun indexAt(x: Float, width: Float, trackSize: Int): Int =
    if (width <= 0f || trackSize <= 1) 0
    else ((x / width) * (trackSize - 1)).roundToInt().coerceIn(0, trackSize - 1)

/**
 * Elevation-over-time area chart (screen 1j "PROFIL D'ALTITUDE"): a filled curve scaled between
 * the track's min and max altitude, with those two values labelled below. `track` must be
 * chronologically ordered and non-empty — callers should only show this card when there's data
 * (older sessions recorded before the track-points feature existed have none).
 *
 * Dragging horizontally moves a cursor along the curve, reporting the touched sample through
 * [onSelectedIndexChange] so the caller can echo it elsewhere (the route trace marks the same
 * point). The cursor stays put once the finger lifts, so the value can be read hands-free.
 */
@Composable
fun AltitudeProfile(
    track: List<TrackPoint>,
    unitSystem: UnitSystem,
    selectedIndex: Int?,
    onSelectedIndexChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (track.isEmpty()) return

    val minAltitude = track.minOf { it.altitude }
    val maxAltitude = track.maxOf { it.altitude }
    val light = LocalIsLightTheme.current
    val lineColor = if (light) CairnGreenDark else CairnGreen
    val fillColor = lineColor.copy(alpha = 0.18f)
    val cursorColor = if (light) LightAchievementLabelGold else AchievementLabelGold
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    // The gesture lambda lives across recompositions, so read the callback through a holder.
    val currentOnSelectedIndexChange by rememberUpdatedState(onSelectedIndexChange)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .pointerInput(track) {
                    // Hand-rolled instead of detectHorizontalDragGestures: the cursor must land on
                    // the first touch (before any slop), yet a vertical swipe has to keep scrolling
                    // the surrounding column rather than being swallowed here.
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        currentOnSelectedIndexChange(indexAt(down.position.x, size.width.toFloat(), track.size))
                        var dx = 0f
                        var dy = 0f
                        var claimed = false
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.isConsumed && !claimed) break // the scrolling parent took over
                            dx += change.positionChange().x
                            dy += change.positionChange().y
                            if (!claimed) {
                                if (abs(dy) > slop && abs(dy) > abs(dx)) break
                                if (abs(dx) > slop) claimed = true
                            }
                            if (claimed) change.consume()
                            currentOnSelectedIndexChange(indexAt(change.position.x, size.width.toFloat(), track.size))
                        }
                    }
                },
        ) {
            val range = (maxAltitude - minAltitude).takeIf { it > 0.0 } ?: 1.0
            // Normalized (0..1) screen-space points: x by index (evenly spaced — samples are
            // roughly evenly spaced in time, close enough for a profile shape at this scale),
            // y inverted since higher altitude should draw higher on screen.
            val points = track.mapIndexed { index, point ->
                val fraction = ((point.altitude - minAltitude) / range).toFloat()
                Offset(xForIndex(index, track.size, size.width), size.height * (1f - fraction))
            }

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(path = fillPath, color = fillColor)
            drawPath(path = linePath, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))

            val cursor = selectedIndex?.let { index -> points.getOrNull(index)?.let { index to it } }
            if (cursor != null) {
                val (index, position) = cursor

                drawLine(
                    color = cursorColor.copy(alpha = 0.7f),
                    start = Offset(position.x, 0f),
                    end = Offset(position.x, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())),
                )
                drawCircle(color = surfaceColor, radius = 6.dp.toPx(), center = position)
                drawCircle(color = cursorColor, radius = 4.dp.toPx(), center = position)

                val point = track[index]
                val label = textMeasurer.measure(
                    text = "${formatElevation(point.altitude, unitSystem)} ${shortUnitLabel(unitSystem)}\n" +
                        formatTimeOfDay(point.timestamp),
                    style = TextStyle(
                        color = onSurfaceColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = MonoFontFamily,
                        textAlign = TextAlign.Center,
                    ),
                )
                val padH = 8.dp.toPx()
                val padV = 5.dp.toPx()
                val bubbleWidth = label.size.width + padH * 2
                val bubbleHeight = label.size.height + padV * 2
                val margin = 3.dp.toPx()
                // Centered on the cursor but kept inside the canvas, and flipped to the bottom when
                // the curve runs high enough that the bubble would sit on top of it.
                val bubbleX = (position.x - bubbleWidth / 2f)
                    .coerceIn(0f, (size.width - bubbleWidth).coerceAtLeast(0f))
                val bubbleY = if (position.y < size.height / 3f) {
                    size.height - bubbleHeight - margin
                } else {
                    margin
                }
                val topLeft = Offset(bubbleX, bubbleY)
                val bubbleSize = Size(bubbleWidth, bubbleHeight)
                val corner = CornerRadius(8.dp.toPx())
                drawRoundRect(color = surfaceColor, topLeft = topLeft, size = bubbleSize, cornerRadius = corner)
                drawRoundRect(
                    color = cursorColor,
                    topLeft = topLeft,
                    size = bubbleSize,
                    cornerRadius = corner,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawText(textLayoutResult = label, topLeft = Offset(bubbleX + padH, bubbleY + padV))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${formatElevation(minAltitude, unitSystem)} ${shortUnitLabel(unitSystem)}",
                fontSize = 11.sp,
                fontFamily = MonoFontFamily,
                color = ValueMuted,
            )
            Text(
                text = "max ${formatElevation(maxAltitude, unitSystem)} ${shortUnitLabel(unitSystem)}",
                fontSize = 11.sp,
                fontFamily = MonoFontFamily,
                color = ValueMuted,
            )
        }
    }
}
