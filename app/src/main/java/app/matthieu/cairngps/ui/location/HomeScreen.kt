package app.matthieu.cairngps.ui.location

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.recording.RecordingUiState
import app.matthieu.cairngps.ui.recording.RecordingViewModel
import app.matthieu.cairngps.ui.settings.SettingsViewModel
import app.matthieu.cairngps.ui.waypoints.defaultWaypointName
import app.matthieu.cairngps.ui.theme.QualityGood
import app.matthieu.cairngps.ui.theme.QualityMedium
import app.matthieu.cairngps.ui.theme.QualityPoor
import app.matthieu.cairngps.ui.theme.QualityUnknown

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
    onOpenSettings: () -> Unit,
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

    LifecycleStartEffect(Unit) {
        viewModel.startTracking()
        onStopOrDispose { viewModel.stopTracking() }
    }

    HomeScreen(
        uiState = uiState,
        coordinateFormat = settings.coordinateFormat,
        recordingUiState = recordingUiState,
        onOpenSettings = onOpenSettings,
        onSaveWaypoint = viewModel::saveWaypoint,
        onStartRecording = { recordingViewModel.start() },
        onStopRecording = { recordingViewModel.stop() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: LocationUiState,
    coordinateFormat: CoordinateFormat,
    recordingUiState: RecordingUiState,
    onOpenSettings: () -> Unit,
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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    val settingsLabel = stringResource(R.string.action_open_settings)
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.semantics { contentDescription = settingsLabel },
                    ) {
                        // Text glyph avoids depending on the large material-icons-extended artifact.
                        Text(
                            text = "⚙",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusLine(hasFix = uiState.hasFix)

            CoordinatesCard(
                uiState = uiState,
                format = coordinateFormat,
                onCopy = { copyCoordinates(context, uiState) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AltitudeCard(
                    uiState = uiState,
                    modifier = Modifier.weight(1f),
                )
                SpeedCard(
                    uiState = uiState,
                    modifier = Modifier.weight(1f),
                )
            }

            AccuracyCard(uiState = uiState)

            Button(
                onClick = { showSaveDialog = true },
                // Nothing meaningful to capture until the first fix arrives.
                enabled = uiState.hasFix,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save_waypoint))
            }

            RecordingCard(
                uiState = recordingUiState,
                onStart = onStartRecording,
                onStop = onStopRecording,
            )
        }
    }
}

@Composable
private fun RecordingCard(
    uiState: RecordingUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    DataCard {
        CardTitle(stringResource(R.string.recording_title))
        Spacer(Modifier.height(12.dp))

        if (uiState.isRecording) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RecordingStat(
                    label = stringResource(R.string.recording_duration),
                    value = formatDuration(uiState.elapsedMs),
                )
                RecordingStat(
                    label = stringResource(R.string.recording_distance),
                    value = "${formatDistanceKm(uiState.distanceMeters)} km",
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RecordingStat(
                    label = stringResource(R.string.recording_avg_speed),
                    value = "${formatSpeedKmh(uiState.averageSpeed)} ${stringResource(R.string.unit_kmh)}",
                )
                RecordingStat(
                    label = stringResource(R.string.recording_max_speed),
                    value = "${formatSpeedKmh(uiState.maxSpeed)} ${stringResource(R.string.unit_kmh)}",
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RecordingStat(
                    label = stringResource(R.string.recording_elevation_gain),
                    value = "+${formatElevation(uiState.elevationGain)} ${stringResource(R.string.unit_meters)}",
                )
                RecordingStat(
                    label = stringResource(R.string.recording_elevation_loss),
                    value = "-${formatElevation(uiState.elevationLoss)} ${stringResource(R.string.unit_meters)}",
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.recording_stop))
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recording_start))
            }
        }
    }
}

@Composable
private fun RecordingStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
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
private fun StatusLine(hasFix: Boolean) {
    Text(
        text = stringResource(if (hasFix) R.string.fix_obtained else R.string.waiting_for_fix),
        style = MaterialTheme.typography.titleSmall,
        color = if (hasFix) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun CoordinatesCard(
    uiState: LocationUiState,
    format: CoordinateFormat,
    onCopy: () -> Unit,
) {
    // Tapping anywhere on the card copies the coordinates (only meaningful once we have a fix).
    DataCard(onClick = onCopy, enabled = uiState.hasFix) {
        CardTitle(stringResource(R.string.label_coordinates))
        Spacer(Modifier.height(12.dp))

        CoordinateRow(
            label = stringResource(R.string.label_latitude),
            value = formatCoordinate(uiState.fix?.latitude, isLatitude = true, format = format),
        )
        Spacer(Modifier.height(8.dp))
        CoordinateRow(
            label = stringResource(R.string.label_longitude),
            value = formatCoordinate(uiState.fix?.longitude, isLatitude = false, format = format),
        )

        if (uiState.hasFix) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.hint_tap_to_copy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CoordinateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun AltitudeCard(uiState: LocationUiState, modifier: Modifier = Modifier) {
    DataCard(modifier = modifier) {
        CardTitle(stringResource(R.string.label_altitude))
        Spacer(Modifier.height(8.dp))
        BigValue(
            value = formatAltitude(uiState.fix?.altitude),
            unit = stringResource(R.string.unit_meters),
        )
    }
}

@Composable
private fun SpeedCard(uiState: LocationUiState, modifier: Modifier = Modifier) {
    DataCard(modifier = modifier) {
        CardTitle(stringResource(R.string.label_speed))
        Spacer(Modifier.height(8.dp))
        BigValue(
            value = formatSpeedKmh(uiState.fix?.speed),
            unit = stringResource(R.string.unit_kmh),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (uiState.hasFix) "${formatSpeedMs(uiState.fix?.speed)} m/s" else DASH,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccuracyCard(uiState: LocationUiState) {
    val accuracy = uiState.fix?.horizontalAccuracy
    val quality = accuracyQuality(accuracy)

    DataCard {
        CardTitle(stringResource(R.string.label_accuracy))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigValue(
                value = if (accuracy != null) "±${formatAccuracy(accuracy)}" else DASH,
                unit = stringResource(R.string.unit_meters),
            )
            Spacer(Modifier.weight(1f))
            QualityIndicator(quality)
        }
    }
}

@Composable
private fun QualityIndicator(quality: AccuracyQuality) {
    val color = when (quality) {
        AccuracyQuality.GOOD -> QualityGood
        AccuracyQuality.MEDIUM -> QualityMedium
        AccuracyQuality.POOR -> QualityPoor
        AccuracyQuality.UNKNOWN -> QualityUnknown
    }
    val labelRes = when (quality) {
        AccuracyQuality.GOOD -> R.string.quality_good
        AccuracyQuality.MEDIUM -> R.string.quality_medium
        AccuracyQuality.POOR -> R.string.quality_poor
        AccuracyQuality.UNKNOWN -> R.string.quality_unknown
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color = color, shape = CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

/**
 * A rounded surface card with consistent padding for a single data group.
 * When [onClick] is provided the whole card becomes tappable (disabled while [enabled] is false).
 */
@Composable
private fun DataCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    if (onClick != null) {
        Card(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A large numeric value with a smaller trailing unit, baseline-aligned. */
@Composable
private fun BigValue(value: String, unit: String, valueColor: Color = Color.Unspecified) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

private fun copyCoordinates(
    context: android.content.Context,
    uiState: LocationUiState,
) {
    val fix = uiState.fix ?: return
    val text = formatCoordinatesForClipboard(fix.latitude, fix.longitude)
    context.getSystemService<ClipboardManager>()
        ?.setPrimaryClip(ClipData.newPlainText("coordinates", text))

    // Android 13+ shows its own "copied" confirmation UI, so only toast on older versions.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.coordinates_copied, Toast.LENGTH_SHORT).show()
    }
}
