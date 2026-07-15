package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.format.formatDistanceKm
import app.matthieu.cairngps.domain.format.formatDuration
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.formatSpeedKmh
import app.matthieu.cairngps.domain.format.formatTimeOfDay
import app.matthieu.cairngps.domain.format.formatWaypointMetaLine
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.SoftError
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.WaypointIconBg
import app.matthieu.cairngps.ui.waypoints.DeleteConfirmDialog
import app.matthieu.cairngps.ui.waypoints.RenameDialog

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
        track = uiState.track,
        onBack = onBack,
        onOpenWaypoint = onOpenWaypoint,
        onDelete = viewModel::delete,
        onRename = viewModel::rename,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDetailScreen(
    session: Session?,
    waypoints: List<Waypoint>,
    track: List<TrackPoint>,
    onBack: () -> Unit,
    onOpenWaypoint: (Long) -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            title = stringResource(R.string.session_delete_dialog_title),
            message = stringResource(R.string.session_delete_dialog_message),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            },
        )
    }

    if (showRenameDialog && session != null) {
        RenameDialog(
            title = stringResource(R.string.session_rename_dialog_title),
            label = stringResource(R.string.session_name_label),
            initialName = session.name,
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
                title = {
                    Column {
                        Text(
                            text = session?.name ?: stringResource(R.string.session_detail_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        if (session != null) {
                            Text(
                                text = "${formatTimeOfDay(session.startTimestamp)} → ${formatTimeOfDay(session.endTimestamp)}",
                                fontSize = 12.5.sp,
                                color = LabelMuted,
                            )
                        }
                    }
                },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
                actions = {
                    if (session != null) {
                        val deleteLabel = stringResource(R.string.action_delete)
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Sym(icon = Glyph.Delete, contentDescription = deleteLabel, tint = SoftError)
                        }
                        val renameLabel = stringResource(R.string.action_rename)
                        IconButton(onClick = { showRenameDialog = true }) {
                            Sym(icon = Glyph.Edit, contentDescription = renameLabel)
                        }
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
            // Only shown when a track was sampled: sessions recorded before this feature existed
            // (or too short to have sampled any point) simply have no route/profile to draw.
            if (track.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    SessionRouteTrace(
                        track = track,
                        startTimestamp = session.startTimestamp,
                        endTimestamp = session.endTimestamp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SessionStatTile(
                    formatDistanceKm(session.distanceMeters),
                    stringResource(R.string.session_stat_distance),
                    Modifier.weight(1f),
                )
                SessionStatTile(
                    formatDuration(session.durationMillis, showSecondsPastOneHour = false),
                    stringResource(R.string.session_stat_duration),
                    Modifier.weight(1f),
                )
                SessionStatTile(
                    "+${formatElevation(session.elevationGain)}",
                    stringResource(R.string.session_stat_elevation_gain),
                    Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SessionStatTile(
                    "−${formatElevation(session.elevationLoss)}",
                    stringResource(R.string.session_stat_elevation_loss),
                    Modifier.weight(1f),
                )
                SessionStatTile(
                    formatSpeedKmh(session.maxSpeed),
                    stringResource(R.string.session_stat_max_speed),
                    Modifier.weight(1f),
                )
                SessionStatTile(
                    formatSpeedKmh(session.averageSpeed),
                    stringResource(R.string.session_stat_avg_speed),
                    Modifier.weight(1f),
                )
            }

            if (track.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)) {
                        Text(
                            text = stringResource(R.string.label_altitude_profile).uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            color = LabelMuted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                        )
                        AltitudeProfile(track = track, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.session_waypoints_label).uppercase()} · ${waypoints.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    color = LabelMuted,
                    modifier = Modifier.padding(start = 4.dp),
                )
                if (waypoints.isEmpty()) {
                    Text(
                        text = stringResource(R.string.session_waypoints_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                } else {
                    waypoints.forEach { waypoint ->
                        SessionWaypointRow(waypoint = waypoint, onClick = { onOpenWaypoint(waypoint.id) })
                    }
                }
            }
        }
    }
}

/** One tile of the 3x2 session stats grid (screen 1j). */
@Composable
private fun SessionStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoFontFamily)
            Text(
                text = label,
                fontSize = 11.sp,
                color = LabelMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SessionWaypointRow(waypoint: Waypoint, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(WaypointIconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Sym(icon = Glyph.Flag, contentDescription = null, filled = true, tint = CairnGreen, size = 19.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = waypoint.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = CairnGreen)
                Text(
                    text = formatWaypointMetaLine(waypoint),
                    fontSize = 12.sp,
                    fontFamily = MonoFontFamily,
                    color = LabelMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = LabelMuted)
        }
    }
}
