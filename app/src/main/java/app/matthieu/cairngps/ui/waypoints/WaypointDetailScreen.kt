package app.matthieu.cairngps.ui.waypoints

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.annotation.SuppressLint
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.location.DASH
import app.matthieu.cairngps.ui.location.formatAccuracy
import app.matthieu.cairngps.ui.location.formatAltitude
import app.matthieu.cairngps.ui.location.formatCoordinate
import app.matthieu.cairngps.ui.location.formatCoordinatesForClipboard
import app.matthieu.cairngps.ui.location.formatShortDistance
import app.matthieu.cairngps.ui.location.formatSpeedKmh
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym
import java.util.Locale
import androidx.core.net.toUri

/**
 * Route: loads the waypoint identified by [waypointId] and renders its full detail. Navigates back
 * automatically once the waypoint has been deleted. Only ever composed once location permission
 * has been granted (behind [app.matthieu.cairngps.ui.permission.LocationPermissionGate]).
 */
@SuppressLint("MissingPermission")
@Composable
fun WaypointDetailRoute(
    waypointId: Long,
    waypointRepository: WaypointRepository,
    sessionRepository: SessionRepository,
    locationRepository: LocationRepository,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onNavigate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WaypointDetailViewModel =
        viewModel(
            factory = WaypointDetailViewModel.factory(
                waypointRepository,
                sessionRepository,
                locationRepository,
                waypointId,
            ),
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Once the delete completes, leave the detail screen (side-effect, not done during composition).
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    // One-shot "distance actuelle" snapshot, taken once the waypoint has finished loading (keyed on
    // its id so it doesn't re-run on every recomposition, e.g. after a rename).
    LaunchedEffect(uiState.waypoint?.id) {
        if (uiState.waypoint != null) viewModel.refreshCurrentDistance()
    }

    WaypointDetailScreen(
        waypoint = uiState.waypoint,
        session = uiState.session,
        currentDistanceMeters = uiState.currentDistanceMeters,
        onBack = onBack,
        onDelete = viewModel::delete,
        onRename = viewModel::rename,
        onOpenSession = onOpenSession,
        onNavigate = { onNavigate(waypointId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaypointDetailScreen(
    waypoint: Waypoint?,
    session: Session?,
    currentDistanceMeters: Double?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }

    if (showRenameDialog && waypoint != null) {
        RenameDialog(
            title = stringResource(R.string.waypoint_rename_dialog_title),
            label = stringResource(R.string.waypoint_name_label),
            initialName = waypoint.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                showRenameDialog = false
                onRename(newName)
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(waypoint?.name ?: stringResource(R.string.waypoint_detail_title)) },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
                actions = {
                    if (waypoint != null) {
                        val renameLabel = stringResource(R.string.action_rename)
                        IconButton(onClick = { showRenameDialog = true }) {
                            Sym(icon = Glyph.Edit, contentDescription = renameLabel)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // waypoint stays null only for the brief load; nothing to render until it resolves.
        if (waypoint == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(stringResource(R.string.label_coordinates)) {
                InfoRow(
                    stringResource(R.string.label_latitude),
                    formatCoordinate(waypoint.latitude, isLatitude = true, format = CoordinateFormat.DECIMAL),
                )
                InfoRow(
                    stringResource(R.string.label_longitude),
                    formatCoordinate(waypoint.longitude, isLatitude = false, format = CoordinateFormat.DECIMAL),
                )
                Spacer(Modifier.height(4.dp))
                InfoRow(
                    stringResource(R.string.label_latitude),
                    formatCoordinate(waypoint.latitude, isLatitude = true, format = CoordinateFormat.DMS),
                )
                InfoRow(
                    stringResource(R.string.label_longitude),
                    formatCoordinate(waypoint.longitude, isLatitude = false, format = CoordinateFormat.DMS),
                )
            }

            InfoCard(stringResource(R.string.label_measurements)) {
                InfoRow(
                    stringResource(R.string.label_altitude),
                    "${formatAltitude(waypoint.altitude)} ${stringResource(R.string.unit_meters)}",
                )
                InfoRow(
                    stringResource(R.string.label_speed),
                    "${formatSpeedKmh(waypoint.speed)} ${stringResource(R.string.unit_kmh)}",
                )
                InfoRow(
                    stringResource(R.string.label_accuracy),
                    "±${formatAccuracy(waypoint.horizontalAccuracy)} ${stringResource(R.string.unit_meters)}",
                )
                InfoRow(
                    stringResource(R.string.label_satellites_used),
                    waypoint.satellitesUsedInFix?.toString() ?: DASH,
                )
                if (currentDistanceMeters != null) {
                    InfoRow(
                        stringResource(R.string.label_current_distance),
                        formatShortDistance(currentDistanceMeters),
                    )
                }
            }

            InfoCard(stringResource(R.string.label_saved_at)) {
                Text(
                    text = formatWaypointTimestamp(waypoint.timestamp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (session != null) {
                Card(onClick = { onOpenSession(session.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.waypoint_parent_session).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = session.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Button(
                onClick = onNavigate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Sym(icon = Glyph.Navigation, contentDescription = null, filled = true)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_navigate_to_waypoint))
            }

            OutlinedButton(
                onClick = { copyCoordinates(context, waypoint) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_copy_coordinates))
            }

            OutlinedButton(
                onClick = { openInMaps(context, waypoint) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_open_in_maps))
            }

            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Sym(icon = Glyph.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.waypoint_delete_dialog_title)) },
        text = { Text(stringResource(R.string.waypoint_delete_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Dialog to edit a saved item's name, pre-filled with its current value. */
@Composable
fun RenameDialog(
    title: String,
    label: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(label) },
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
private fun InfoCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
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
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = MonoFontFamily,
        )
    }
}

private fun copyCoordinates(context: Context, waypoint: Waypoint) {
    val text = formatCoordinatesForClipboard(waypoint.latitude, waypoint.longitude)
    context.getSystemService<ClipboardManager>()
        ?.setPrimaryClip(ClipData.newPlainText("coordinates", text))

    // Android 13+ shows its own "copied" confirmation UI, so only toast on older versions.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.coordinates_copied, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Ouvre le repère dans une app de cartographie via une URI `geo:`. Android présente le sélecteur
 * de toutes les apps géo installées (Google Maps, Organic Maps, OsmAnd…), sans coder pour chacune.
 */
private fun openInMaps(context: Context, waypoint: Waypoint) {
    // Locale.US force le point décimal : une virgule casserait l'URI geo:.
    val coordinates = "%.6f,%.6f".format(Locale.US, waypoint.latitude, waypoint.longitude)
    // q=lat,lon(label) place un marqueur nommé au point ; le nom doit être encodé.
    val uri = "geo:$coordinates?q=$coordinates(${Uri.encode(waypoint.name)})".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        // resolveActivity est peu fiable sous Android 11+ (visibilité des packages), d'où le catch.
        Toast.makeText(context, R.string.no_maps_app, Toast.LENGTH_SHORT).show()
    }
}
