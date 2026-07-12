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
import androidx.compose.material3.CardDefaults
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
import app.matthieu.cairngps.ui.location.formatShortDistance
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGpsTheme
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.CompassDialBorder
import app.matthieu.cairngps.ui.theme.CompassDialFill
import app.matthieu.cairngps.ui.theme.CompassTickMajor
import app.matthieu.cairngps.ui.theme.CompassTickMinor
import app.matthieu.cairngps.ui.theme.DarkBackground
import app.matthieu.cairngps.ui.theme.DarkSurface
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OnGreenButton
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.ValueMuted
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

    val cardinalColor = MaterialTheme.colorScheme.onSurface
    val indexColor = CairnAmber
    val targetColor = CairnGreen
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
                cardinalColor = cardinalColor,
                indexColor = indexColor,
                targetColor = targetColor,
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
                            fontSize = 34.sp,
                            baselineShift = BaselineShift.Superscript,
                        )
                        withStyle(degree.copy(color = Color.Transparent)) { append("°") }
                        append("${uiState.headingDegrees.roundToInt() % 360}")
                        withStyle(degree) { append("°") }
                    },
                    fontSize = 64.sp,
                    fontFamily = MonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = cardinals.getOrElse(uiState.cardinalIndex) { "" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = CairnStone,
                    modifier = Modifier.padding(top = 6.dp),
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.hasTarget) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(icon = Glyph.Flag, contentDescription = null, filled = true, tint = OnGreenButton)
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
        }
    }
    TextButton(onClick = onChangeTarget, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.compass_change_target))
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
 * north. There is no needle — matching the design (1c), the current heading is read from the
 * fixed numeric readout and the fixed amber index at the top; the rose underneath just carries
 * the tick marks, cardinal letters and degree numbers. When a navigation target is set, a green
 * dot on the ring marks its bearing.
 */
private fun DrawScope.drawCompassRose(
    heading: Float,
    targetBearing: Float?,
    roseLabels: Array<String>,
    cardinalColor: Color,
    indexColor: Color,
    targetColor: Color,
    textMeasurer: TextMeasurer,
) {
    // Reserve a margin at the edge for the fixed heading index, so it sits clear of the ticks.
    val indexMargin = 16.dp.toPx()
    val radius = size.minDimension / 2f - indexMargin
    val center = Offset(size.width / 2f, size.height / 2f)

    // Solid puck fill behind the whole dial, with a thin border — matching the design's flat
    // circle rather than a bare stroked ring.
    drawCircle(color = CompassDialFill, radius = radius, center = center)
    drawCircle(
        color = CompassDialBorder,
        radius = radius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )

    rotate(degrees = -heading, pivot = center) {
        // Tick marks every 5°, longer/thicker and paired with a label on the 30° marks.
        for (i in 0 until 72) {
            val deg = i * 5
            val isMajor = i % 6 == 0
            val rad = Math.toRadians(deg.toDouble())
            val sinV = sin(rad).toFloat()
            val cosV = cos(rad).toFloat()
            val outer = radius - 4.dp.toPx()
            val inner = outer - (if (isMajor) 18.dp.toPx() else 10.dp.toPx())
            drawLine(
                color = if (isMajor) CompassTickMajor else CompassTickMinor,
                start = Offset(center.x + inner * sinV, center.y - inner * cosV),
                end = Offset(center.x + outer * sinV, center.y - outer * cosV),
                strokeWidth = (if (isMajor) 2.5f else 1f).dp.toPx(),
            )

            // Cardinal letters at N/E/S/O (roseLabels order); the other 30° marks get their
            // degree number instead, smaller and muted.
            if (isMajor) {
                val cardinalIndex = deg / 90
                val isCardinal = deg % 90 == 0
                val labelRadius = radius - 42.dp.toPx()
                val x = center.x + labelRadius * sinV
                val y = center.y - labelRadius * cosV
                val layout = textMeasurer.measure(
                    text = if (isCardinal) roseLabels[cardinalIndex] else deg.toString(),
                    style = TextStyle(
                        color = if (deg == 0) indexColor else if (isCardinal) cardinalColor else ValueMuted,
                        fontSize = if (isCardinal) 19.sp else 11.sp,
                        fontWeight = if (isCardinal) FontWeight.Bold else FontWeight.Medium,
                    ),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(x - layout.size.width / 2f, y - layout.size.height / 2f),
                )
            }
        }

        // Target bearing marker: a small filled dot on the ring, haloed with the background color
        // for contrast, rotating with it since it's drawn inside the same `rotate` block as the
        // ticks (its screen position still tracks the true/magnetic bearing regardless of which
        // way the device currently points).
        if (targetBearing != null) {
            val rad = Math.toRadians(targetBearing.toDouble())
            val tickRadius = radius - 4.dp.toPx()
            val dotCenter = Offset(
                center.x + tickRadius * sin(rad).toFloat(),
                center.y - tickRadius * cos(rad).toFloat(),
            )
            drawCircle(color = DarkBackground, radius = 8.dp.toPx(), center = dotCenter)
            drawCircle(color = targetColor, radius = 7.dp.toPx(), center = dotCenter)
        }
    }

    // Fixed heading index, drawn last so it stays on top of the ticks. Points down at the ring from
    // the reserved margin.
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
