package app.matthieu.cairngps.ui.compass

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CompassRepository
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.NavigationTargetRepository
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.common.SegmentedToggle
import app.matthieu.cairngps.ui.location.formatShortDistance
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGpsTheme
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.QualityPoor
import app.matthieu.cairngps.ui.theme.Sym
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.draw.rotate as rotateModifier

/**
 * Screen route. Wires up the [CompassViewModel] and binds the sensor subscription to the screen
 * lifecycle: listening starts in `ON_START` and stops in `ON_STOP`, so the magnetometer (and, for
 * the target bearing, the GPS chip) is only powered while the screen is visible. Only ever
 * composed once location permission is granted.
 */
@SuppressLint("MissingPermission")
@Composable
fun CompassRoute(
    compassRepository: CompassRepository,
    locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
    waypointRepository: WaypointRepository,
    navigationTargetRepository: NavigationTargetRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: CompassViewModel = viewModel(
        factory = CompassViewModel.factory(
            compassRepository,
            locationRepository,
            settingsRepository,
            waypointRepository,
            navigationTargetRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.startTracking()
        onStopOrDispose { viewModel.stopTracking() }
    }

    CompassScreen(
        uiState = uiState,
        onNorthReferenceChange = viewModel::setNorthReference,
        onSelectTarget = viewModel::setTarget,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompassScreen(
    uiState: CompassUiState,
    onNorthReferenceChange: (NorthReference) -> Unit,
    onSelectTarget: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTargetPicker by remember { mutableStateOf(false) }

    if (showTargetPicker) {
        TargetPickerDialog(
            waypoints = uiState.waypoints,
            onSelect = { id ->
                showTargetPicker = false
                onSelectTarget(id)
            },
            onDismiss = { showTargetPicker = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.compass_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !uiState.sensorAvailable -> SensorUnavailable()
                else -> {
                    if (uiState.needsCalibration) {
                        CalibrationBanner()
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CompassDial(uiState)
                    }
                    DeclinationInfo(uiState)
                    TargetCard(
                        uiState = uiState,
                        onChangeTarget = { showTargetPicker = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorUnavailable() {
    Text(
        text = stringResource(R.string.compass_sensor_unavailable),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CalibrationBanner() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon = Glyph.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.compass_calibration_needed),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.compass_calibration_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** The rotating compass rose with the current heading, cardinal point and target tick. */
@Composable
private fun CompassDial(uiState: CompassUiState) {
    val roseLabels = stringArrayResource(R.array.compass_rose_labels)
    val cardinals = stringArrayResource(R.array.compass_cardinals)

    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant
    val cardinalColor = MaterialTheme.colorScheme.onSurface
    val northColor = QualityPoor
    val southColor = CairnStone
    val indexColor = CairnAmber
    val targetColor = CairnGreen
    val hubColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 340.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCompassRose(
                heading = uiState.headingDegrees,
                targetBearing = uiState.bearingToTargetDegrees,
                roseLabels = roseLabels,
                tickColor = tickColor,
                cardinalColor = cardinalColor,
                northColor = northColor,
                southColor = southColor,
                indexColor = indexColor,
                targetColor = targetColor,
                hubColor = hubColor,
                textMeasurer = textMeasurer,
            )
        }

        if (uiState.hasData) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    // Monospace keeps the width stable as the value changes. The degree sign is a
                    // small superscript, balanced by an invisible copy on the left so the DIGITS
                    // stay centered (and the cardinal below lines up under the middle digit).
                    text = buildAnnotatedString {
                        val degree = SpanStyle(
                            fontSize = 20.sp,
                            baselineShift = BaselineShift.Superscript,
                        )
                        withStyle(degree.copy(color = Color.Transparent)) { append("°") }
                        append("${uiState.headingDegrees.roundToInt() % 360}")
                        withStyle(degree) { append("°") }
                    },
                    fontSize = 38.sp,
                    fontFamily = MonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = cardinals.getOrElse(uiState.cardinalIndex) { "" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun DeclinationInfo(uiState: CompassUiState) {
    val roseLabels = stringArrayResource(R.array.compass_rose_labels)
    val declination = uiState.declinationDegrees

    val text = if (declination == null) {
        stringResource(R.string.compass_true_north_unavailable)
    } else {
        // roseLabels = [N, E, S, O]; declination is positive toward the east.
        val direction = if (declination >= 0f) roseLabels[1] else roseLabels[3]
        stringResource(
            R.string.compass_declination,
            "%.1f".format(kotlin.math.abs(declination)),
            direction,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * The target card (screen 1c): shows the selected waypoint's name, bearing and distance with an
 * arrow glyph rotated to point at it, or an empty-state inviting the user to pick one.
 */
@Composable
private fun TargetCard(uiState: CompassUiState, onChangeTarget: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.hasTarget) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(icon = Glyph.Flag, contentDescription = null, filled = true, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = uiState.targetName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        val bearing = uiState.bearingToTargetDegrees
                        if (bearing != null) {
                            val refLabel = stringResource(
                                if (uiState.useTrueNorth) {
                                    R.string.compass_target_ref_true
                                } else {
                                    R.string.compass_target_ref_magnetic
                                },
                            )
                            Text(
                                text = stringResource(
                                    R.string.compass_target_bearing_fmt,
                                    bearing.roundToInt(),
                                    refLabel,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val relative = uiState.relativeBearingDegrees
                        Sym(
                            icon = Glyph.Navigation,
                            contentDescription = null,
                            filled = true,
                            tint = CairnGreen,
                            modifier = if (relative != null) {
                                Modifier.rotateGlyph(relative)
                            } else {
                                Modifier
                            },
                        )
                        val distance = uiState.targetDistanceMeters
                        if (distance != null) {
                            Text(
                                text = formatShortDistance(distance),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = MonoFontFamily,
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.compass_no_target_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onChangeTarget, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.compass_change_target))
            }
        }
    }
}

/** Rotates a glyph in place around its own center by [degrees]. */
private fun Modifier.rotateGlyph(degrees: Float): Modifier = this.rotateModifier(degrees)

/** Dialog listing every saved waypoint so the user can pick a new navigation target. */
@Composable
private fun TargetPickerDialog(
    waypoints: List<Waypoint>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compass_select_target_title)) },
        text = {
            if (waypoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.compass_no_waypoints),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(items = waypoints, key = { it.id }) { waypoint ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(waypoint.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Sym(icon = Glyph.Flag, contentDescription = null, filled = true, tint = CairnGreen)
                            Spacer(Modifier.width(12.dp))
                            Text(text = waypoint.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Draws the compass rose rotated by `-heading` so its north mark points to real (magnetic or true)
 * north, with the needle's red half pointing north. A fixed index at the top marks the heading,
 * and — when a navigation target is set — an amber tick on the ring marks its bearing.
 */
private fun DrawScope.drawCompassRose(
    heading: Float,
    targetBearing: Float?,
    roseLabels: Array<String>,
    tickColor: Color,
    cardinalColor: Color,
    northColor: Color,
    southColor: Color,
    indexColor: Color,
    targetColor: Color,
    hubColor: Color,
    textMeasurer: TextMeasurer,
) {
    // Reserve a margin at the edge for the fixed heading index, so it sits clear of the ticks.
    val indexMargin = 16.dp.toPx()
    val radius = size.minDimension / 2f - indexMargin
    val center = Offset(size.width / 2f, size.height / 2f)

    // Outer ring.
    drawCircle(
        color = tickColor.copy(alpha = 0.25f),
        radius = radius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
    )

    rotate(degrees = -heading, pivot = center) {
        // Tick marks every 15°, longer and thicker on the 45° marks.
        for (deg in 0 until 360 step 15) {
            val isMajor = deg % 45 == 0
            val rad = Math.toRadians(deg.toDouble())
            val sinV = sin(rad).toFloat()
            val cosV = cos(rad).toFloat()
            val outer = radius - 4.dp.toPx()
            val inner = outer - (if (isMajor) 18.dp.toPx() else 9.dp.toPx())
            drawLine(
                color = tickColor,
                start = Offset(center.x + inner * sinV, center.y - inner * cosV),
                end = Offset(center.x + outer * sinV, center.y - outer * cosV),
                strokeWidth = (if (isMajor) 3f else 1.5f).dp.toPx(),
            )
        }

        // Cardinal letters at N/E/S/O (roseLabels order), N highlighted like the needle.
        roseLabels.forEachIndexed { i, label ->
            val rad = Math.toRadians(i * 90.0)
            val labelRadius = radius - 42.dp.toPx()
            val x = center.x + labelRadius * sin(rad).toFloat()
            val y = center.y - labelRadius * cos(rad).toFloat()
            val layout = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    color = if (i == 0) northColor else cardinalColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
            )
        }

        // The two-tone needle: red half toward north, stone half toward south.
        val needleLength = radius - 60.dp.toPx()
        val halfWidth = 9.dp.toPx()
        drawPath(
            Path().apply {
                moveTo(center.x, center.y - needleLength)
                lineTo(center.x - halfWidth, center.y)
                lineTo(center.x + halfWidth, center.y)
                close()
            },
            color = northColor,
        )
        drawPath(
            Path().apply {
                moveTo(center.x, center.y + needleLength)
                lineTo(center.x - halfWidth, center.y)
                lineTo(center.x + halfWidth, center.y)
                close()
            },
            color = southColor,
        )

        // Target bearing tick: a small filled dot on the ring, rotating with it since it's drawn
        // inside the same `rotate` block as the ticks/needle (its screen position still tracks the
        // true/magnetic bearing regardless of which way the device currently points).
        if (targetBearing != null) {
            val rad = Math.toRadians(targetBearing.toDouble())
            val tickRadius = radius - 4.dp.toPx()
            drawCircle(
                color = targetColor,
                radius = 7.dp.toPx(),
                center = Offset(
                    center.x + tickRadius * sin(rad).toFloat(),
                    center.y - tickRadius * cos(rad).toFloat(),
                ),
            )
        }
    }

    // Solid hub behind the center readout so the heading and cardinal stay legible over the needle.
    val hubRadius = 58.dp.toPx()
    drawCircle(color = hubColor, radius = hubRadius, center = center)
    drawCircle(
        color = tickColor.copy(alpha = 0.3f),
        radius = hubRadius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )

    // Fixed heading index, drawn last so it stays on top of the ticks. Points down at the ring from
    // the reserved margin; amber to read clearly and stand apart from the red north needle.
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

@Preview(showBackground = true)
@Composable
private fun CompassScreenPreview() {
    CairnGpsTheme {
        CompassScreen(
            uiState = CompassUiState(
                sensorAvailable = true,
                hasData = true,
                headingDegrees = 42f,
                cardinalIndex = 1,
                useTrueNorth = false,
                declinationDegrees = 1.5f,
                needsCalibration = true,
                targetName = "Lac Blanc",
                targetDistanceMeters = 1240.0,
                bearingToTargetDegrees = 312f,
            ),
            onNorthReferenceChange = {},
            onSelectTarget = {},
        )
    }
}
