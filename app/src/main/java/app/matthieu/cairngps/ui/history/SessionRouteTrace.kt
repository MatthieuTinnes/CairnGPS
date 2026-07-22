package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.domain.distanceAndBearing
import app.matthieu.cairngps.domain.format.formatShortDistance
import app.matthieu.cairngps.domain.format.formatTimeOfDay
import app.matthieu.cairngps.ui.theme.AchievementLabelGold
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightAchievementLabelGold
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily

/**
 * The session's route shape (screen 1j), with a filled start marker and a hollow end marker, plus
 * their departure/arrival times below. `track` must be non-empty; callers should skip this card
 * otherwise (sessions recorded before the track-points feature existed have none).
 *
 * [selectedIndex] echoes the altitude/speed profiles' cursor: the matching track point is
 * highlighted here, with a bubble showing the distance covered up to that point (summed from
 * consecutive points via [distanceAndBearing], since [TrackPoint] doesn't store a running total),
 * so the value being read on either profile can be located on the route.
 */
@Composable
fun SessionRouteTrace(
    track: List<TrackPoint>,
    unitSystem: UnitSystem,
    startTimestamp: Long,
    endTimestamp: Long,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
) {
    if (track.size < 2) return

    val light = LocalIsLightTheme.current
    val traceColor = if (light) CairnGreenDark else CairnGreen
    val arrivalColor = if (light) LightAchievementLabelGold else AchievementLabelGold
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()

    val cumulativeDistances = remember(track) {
        val cumul = DoubleArray(track.size)
        for (i in 1 until track.size) {
            val previous = track[i - 1]
            val current = track[i]
            cumul[i] = cumul[i - 1] + distanceAndBearing(
                previous.latitude, previous.longitude,
                current.latitude, current.longitude,
            ).distanceMeters
        }
        cumul
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
        ) {
            val project = trackProjector(track, marginXFraction = 0.04f, marginYFraction = 0.1f)

            val path = Path()
            track.forEachIndexed { index, point ->
                val offset = project(point)
                if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
            }
            drawPath(
                path = path,
                color = traceColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            drawCircle(color = traceColor, radius = 6.dp.toPx(), center = project(track.first()))
            drawCircle(
                color = CairnAmber,
                radius = 6.dp.toPx(),
                center = project(track.last()),
                style = Stroke(width = 3.dp.toPx()),
            )

            selectedIndex?.let { index -> track.getOrNull(index)?.let { index to it } }?.let { (index, point) ->
                val center = project(point)
                drawCircle(color = surfaceColor, radius = 7.dp.toPx(), center = center)
                drawCircle(color = arrivalColor, radius = 4.5.dp.toPx(), center = center)

                val label = textMeasurer.measure(
                    text = formatShortDistance(cumulativeDistances[index], unitSystem),
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
                // Clears the marker's own radius (7dp) so the bubble doesn't overlap it, flipped
                // to sit below when there isn't enough headroom above.
                val margin = 10.dp.toPx()
                val preferAbove = center.y - bubbleHeight - margin >= 0f
                val rawBubbleY = if (preferAbove) center.y - bubbleHeight - margin else center.y + margin
                val bubbleX = (center.x - bubbleWidth / 2f)
                    .coerceIn(0f, (size.width - bubbleWidth).coerceAtLeast(0f))
                val bubbleY = rawBubbleY.coerceIn(0f, (size.height - bubbleHeight).coerceAtLeast(0f))
                val topLeft = Offset(bubbleX, bubbleY)
                val bubbleSize = Size(bubbleWidth, bubbleHeight)
                val corner = CornerRadius(8.dp.toPx())
                drawRoundRect(color = surfaceColor, topLeft = topLeft, size = bubbleSize, cornerRadius = corner)
                drawRoundRect(
                    color = arrivalColor,
                    topLeft = topLeft,
                    size = bubbleSize,
                    cornerRadius = corner,
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawText(textLayoutResult = label, topLeft = Offset(bubbleX + padH, bubbleY + padV))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.session_departure_fmt, formatTimeOfDay(startTimestamp)),
                fontSize = 11.5.sp,
                color = LabelMuted,
            )
            Text(
                text = stringResource(R.string.session_arrival_fmt, formatTimeOfDay(endTimestamp)),
                fontSize = 11.5.sp,
                color = arrivalColor,
            )
        }
    }
}
