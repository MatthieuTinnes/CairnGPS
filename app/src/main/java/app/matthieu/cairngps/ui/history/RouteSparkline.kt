package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.ui.theme.CairnGreen

/**
 * Tiny route-shape preview for a session row (screen 1p): the track's lat/lon flattened into the
 * row's small bounds, ignoring true aspect ratio — a decorative shape, not a map. `track` must be
 * non-empty for anything to be drawn; callers should skip this composable otherwise.
 */
@Composable
fun RouteSparkline(track: List<TrackPoint>, modifier: Modifier = Modifier) {
    if (track.size < 2) return

    Canvas(modifier = modifier) {
        val lats = track.map { it.latitude }
        val lons = track.map { it.longitude }
        val latSpan = (lats.max() - lats.min()).takeIf { it > 0.0 } ?: 1.0
        val lonSpan = (lons.max() - lons.min()).takeIf { it > 0.0 } ?: 1.0
        val minLat = lats.min()
        val minLon = lons.min()

        // A small margin so the line doesn't touch the row's edges.
        val marginX = size.width * 0.05f
        val marginY = size.height * 0.1f
        val drawWidth = size.width - marginX * 2
        val drawHeight = size.height - marginY * 2

        val path = Path()
        track.forEachIndexed { index, point ->
            val x = marginX + ((point.longitude - minLon) / lonSpan).toFloat() * drawWidth
            // Inverted: higher latitude (north) should draw higher on screen.
            val y = marginY + (1f - ((point.latitude - minLat) / latSpan).toFloat()) * drawHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = CairnGreen,
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
