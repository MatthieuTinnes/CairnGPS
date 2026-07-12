package app.matthieu.cairngps.ui.satellites

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.domain.EcefPosition
import app.matthieu.cairngps.domain.SatelliteGeometry
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.Sym
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Slow automatic yaw rotation, in degrees per second. */
private const val AUTO_ROTATE_DEG_PER_SECOND = 4f

/** Reuses the same C/N0 full-scale value as the satellites list for size/opacity modulation. */
private const val GLOBE_CN0_FULL_SCALE_DBHZ = 45f

/** Tap hit radius around a satellite marker. */
private val TAP_RADIUS = 28.dp

/**
 * Screen route for the 3D satellite globe. Wires up the [SatelliteGlobeViewModel] and binds the
 * GPS + GNSS subscriptions to the screen lifecycle: started in `ON_START`, stopped in `ON_STOP`.
 * Only ever composed once the location permission has been granted.
 */
@SuppressLint("MissingPermission")
@Composable
fun SatelliteGlobeRoute(
    locationRepository: LocationRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SatelliteGlobeViewModel =
        viewModel(factory = SatelliteGlobeViewModel.factory(locationRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.startTracking()
        onStopOrDispose { viewModel.stopTracking() }
    }

    SatelliteGlobeScreen(
        uiState = uiState,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatelliteGlobeScreen(
    uiState: SatelliteGlobeUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllInView by rememberSaveable { mutableStateOf(false) }
    var autoRotate by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.satellite_globe_title)) },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
            )
        },
    ) { innerPadding ->
        val observer = uiState.observer
        when {
            observer == null -> GlobeWaiting(
                message = stringResource(R.string.waiting_for_fix),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            !uiState.hasGnssData -> GlobeWaiting(
                message = stringResource(R.string.sats_waiting),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = autoRotate,
                        onClick = { autoRotate = !autoRotate },
                        label = { Text(stringResource(R.string.globe_auto_rotate)) },
                    )
                    FilterChip(
                        selected = showAllInView,
                        onClick = { showAllInView = !showAllInView },
                        label = { Text(stringResource(R.string.globe_show_in_view)) },
                    )
                }
                GlobeCanvas(
                    observer = observer,
                    satellites = uiState.satellites,
                    showAllInView = showAllInView,
                    autoRotate = autoRotate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlobeWaiting(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The interactive 3D view: wireframe Earth with its landmass base map, observer marker and
 * satellites, drawn with a manual orthographic projection on a plain Compose [Canvas]
 * (deliberately no OpenGL/Filament dependency, to stay light on battery).
 */
@Composable
private fun GlobeCanvas(
    observer: LocationData,
    satellites: List<GlobeSatellite>,
    showAllInView: Boolean,
    autoRotate: Boolean,
    modifier: Modifier = Modifier,
) {
    // Camera state. Initialized so the observer's position faces the viewer on first composition.
    var yawDeg by rememberSaveable { mutableFloatStateOf(observer.longitude.toFloat()) }
    var pitchDeg by rememberSaveable { mutableFloatStateOf(observer.latitude.toFloat()) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedKey by remember { mutableStateOf<Pair<Constellation, Int>?>(null) }

    val displayed = remember(satellites, showAllInView) {
        if (showAllInView) satellites else satellites.filter { it.info.usedInFix }
    }
    val selected = displayed.firstOrNull { (it.info.constellation to it.info.svid) == selectedKey }

    // Fit the view to the farthest displayed shell (min 2 Earth radii so the globe never
    // fills the whole canvas when nothing is displayed yet).
    val maxRadiusEarthRadii = remember(displayed) {
        ((displayed.maxOfOrNull { it.position.norm } ?: 0.0) / SatelliteGeometry.EARTH_RADIUS_KM)
            .coerceAtLeast(2.0) * 1.08
    }

    val projection = remember(yawDeg, pitchDeg, zoom, canvasSize, maxRadiusEarthRadii) {
        if (canvasSize.width == 0 || canvasSize.height == 0) null
        else GlobeProjection(yawDeg, pitchDeg, zoom, canvasSize, maxRadiusEarthRadii)
    }

    // Graticule geometry is constant; compute the ECEF polylines once.
    val graticule = remember { buildGraticule() }
    // Landmass polygons are parsed from a raw resource; load them off the main thread once.
    val context = LocalContext.current
    val landmasses by produceState(initialValue = emptyList<List<EcefPosition>>(), context) {
        value = withContext(Dispatchers.Default) { WorldLandmasses.load(context) }
    }
    val observerEcef = remember(observer.latitude, observer.longitude) {
        // Sub-point on the ground: the marker sits on the sphere's surface, not at altitude.
        SatelliteGeometry.geodeticToEcef(observer.latitude, observer.longitude, 0.0)
    }

    LaunchedEffect(autoRotate) {
        if (!autoRotate) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                yawDeg = (yawDeg + (now - lastFrameNanos) / 1e9f * AUTO_ROTATE_DEG_PER_SECOND) % 360f
                lastFrameNanos = now
            }
        }
    }

    // Latest values for the tap detector, whose pointerInput(Unit) lambda would otherwise
    // capture stale state.
    val currentProjection by rememberUpdatedState(projection)
    val currentDisplayed by rememberUpdatedState(displayed)

    val colorScheme = MaterialTheme.colorScheme

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        yawDeg = (yawDeg - pan.x * 0.25f).mod(360f)
                        pitchDeg = (pitchDeg + pan.y * 0.25f).coerceIn(-89f, 89f)
                        zoom = (zoom * gestureZoom).coerceIn(0.5f, 8f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val proj = currentProjection ?: return@detectTapGestures
                        val hit = currentDisplayed
                            .map { it to proj.project(it.position) }
                            .filter { (_, point) -> !point.occludedByGlobe }
                            .minByOrNull { (_, point) -> (point.screen - tap).getDistance() }
                            ?.takeIf { (_, point) ->
                                (point.screen - tap).getDistance() <= TAP_RADIUS.toPx()
                            }
                        selectedKey = hit?.first?.info?.let { it.constellation to it.svid }
                    }
                },
        ) {
            val proj = projection ?: return@Canvas
            drawGlobe(proj, graticule, colorScheme.surfaceVariant, colorScheme.onSurfaceVariant)
            drawLandmasses(proj, landmasses, colorScheme.primary)
            drawSatellites(proj, displayed, observerEcef, selectedKey, colorScheme.onSurface)
            drawObserverMarker(proj, observerEcef, colorScheme.primary)
        }

        if (displayed.isEmpty()) {
            Text(
                text = stringResource(R.string.globe_no_fixed_satellites),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }

        val selectedPoint = selected?.let { projection?.project(it.position) }
        if (selected != null && selectedPoint != null && !selectedPoint.occludedByGlobe) {
            SatelliteTooltip(
                satellite = selected,
                modifier = Modifier.offset {
                    IntOffset(
                        x = (selectedPoint.screen.x + 12.dp.toPx()).roundToInt(),
                        y = (selectedPoint.screen.y - 12.dp.toPx()).roundToInt(),
                    )
                },
            )
        }
    }
}

/** Small info bubble shown next to the tapped satellite. */
@Composable
private fun SatelliteTooltip(satellite: GlobeSatellite, modifier: Modifier = Modifier) {
    val info = satellite.info
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "${info.constellation.displayName} %02d".format(info.svid),
                style = MaterialTheme.typography.titleSmall,
                color = info.constellation.color(),
            )
            Text(
                text = stringResource(
                    R.string.sat_elevation_azimuth,
                    info.elevationDegrees.roundToInt(),
                    info.azimuthDegrees.roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "%.0f ".format(info.cn0DbHz) + stringResource(R.string.unit_dbhz),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Projection & drawing
// ---------------------------------------------------------------------------------------------

/**
 * Orthographic camera. The camera looks at the Earth's center; [yawDeg]/[pitchDeg] are the
 * geodetic longitude/latitude of the point facing the viewer, so initializing them with the
 * observer's coordinates centers the view on the observer.
 */
private class GlobeProjection(
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
private data class ProjectedPoint(
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
private fun buildGraticule(): List<List<EcefPosition>> {
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

private fun DrawScope.drawGlobe(
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
private fun DrawScope.drawLandmasses(
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
private fun clipToFrontHemisphere(ring: List<ProjectedPoint>): List<Offset> {
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

private fun DrawScope.drawObserverMarker(
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

private fun DrawScope.drawSatellites(
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
