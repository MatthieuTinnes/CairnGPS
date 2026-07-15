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
        // A small margin so the line doesn't touch the row's edges.
        val project = trackProjector(track, marginXFraction = 0.05f, marginYFraction = 0.1f)

        val path = Path()
        track.forEachIndexed { index, point ->
            val offset = project(point)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }

        drawPath(
            path = path,
            color = CairnGreen,
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
