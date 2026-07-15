package app.matthieu.cairngps.ui.satellites

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.domain.EcefPosition
import app.matthieu.cairngps.domain.SatelliteGeometry
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure geometry and drawing for the 3D satellite globe (screen 1n): the manual orthographic
 * projection and the [DrawScope] renderers that consume it. Kept separate from
 * [SatelliteGlobeScreen]'s [GlobeCanvas], which owns the interactive/stateful side (gestures,
 * camera state, tap hit-testing) and is the only caller of this file.
 */

internal const val GLOBE_CN0_FULL_SCALE_DBHZ = 45f

/**
 * Orthographic camera. The camera looks at the Earth's center; [yawDeg]/[pitchDeg] are the
 * geodetic longitude/latitude of the point facing the viewer, so initializing them with the
 * observer's coordinates centers the view on the observer.
 */
internal class GlobeProjection(
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    size: IntSize,
    maxRadiusEarthRadii: Double,
) {
    val center = Offset(size.width / 2f, size.height / 2f)

    /** Screen pixels per Earth radius; the globe's screen radius equals this value. */
    val pxPerEarthRadius: Float =
        min(size.width, size.height) / 2f * 0.94f / maxRadiusEarthRadii.toFloat() * zoom

    private val cosYaw = cos(Math.toRadians(yawDeg.toDouble()))
    private val sinYaw = sin(Math.toRadians(yawDeg.toDouble()))
    private val cosPitch = cos(Math.toRadians(pitchDeg.toDouble()))
    private val sinPitch = sin(Math.toRadians(pitchDeg.toDouble()))

    fun project(position: EcefPosition): ProjectedPoint {
        val x = position.x / SatelliteGeometry.EARTH_RADIUS_KM
        val y = position.y / SatelliteGeometry.EARTH_RADIUS_KM
        val z = position.z / SatelliteGeometry.EARTH_RADIUS_KM
        // Yaw about the polar (ECEF Z) axis, then pitch about the screen-horizontal axis.
        val x1 = x * cosYaw + y * sinYaw
        val right = -x * sinYaw + y * cosYaw
        val depth = x1 * cosPitch + z * sinPitch
        val up = -x1 * sinPitch + z * cosPitch
        return ProjectedPoint(
            screen = Offset(
                x = center.x + (right * pxPerEarthRadius).toFloat(),
                y = center.y - (up * pxPerEarthRadius).toFloat(),
            ),
            depth = depth.toFloat(),
            radialDistanceSq = (right * right + up * up).toFloat(),
        )
    }
}

/**
 * @property depth            Signed distance toward the viewer, in Earth radii (> 0 = front side).
 * @property radialDistanceSq Squared distance from the view axis, in Earth radii — with an
 *                            orthographic projection a point is hidden by the globe exactly when
 *                            it is on the back side and inside the unit disk.
 */
internal data class ProjectedPoint(
    val screen: Offset,
    val depth: Float,
    val radialDistanceSq: Float,
) {
    val occludedByGlobe: Boolean get() = depth < 0f && radialDistanceSq < 1f
}

/**
 * Graticule polylines in ECEF (km): parallels every 30° between ±60° plus the equator, and
 * meridians every 30°, each sampled every 10°.
 */
internal fun buildGraticule(): List<List<EcefPosition>> {
    val polylines = mutableListOf<List<EcefPosition>>()
    for (lat in -60..60 step 30) {
        polylines += (0..360 step 10).map { lon ->
            SatelliteGeometry.geodeticToEcef(lat.toDouble(), lon.toDouble(), 0.0)
        }
    }
    for (lon in 0 until 360 step 30) {
        polylines += (-90..90 step 10).map { lat ->
            SatelliteGeometry.geodeticToEcef(lat.toDouble(), lon.toDouble(), 0.0)
        }
    }
    return polylines
}

internal fun DrawScope.drawGlobe(
    projection: GlobeProjection,
    graticule: List<List<EcefPosition>>,
    surfaceColor: Color,
    lineColor: Color,
) {
    val radius = projection.pxPerEarthRadius
    // Light shading offset toward the upper-left to suggest volume.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(surfaceColor.copy(alpha = 0.45f), surfaceColor.copy(alpha = 0.08f)),
            center = projection.center + Offset(-radius * 0.35f, -radius * 0.35f),
            radius = radius * 1.8f,
        ),
        radius = radius,
        center = projection.center,
    )
    // Limb (silhouette) circle.
    drawCircle(
        color = lineColor.copy(alpha = 0.55f),
        radius = radius,
        center = projection.center,
        style = Stroke(width = 1.dp.toPx()),
    )
    val strokeWidth = 1.dp.toPx()
    graticule.forEach { polyline ->
        var previous = projection.project(polyline.first())
        for (i in 1 until polyline.size) {
            val current = projection.project(polyline[i])
            // Front segments are visible; back segments are drawn very faint as a depth cue.
            // Segments crossing the limb are skipped rather than clipped — invisible at this size.
            val alpha = when {
                previous.depth >= 0f && current.depth >= 0f -> 0.30f
                previous.depth < 0f && current.depth < 0f -> 0.07f
                else -> 0f
            }
            if (alpha > 0f) {
                drawLine(
                    color = lineColor.copy(alpha = alpha),
                    start = previous.screen,
                    end = current.screen,
                    strokeWidth = strokeWidth,
                )
            }
            previous = current
        }
    }
}

/**
 * Base map: landmasses filled with a subtle tint and outlined by their coastline. Only the front
 * hemisphere is drawn; polygons are clipped in place against the `depth = 0` plane
 * (Sutherland–Hodgman). The projection is orthographic, hence linear, so interpolating screen
 * positions at the plane crossing is exact and clipped vertices land right on the limb — the
 * only artifact is a straight chord (instead of an arc) between two consecutive crossings, which
 * is invisible at the fill's low opacity.
 */
internal fun DrawScope.drawLandmasses(
    projection: GlobeProjection,
    landmasses: List<List<EcefPosition>>,
    landColor: Color,
) {
    val fillColor = landColor.copy(alpha = 0.16f)
    val outlineColor = landColor.copy(alpha = 0.50f)
    val outlineWidth = 1.dp.toPx()
    landmasses.forEach { polygon ->
        val projected = polygon.map { projection.project(it) }
        val clipped = clipToFrontHemisphere(projected)
        if (clipped.size >= 3) {
            val path = Path().apply {
                moveTo(clipped[0].x, clipped[0].y)
                for (i in 1 until clipped.size) lineTo(clipped[i].x, clipped[i].y)
                close()
            }
            drawPath(path = path, color = fillColor)
        }
        // Coastline: front-facing segments only (rings are closed, so consecutive pairs cover
        // the whole outline).
        for (i in 1 until projected.size) {
            val previous = projected[i - 1]
            val current = projected[i]
            if (previous.depth >= 0f && current.depth >= 0f) {
                drawLine(
                    color = outlineColor,
                    start = previous.screen,
                    end = current.screen,
                    strokeWidth = outlineWidth,
                )
            }
        }
    }
}

/** Sutherland–Hodgman clip of a closed ring against the front half-space `depth >= 0`. */
internal fun clipToFrontHemisphere(ring: List<ProjectedPoint>): List<Offset> {
    if (ring.isEmpty()) return emptyList()
    val result = mutableListOf<Offset>()
    var previous = ring.last()
    ring.forEach { current ->
        val currentFront = current.depth >= 0f
        val previousFront = previous.depth >= 0f
        if (currentFront) {
            if (!previousFront) result += limbCrossing(previous, current)
            result += current.screen
        } else if (previousFront) {
            result += limbCrossing(previous, current)
        }
        previous = current
    }
    return result
}

/** Screen position where the segment [a]–[b] crosses the `depth = 0` plane. */
private fun limbCrossing(a: ProjectedPoint, b: ProjectedPoint): Offset {
    val t = a.depth / (a.depth - b.depth)
    return Offset(
        x = a.screen.x + (b.screen.x - a.screen.x) * t,
        y = a.screen.y + (b.screen.y - a.screen.y) * t,
    )
}

internal fun DrawScope.drawObserverMarker(
    projection: GlobeProjection,
    observerEcef: EcefPosition,
    color: Color,
) {
    val point = projection.project(observerEcef)
    if (point.depth < 0f) return // On the far side of the globe.
    drawCircle(color = color, radius = 4.dp.toPx(), center = point.screen)
    drawCircle(
        color = color.copy(alpha = 0.5f),
        radius = 8.dp.toPx(),
        center = point.screen,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

internal fun DrawScope.drawSatellites(
    projection: GlobeProjection,
    satellites: List<GlobeSatellite>,
    observerEcef: EcefPosition,
    selectedKey: Pair<Constellation, Int>?,
    selectionColor: Color,
) {
    val observerPoint = projection.project(observerEcef)
    satellites.forEach { satellite ->
        val point = projection.project(satellite.position)
        if (point.occludedByGlobe) return@forEach

        val info = satellite.info
        val cn0Fraction = (info.cn0DbHz / GLOBE_CN0_FULL_SCALE_DBHZ).coerceIn(0f, 1f)
        // Strong signals are bigger and more opaque; satellites tracked but not used in the fix
        // are dimmed, and everything on the far side of the globe is attenuated as a depth cue.
        var alpha = 0.45f + 0.55f * cn0Fraction
        if (!info.usedInFix) alpha *= 0.4f
        if (point.depth < 0f) alpha *= 0.5f
        val markerRadius = (2.5f + 3.5f * cn0Fraction).dp.toPx()
        val color = info.constellation.color()

        // Line of sight, only when the observer marker itself is visible.
        if (observerPoint.depth >= 0f) {
            drawLine(
                color = color.copy(alpha = alpha * 0.35f),
                start = observerPoint.screen,
                end = point.screen,
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = markerRadius,
            center = point.screen,
        )
        if ((info.constellation to info.svid) == selectedKey) {
            drawCircle(
                color = selectionColor,
                radius = markerRadius + 5.dp.toPx(),
                center = point.screen,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}
