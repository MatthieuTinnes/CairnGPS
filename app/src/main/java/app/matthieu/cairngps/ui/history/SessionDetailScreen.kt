package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.location.formatDistanceKm
import app.matthieu.cairngps.ui.location.formatDuration
import app.matthieu.cairngps.ui.location.formatElevation
import app.matthieu.cairngps.ui.location.formatSpeedKmh
import app.matthieu.cairngps.ui.waypoints.formatWaypointTimestamp

/**
 * Route: loads the session identified by [sessionId] and renders its full detail, including the
 * waypoints attached to it. Navigates back automatically once the session has been deleted.
 */
@Composable
fun SessionDetailRoute(
    sessionId: Long,
    sessionRepository: SessionRepository,
    waypointRepository: WaypointRepository,
    onBack: () -> Unit,
    onOpenWaypoint: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SessionDetailViewModel = viewModel(
        factory = SessionDetailViewModel.factory(sessionRepository, waypointRepository, sessionId),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Once the delete completes, leave the detail screen (side-effect, not done during composition).
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    SessionDetailScreen(
        session = uiState.session,
        waypoints = uiState.waypoints,
        onBack = onBack,
        onOpenWaypoint = onOpenWaypoint,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailScreen(
    session: Session?,
    waypoints: List<Waypoint>,
    onBack: () -> Unit,
    onOpenWaypoint: (Long) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(session?.name ?: stringResource(R.string.session_detail_title)) },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = backLabel },
                    ) {
                        // Text glyph avoids depending on the large material-icons-extended artifact.
                        Text(text = "←", style = MaterialTheme.typography.headlineSmall)
                    }
                },
            )
        },
    ) { innerPadding ->
        // session stays null only for the brief load; nothing to render until it resolves.
        if (session == null) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoCard(stringResource(R.string.label_measurements)) {
                InfoRow(
                    stringResource(R.string.recording_distance),
                    "${formatDistanceKm(session.distanceMeters)} km",
                )
                InfoRow(
                    stringResource(R.string.recording_duration),
                    formatDuration(session.durationMillis),
                )
                InfoRow(
                    stringResource(R.string.recording_avg_speed),
                    "${formatSpeedKmh(session.averageSpeed)} ${stringResource(R.string.unit_kmh)}",
                )
                InfoRow(
                    stringResource(R.string.recording_max_speed),
                    "${formatSpeedKmh(session.maxSpeed)} ${stringResource(R.string.unit_kmh)}",
                )
                InfoRow(
                    stringResource(R.string.recording_elevation_gain),
                    "+${formatElevation(session.elevationGain)} ${stringResource(R.string.unit_meters)}",
                )
                InfoRow(
                    stringResource(R.string.recording_elevation_loss),
                    "-${formatElevation(session.elevationLoss)} ${stringResource(R.string.unit_meters)}",
                )
                InfoRow(
                    stringResource(R.string.label_altitude_min),
                    "${formatElevation(session.minAltitude)} ${stringResource(R.string.unit_meters)}",
                )
                InfoRow(
                    stringResource(R.string.label_altitude_max),
                    "${formatElevation(session.maxAltitude)} ${stringResource(R.string.unit_meters)}",
                )
            }

            InfoCard(stringResource(R.string.label_saved_at)) {
                Text(
                    text = formatWaypointTimestamp(session.startTimestamp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            InfoCard(stringResource(R.string.session_waypoints_label)) {
                if (waypoints.isEmpty()) {
                    Text(
                        text = stringResource(R.string.session_waypoints_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    waypoints.forEach { waypoint ->
                        SessionWaypointRow(waypoint = waypoint, onClick = { onOpenWaypoint(waypoint.id) })
                    }
                }
            }

            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SessionWaypointRow(waypoint: Waypoint, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = waypoint.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = formatWaypointTimestamp(waypoint.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_delete_dialog_title)) },
        text = { Text(stringResource(R.string.session_delete_dialog_message)) },
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
            fontFamily = FontFamily.Monospace,
        )
    }
}
