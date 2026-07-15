package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.ValueMuted

/**
 * Elevation-over-time area chart (screen 1j "PROFIL D'ALTITUDE"): a filled curve scaled between
 * the track's min and max altitude, with those two values labelled below. `track` must be
 * chronologically ordered and non-empty — callers should only show this card when there's data
 * (older sessions recorded before the track-points feature existed have none).
 */
@Composable
fun AltitudeProfile(track: List<TrackPoint>, modifier: Modifier = Modifier) {
    if (track.isEmpty()) return

    val minAltitude = track.minOf { it.altitude }
    val maxAltitude = track.maxOf { it.altitude }
    val lineColor = CairnGreen
    val fillColor = lineColor.copy(alpha = 0.18f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
        ) {
            val range = (maxAltitude - minAltitude).takeIf { it > 0.0 } ?: 1.0
            // Normalized (0..1) screen-space points: x by index (evenly spaced — samples are
            // roughly evenly spaced in time, close enough for a profile shape at this scale),
            // y inverted since higher altitude should draw higher on screen.
            val points = track.mapIndexed { index, point ->
                val x = if (track.size > 1) index.toFloat() / (track.size - 1) * size.width else 0f
                val fraction = ((point.altitude - minAltitude) / range).toFloat()
                val y = size.height * (1f - fraction)
                Offset(x, y)
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
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${formatElevation(minAltitude)} m",
                fontSize = 11.sp,
                fontFamily = MonoFontFamily,
                color = ValueMuted,
            )
            Text(
                text = "max ${formatElevation(maxAltitude)} m",
                fontSize = 11.sp,
                fontFamily = MonoFontFamily,
                color = ValueMuted,
            )
        }
    }
}
