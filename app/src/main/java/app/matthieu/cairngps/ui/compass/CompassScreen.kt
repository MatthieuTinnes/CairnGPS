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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.format.defaultWaypointName
import app.matthieu.cairngps.domain.format.formatShortDistance
import app.matthieu.cairngps.domain.format.formatWaypointMetaLine
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGpsTheme
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.CompassDialBorder
import app.matthieu.cairngps.ui.theme.CompassDialBorderLight
import app.matthieu.cairngps.ui.theme.CompassDialFill
import app.matthieu.cairngps.ui.theme.CompassDialFillLight
import app.matthieu.cairngps.ui.theme.CompassTickMajor
import app.matthieu.cairngps.ui.theme.CompassTickMajorLight
import app.matthieu.cairngps.ui.theme.CompassTickMinor
import app.matthieu.cairngps.ui.theme.CompassTickMinorLight
import app.matthieu.cairngps.ui.theme.DarkBackground
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightBorderSubtle
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LightWaypointIconBg
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OnGreenButton
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.WaypointIconBg
import app.matthieu.cairngps.ui.waypoints.RenameDialog
import app.matthieu.cairngps.ui.waypoints.WaypointIcons
import kotlin.math.roundToInt
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
    recordingRepository: RecordingRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: CompassViewModel = viewModel(
        factory = CompassViewModel.factory(
            compassRepository,
            locationRepository,
            settingsRepository,
            waypointRepository,
            navigationTargetRepository,
            recordingRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.startTracking()
        onStopOrDispose { viewModel.stopTracking() }
    }

    CompassScreen(
        uiState = uiState,
        onSelectTarget = viewModel::setTarget,
        onCreateTargetHere = viewModel::createWaypointHere,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompassScreen(
    uiState: CompassUiState,
    onSelectTarget: (Long) -> Unit,
    onCreateTargetHere: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTargetPicker by remember { mutableStateOf(false) }

    if (showTargetPicker) {
        TargetPickerSheet(
            waypoints = uiState.waypoints,
            targetWaypointId = uiState.targetWaypointId,
            unitSystem = uiState.unitSystem,
            onSelect = { id ->
                showTargetPicker = false
                onSelectTarget(id)
            },
            onCreateHere = onCreateTargetHere,
            onDismiss = { showTargetPicker = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compass_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
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

    val light = LocalIsLightTheme.current
    val cardinalColor = MaterialTheme.colorScheme.onSurface
    val indexColor = CairnAmber
    val targetColor = if (light) CairnGreenDark else CairnGreen
    val dialFill = if (light) CompassDialFillLight else CompassDialFill
    val dialBorder = if (light) CompassDialBorderLight else CompassDialBorder
    val tickMajor = if (light) CompassTickMajorLight else CompassTickMajor
    val tickMinor = if (light) CompassTickMinorLight else CompassTickMinor
    val dotHalo = if (light) CompassDialFillLight else DarkBackground
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
                dialFill = dialFill,
                dialBorder = dialBorder,
                tickMajor = tickMajor,
                tickMinor = tickMinor,
                dotHalo = dotHalo,
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
                    // CairnStone is too low-contrast on the light background (design 5b).
                    color = if (light) LightStatusText else CairnStone,
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
    val light = LocalIsLightTheme.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.hasTarget) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (light) CairnGreenDark else CairnGreen, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(
                            icon = WaypointIcons.glyphFor(uiState.targetIcon ?: "flag"),
                            contentDescription = null,
                            filled = true,
                            tint = OnGreenButton,
                        )
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
                            tint = if (light) CairnGreenDark else CairnGreen,
                            modifier = if (relative != null) {
                                Modifier.rotateGlyph(relative)
                            } else {
                                Modifier
                            },
                        )
                        val distance = uiState.targetDistanceMeters
                        if (distance != null) {
                            Text(
                                text = formatShortDistance(distance, uiState.unitSystem),
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

/**
 * Bottom sheet listing every saved waypoint so the user can pick a new navigation target (screens
 * 3c/5q), plus a footer row to create a waypoint at the current position and target it immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetPickerSheet(
    waypoints: List<Waypoint>,
    targetWaypointId: Long?,
    unitSystem: UnitSystem,
    onSelect: (Long) -> Unit,
    onCreateHere: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val light = LocalIsLightTheme.current
    var showCreateDialog by remember { mutableStateOf(false) }

    // Naming step for "Créer un nouveau repère ici": reuses the shared rename/name dialog rather
    // than duplicating an OutlinedTextField dialog here.
    if (showCreateDialog) {
        val namePrefix = stringResource(R.string.waypoint_default_name_prefix)
        RenameDialog(
            title = stringResource(R.string.waypoint_save_dialog_title),
            label = stringResource(R.string.waypoint_name_label),
            initialName = defaultWaypointName(namePrefix),
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreateHere(name)
                onDismiss()
            },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = stringResource(R.string.compass_select_target_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            if (waypoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.compass_no_waypoints),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items = waypoints, key = { it.id }) { waypoint ->
                        TargetPickerRow(
                            waypoint = waypoint,
                            selected = waypoint.id == targetWaypointId,
                            unitSystem = unitSystem,
                            onClick = { onSelect(waypoint.id) },
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(1.dp)
                    .background(if (light) LightBorderSubtle else CompassDialBorder),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCreateDialog = true }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(if (light) LightWaypointIconBg else WaypointIconBg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(
                        icon = Glyph.AddLocationAlt,
                        contentDescription = null,
                        tint = if (light) LightStatusText else CairnStone,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.compass_create_target_here),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One waypoint row in [TargetPickerSheet]: icon circle, name, muted metadata subtitle. */
@Composable
private fun TargetPickerRow(waypoint: Waypoint, selected: Boolean, unitSystem: UnitSystem, onClick: () -> Unit) {
    val light = LocalIsLightTheme.current
    val accentColor = if (light) CairnGreenDark else CairnGreen
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (light) LightWaypointIconBg else WaypointIconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Sym(icon = WaypointIcons.glyphFor(waypoint.icon), contentDescription = null, filled = true, tint = accentColor, size = 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = waypoint.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = formatWaypointMetaLine(waypoint, unitSystem),
                fontSize = 12.5.sp,
                fontFamily = MonoFontFamily,
                color = LabelMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
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
            onSelectTarget = {},
            onCreateTargetHere = {},
        )
    }
}
