package app.matthieu.cairngps.ui.history

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import app.matthieu.cairngps.data.TrackPoint

/**
 * Builds a projector from a [TrackPoint]'s lat/lon into this [DrawScope]'s canvas coordinates,
 * flattening the track's bounding box to fill the canvas (minus [marginXFraction]/
 * [marginYFraction] of the width/height on each side) while ignoring true aspect ratio — a
 * decorative shape, not a map projection. Shared by [RouteSparkline] and [SessionRouteTrace].
 */
fun DrawScope.trackProjector(
    track: List<TrackPoint>,
    marginXFraction: Float,
    marginYFraction: Float,
): (TrackPoint) -> Offset {
    val lats = track.map { it.latitude }
    val lons = track.map { it.longitude }
    val latSpan = (lats.max() - lats.min()).takeIf { it > 0.0 } ?: 1.0
    val lonSpan = (lons.max() - lons.min()).takeIf { it > 0.0 } ?: 1.0
    val minLat = lats.min()
    val minLon = lons.min()

    val marginX = size.width * marginXFraction
    val marginY = size.height * marginYFraction
    val drawWidth = size.width - marginX * 2
    val drawHeight = size.height - marginY * 2

    return { point ->
        val x = marginX + ((point.longitude - minLon) / lonSpan).toFloat() * drawWidth
        // Inverted: higher latitude (north) draws higher on screen.
        val y = marginY + (1f - ((point.latitude - minLat) / latSpan).toFloat()) * drawHeight
        Offset(x, y)
    }
}
