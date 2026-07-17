package app.matthieu.cairngps.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.format.AccuracyQuality
import app.matthieu.cairngps.domain.format.DASH
import app.matthieu.cairngps.domain.format.accuracyQuality
import app.matthieu.cairngps.domain.format.copyCoordinates
import app.matthieu.cairngps.domain.format.defaultWaypointName
import app.matthieu.cairngps.domain.format.distanceUnitLabel
import app.matthieu.cairngps.domain.format.formatAccuracy
import app.matthieu.cairngps.domain.format.formatAltitude
import app.matthieu.cairngps.domain.format.formatCoordinate
import app.matthieu.cairngps.domain.format.formatDistance
import app.matthieu.cairngps.domain.format.formatDuration
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.formatSpeed
import app.matthieu.cairngps.domain.format.formatSpeedSecondary
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.domain.format.speedSecondaryUnitLabel
import app.matthieu.cairngps.domain.format.speedUnitLabel
import app.matthieu.cairngps.service.RecordingService
import app.matthieu.cairngps.ui.common.BigValue
import app.matthieu.cairngps.ui.common.CardTitle
import app.matthieu.cairngps.ui.common.DataCard
import app.matthieu.cairngps.ui.common.PulsingDot
import app.matthieu.cairngps.ui.recording.RecordingUiState
import app.matthieu.cairngps.ui.recording.RecordingViewModel
import app.matthieu.cairngps.ui.settings.SettingsViewModel
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.DashMuted
import app.matthieu.cairngps.ui.theme.DashText
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.IdleButtonBg
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightOnSaveButton
import app.matthieu.cairngps.ui.theme.LightOnStartButton
import app.matthieu.cairngps.ui.theme.LightRecChipBg
import app.matthieu.cairngps.ui.theme.LightRecChipBorder
import app.matthieu.cairngps.ui.theme.LightRecChipText
import app.matthieu.cairngps.ui.theme.LightSaveButtonBg
import app.matthieu.cairngps.ui.theme.LightStartButtonBg
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OnAmberButton
import app.matthieu.cairngps.ui.theme.OnGreenButton
import app.matthieu.cairngps.ui.theme.QualityGood
import app.matthieu.cairngps.ui.theme.QualityMedium
import app.matthieu.cairngps.ui.theme.QualityPoor
import app.matthieu.cairngps.ui.theme.QualityUnknown
import app.matthieu.cairngps.ui.theme.RecChipBg
import app.matthieu.cairngps.ui.theme.RecChipBorder
import app.matthieu.cairngps.ui.theme.RecChipText
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.ValueMuted

private const val DASH_COORD_DECIMAL = "--.------°"
private const val DASH_COORD_DMS = "--°--'--.-\"-"
private const val DASH_SPEED = "--,-"
private const val DASH_ALTITUDE = "-- --"

private fun coordinateDash(format: CoordinateFormat): String = when (format) {
    CoordinateFormat.DECIMAL -> DASH_COORD_DECIMAL
    CoordinateFormat.DMS -> DASH_COORD_DMS
}

private fun dashAccuracy(unitSystem: UnitSystem): String = "± -- ${shortUnitLabel(unitSystem)}"

/**
 * Screen route. Wires up the [LocationViewModel] and binds GPS tracking to the screen lifecycle:
 * tracking starts in `ON_START` and stops in `ON_STOP`, so the GPS chip is only powered while the
 * screen is actually visible. Only ever composed once the location permission has been granted.
 */
@SuppressLint("MissingPermission")
@Composable
fun HomeRoute(
    locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
    waypointRepository: WaypointRepository,
    recordingRepository: RecordingRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocationViewModel =
        viewModel(
            factory = LocationViewModel.factory(locationRepository, waypointRepository, recordingRepository),
        )
    val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(settingsRepository))
    val recordingViewModel: RecordingViewModel =
        viewModel(factory = RecordingViewModel.factory(recordingRepository))

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val recordingUiState by recordingViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current

    LifecycleStartEffect(Unit) {
        viewModel.startTracking()
        onStopOrDispose { viewModel.stopTracking() }
    }

    // Showing the recording notification needs POST_NOTIFICATIONS at runtime on API 33+. The
    // recording itself works either way, so the service is started regardless of the outcome —
    // only the notification's visibility depends on it.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { RecordingService.start(context) }

    HomeScreen(
        uiState = uiState,
        coordinateFormat = settings.coordinateFormat,
        unitSystem = settings.unitSystem,
        recordingUiState = recordingUiState,
        onSaveWaypoint = viewModel::saveWaypoint,
        onStartRecording = {
            // Recording start/stop is owned by RecordingService, not the ViewModel: only it can
            // keep the recording (and its notification) alive while the app is backgrounded.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                RecordingService.start(context)
            }
        },
        onStopRecording = { RecordingService.stop(context) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: LocationUiState,
    coordinateFormat: CoordinateFormat,
    unitSystem: UnitSystem,
    recordingUiState: RecordingUiState,
    onSaveWaypoint: (String) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }

    if (showSaveDialog) {
        SaveWaypointDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                showSaveDialog = false
                onSaveWaypoint(name)
                Toast.makeText(context, R.string.waypoint_saved, Toast.LENGTH_SHORT).show()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_home)) },
                // Transparent rather than the default surface container: Material3's TopAppBar
                // otherwise draws an opaque surface-colored bar (white in light theme) behind the
                // title, which reads as a stray white block over the page background (design 1o).
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    if (recordingUiState.isRecording) {
                        RecordingBadge(elapsedMs = recordingUiState.elapsedMs)
                        Spacer(Modifier.width(8.dp))
                    }
                },
            )
        },
        bottomBar = {
            BottomActionsRow(
                hasFix = uiState.hasFix,
                isRecording = recordingUiState.isRecording,
                onSaveWaypoint = { showSaveDialog = true },
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            StatusLine(
                hasFix = uiState.hasFix,
                satellitesUsedInFix = uiState.satellitesUsedInFix,
                satellitesVisible = uiState.satellitesVisible,
            )
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CoordinatesCard(
                    uiState = uiState,
                    format = coordinateFormat,
                    onCopy = { copyCoordinates(context, uiState) },
                )

                SpeedCard(uiState = uiState, unitSystem = unitSystem)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AltitudeCard(
                        uiState = uiState,
                        unitSystem = unitSystem,
                        modifier = Modifier.weight(1f),
                    )
                    AccuracyCard(
                        uiState = uiState,
                        unitSystem = unitSystem,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!uiState.hasFix) {
                    NoFixHintCard()
                }

                if (recordingUiState.isRecording) {
                    SessionCard(uiState = recordingUiState, unitSystem = unitSystem)
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/**
 * The two thumb-reach actions fixed at the bottom of the screen: capturing a waypoint (always
 * green) and toggling the recording.
 *
 * The recording button is amber whenever it does something meaningful — stopping an active
 * recording, or starting one while a fix is available — but drops to a neutral fill with muted
 * (CairnStone) icon/text while idle without a fix (design 1b), since starting a recording with no
 * fix wouldn't capture anything yet. In that same no-fix + idle state, the whole row is rendered
 * at reduced opacity (design 1b): "Marquer un repère" is truly disabled (no fix to capture) so it
 * keeps its normal green colors and lets that opacity do the dimming, rather than swapping to a
 * generic disabled grey.
 */
@Composable
private fun BottomActionsRow(
    hasFix: Boolean,
    isRecording: Boolean,
    onSaveWaypoint: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
) {
    val isIdleWithoutFix = !hasFix && !isRecording
    val light = LocalIsLightTheme.current

    // Design 1o's "Marquer un repère" fill/text and "Démarrer" fill are lighter, distinct tones on
    // a white/off-white background — the dark theme's literals (near-black text on dark green,
    // amber start button) don't carry over, so both buttons pick light-theme-specific colors here.
    val saveButtonBg = if (light) LightSaveButtonBg else CairnGreenDark
    val onSaveButton = if (light) LightOnSaveButton else MaterialTheme.colorScheme.onSurface
    val startButtonBg = if (light) LightStartButtonBg else CairnAmber
    val onStartButton = if (light) LightOnStartButton else OnAmberButton

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (isIdleWithoutFix) 0.38f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onSaveWaypoint,
            enabled = hasFix,
            shape = RoundedCornerShape(16.dp),

            contentPadding = PaddingValues(5.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = saveButtonBg,
                contentColor = onSaveButton,
                disabledContainerColor = saveButtonBg,
                disabledContentColor = onSaveButton,
            ),
            modifier = Modifier
                .weight(1f)
                .height(60.dp),
        ) {
            Sym(icon = Glyph.AddLocationAlt, contentDescription = null, tint = if (light) onSaveButton else OnGreenButton)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_save_waypoint))
        }

        Button(
            onClick = if (isRecording) onStopRecording else onStartRecording,
            enabled = isRecording || hasFix,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(5.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isIdleWithoutFix) IdleButtonBg else startButtonBg,
                contentColor = if (isIdleWithoutFix) MaterialTheme.colorScheme.tertiary else onStartButton,
                disabledContainerColor = IdleButtonBg,
                disabledContentColor = MaterialTheme.colorScheme.tertiary,
            ),
            modifier = Modifier
                .weight(1f)
                .height(60.dp),
        ) {
            Sym(
                icon = if (isRecording) Glyph.Stop else Glyph.PlayArrow,
                contentDescription = null,
                filled = true,
                tint = if (isIdleWithoutFix) MaterialTheme.colorScheme.tertiary else onStartButton,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (isRecording) R.string.recording_stop else R.string.recording_start))
        }
    }
}

/** The "SESSION EN COURS" card: a 2×3 grid of live recording stats, shown only while recording. */
@Composable
private fun SessionCard(uiState: RecordingUiState, unitSystem: UnitSystem) {
    DataCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = QualityPoor)
            Spacer(Modifier.width(8.dp))
            CardTitle(stringResource(R.string.recording_title))
        }
        Spacer(Modifier.height(12.dp))

        // Two rows of three rather than three rows of two: it reads as a single glanceable block
        // instead of a list of pairs.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecordingStat(
                label = stringResource(R.string.recording_distance),
                value = "${formatDistance(uiState.distanceMeters, unitSystem)} ${distanceUnitLabel(unitSystem)}",
                modifier = Modifier.weight(1f),
            )
            RecordingStat(
                label = stringResource(R.string.recording_elevation_gain),
                value = "+${formatElevation(uiState.elevationGain, unitSystem)} ${shortUnitLabel(unitSystem)}",
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            RecordingStat(
                label = stringResource(R.string.recording_elevation_loss),
                value = "-${formatElevation(uiState.elevationLoss, unitSystem)} ${shortUnitLabel(unitSystem)}",
                valueColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RecordingStat(
                label = stringResource(R.string.recording_avg_speed),
                value = "${formatSpeed(uiState.averageSpeed, unitSystem)} ${speedUnitLabel(unitSystem)}",
                modifier = Modifier.weight(1f),
            )
            RecordingStat(
                label = stringResource(R.string.recording_max_speed),
                value = "${formatSpeed(uiState.maxSpeed, unitSystem)} ${speedUnitLabel(unitSystem)}",
                modifier = Modifier.weight(1f),
            )
            RecordingStat(
                label = stringResource(R.string.recording_duration),
                value = formatDuration(uiState.elapsedMs),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RecordingStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonoFontFamily,
            color = valueColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = LabelMuted,
        )
    }
}

/** Pill with the live elapsed time, shown in the top bar while recording. */
@Composable
private fun RecordingBadge(elapsedMs: Long) {
    val light = LocalIsLightTheme.current
    val chipBg = if (light) LightRecChipBg else RecChipBg
    val chipBorder = if (light) LightRecChipBorder else RecChipBorder
    val chipText = if (light) LightRecChipText else RecChipText

    Row(
        modifier = Modifier
            .background(chipBg, RoundedCornerShape(16.dp))
            .border(1.dp, chipBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(color = QualityPoor)
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatDuration(elapsedMs),
            style = MaterialTheme.typography.labelLarge,
            fontFamily = MonoFontFamily,
            color = chipText,
        )
    }
}

/** Dialog asking for a waypoint name, pre-filled with a date/time default the user can edit. */
@Composable
private fun SaveWaypointDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val namePrefix = stringResource(R.string.waypoint_default_name_prefix)
    // Snapshot the default name once, at the moment the dialog opens.
    var name by remember { mutableStateOf(defaultWaypointName(namePrefix)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.waypoint_save_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.waypoint_name_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun StatusLine(hasFix: Boolean, satellitesUsedInFix: Int?, satellitesVisible: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Indeterminate acquisition bar — only meaningful while there's no fix yet.
        if (!hasFix) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (hasFix) QualityGood else DashText,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    hasFix && satellitesUsedInFix != null ->
                        stringResource(R.string.fix_obtained_with_satellites, satellitesUsedInFix)
                    hasFix -> stringResource(R.string.fix_obtained)
                    satellitesVisible != null ->
                        stringResource(
                            R.string.waiting_for_fix_with_satellites,
                            satellitesVisible,
                            satellitesUsedInFix ?: 0,
                        )
                    else -> stringResource(R.string.waiting_for_fix)
                },
                style = MaterialTheme.typography.titleSmall,
                // Same muted tone whether or not a fix has been acquired — only the dot's color
                // signals fix status (design 1a/1b). colorScheme.tertiary (CairnStone) is a light
                // grey that's too low-contrast on the light background, so light theme uses its
                // own darker tone from design 1o instead.
                color = if (LocalIsLightTheme.current) LightStatusText else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun CoordinatesCard(
    uiState: LocationUiState,
    format: CoordinateFormat,
    onCopy: () -> Unit,
) {
    val latitude = if (uiState.hasFix) {
        formatCoordinate(uiState.fix?.latitude, isLatitude = true, format = format)
    } else {
        coordinateDash(format)
    }
    val longitude = if (uiState.hasFix) {
        formatCoordinate(uiState.fix?.longitude, isLatitude = false, format = format)
    } else {
        coordinateDash(format)
    }
    val latitudeLabel = stringResource(R.string.label_latitude)
    val longitudeLabel = stringResource(R.string.label_longitude)
    // No fix yet: render the dashes in the design's darker, deliberately "empty" grey rather than
    // the normal onSurface tone (design 1b).
    val valueColor = if (uiState.hasFix) Color.Unspecified else DashText

    // Tapping anywhere on the card copies the coordinates (only meaningful once we have a fix).
    // The design shows the two numbers alone, stacked; the label/value pairing is kept for screen
    // readers via contentDescription rather than as visible text.
    DataCard(onClick = onCopy, enabled = uiState.hasFix) {
        CardTitle(stringResource(R.string.label_coordinates))
        Spacer(Modifier.height(4.dp))
        Text(
            text = latitude,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = MonoFontFamily,
            color = valueColor,
            modifier = Modifier.semantics { contentDescription = "$latitudeLabel $latitude" },
        )
        Text(
            text = longitude,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = MonoFontFamily,
            color = valueColor,
            modifier = Modifier.semantics { contentDescription = "$longitudeLabel $longitude" },
        )
    }
}

@Composable
private fun AltitudeCard(uiState: LocationUiState, unitSystem: UnitSystem, modifier: Modifier = Modifier) {
    DataCard(modifier = modifier) {
        CardTitle(stringResource(R.string.label_altitude))
        Spacer(Modifier.height(8.dp))
        BigValue(
            value = if (uiState.hasFix) formatAltitude(uiState.fix?.altitude, unitSystem) else DASH_ALTITUDE,
            unit = shortUnitLabel(unitSystem),
            valueColor = if (uiState.hasFix) Color.Unspecified else DashMuted,
            unitColor = if (uiState.hasFix) MaterialTheme.colorScheme.tertiary else ValueMuted,
        )
    }
}

/** Full-width VITESSE card: the primary km/h (or mph) reading, with the secondary unit alongside it. */
@Composable
private fun SpeedCard(uiState: LocationUiState, unitSystem: UnitSystem) {
    DataCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(R.string.label_speed))
                Spacer(Modifier.height(4.dp))
                BigValue(
                    value = if (uiState.hasFix) formatSpeed(uiState.fix?.speed, unitSystem) else DASH_SPEED,
                    unit = speedUnitLabel(unitSystem),
                    valueColor = if (uiState.hasFix) Color.Unspecified else DashMuted,
                    unitColor = if (uiState.hasFix) MaterialTheme.colorScheme.tertiary else ValueMuted,
                )
            }
            Text(
                text = if (uiState.hasFix) {
                    "${formatSpeedSecondary(uiState.fix?.speed, unitSystem)} ${speedSecondaryUnitLabel(unitSystem)}"
                } else {
                    "$DASH_SPEED ${speedSecondaryUnitLabel(unitSystem)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = MonoFontFamily,
                color = if (uiState.hasFix) ValueMuted else DashMuted,
            )
        }
    }
}

/** PRÉCISION card: horizontal and vertical accuracy, each with its own quality dot. */
@Composable
private fun AccuracyCard(uiState: LocationUiState, unitSystem: UnitSystem, modifier: Modifier = Modifier) {
    DataCard(modifier = modifier) {
        CardTitle(stringResource(R.string.label_precision))
        Spacer(Modifier.height(10.dp))
        AccuracyRow(
            accuracyMeters = uiState.fix?.horizontalAccuracy,
            letter = stringResource(R.string.label_accuracy_h),
            unitSystem = unitSystem,
        )
        Spacer(Modifier.height(8.dp))
        AccuracyRow(
            accuracyMeters = uiState.fix?.verticalAccuracy,
            letter = stringResource(R.string.label_accuracy_v),
            unitSystem = unitSystem,
        )
    }
}

@Composable
private fun AccuracyRow(accuracyMeters: Float?, letter: String, unitSystem: UnitSystem) {
    val quality = accuracyQuality(accuracyMeters)
    val color = when (quality) {
        AccuracyQuality.GOOD -> QualityGood
        AccuracyQuality.MEDIUM -> QualityMedium
        AccuracyQuality.POOR -> QualityPoor
        AccuracyQuality.UNKNOWN -> QualityUnknown
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color = color, shape = CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (accuracyMeters != null) {
                "±${formatAccuracy(accuracyMeters, unitSystem)} ${shortUnitLabel(unitSystem)}"
            } else {
                dashAccuracy(unitSystem)
            },
            style = MaterialTheme.typography.titleMedium,
            fontFamily = MonoFontFamily,
            color = if (accuracyMeters != null) Color.Unspecified else DashText,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = letter,
            style = MaterialTheme.typography.bodySmall,
            color = if (accuracyMeters != null) ValueMuted else DashMuted,
        )
    }
}

/** Nudge shown only while no fix is available (design 1b), below the data cards. */
@Composable
private fun NoFixHintCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon = Glyph.WbTwilight, contentDescription = null, tint = CairnAmber)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.hint_clear_sky),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

private fun copyCoordinates(
    context: android.content.Context,
    uiState: LocationUiState,
) {
    val fix = uiState.fix ?: return
    context.copyCoordinates(fix.latitude, fix.longitude)
}
