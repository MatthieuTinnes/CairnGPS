package app.matthieu.cairngps.ui.satellites

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.domain.EcefPosition
import app.matthieu.cairngps.domain.SatelliteGeometry
import app.matthieu.cairngps.ui.theme.DarkOnSurface
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.GlobeLegendBorder
import app.matthieu.cairngps.ui.theme.StatusChipBg
import app.matthieu.cairngps.ui.theme.Sym
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
                GlobeCanvas(
                    observer = observer,
                    satellites = uiState.satellites,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                val present = uiState.satellites
                    .filter { it.info.usedInFix }
                    .map { it.info.constellation }
                    .distinct()
                    .sortedBy { it.ordinal }
                if (present.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        present.forEach { constellation ->
                            GlobeLegendChip(constellation)
                        }
                    }
                }
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

/** Constellation legend chip below the globe (screen 1e). */
@Composable
private fun GlobeLegendChip(constellation: Constellation) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(StatusChipBg)
            .border(width = 1.dp, color = GlobeLegendBorder, shape = RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(constellation.color(), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = constellation.displayName,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = DarkOnSurface,
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
    modifier: Modifier = Modifier,
) {
    // Camera state. Initialized so the observer's position faces the viewer on first composition.
    var yawDeg by rememberSaveable { mutableFloatStateOf(observer.longitude.toFloat()) }
    var pitchDeg by rememberSaveable { mutableFloatStateOf(observer.latitude.toFloat()) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedKey by remember { mutableStateOf<Pair<Constellation, Int>?>(null) }

    val displayed = remember(satellites) { satellites.filter { it.info.usedInFix } }
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

// Projection and DrawScope rendering (GlobeProjection, drawGlobe/drawLandmasses/drawSatellites/
// drawObserverMarker, clipToFrontHemisphere) live in GlobeRendering.kt.
