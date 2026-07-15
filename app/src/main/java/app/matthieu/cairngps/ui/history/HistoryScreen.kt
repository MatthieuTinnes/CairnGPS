package app.matthieu.cairngps.ui.history

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.format.formatDistanceKm
import app.matthieu.cairngps.domain.format.formatDuration
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.formatWaypointShortDateTime
import app.matthieu.cairngps.ui.common.SegmentedToggle
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.ValueMuted
import app.matthieu.cairngps.ui.waypoints.WaypointsListContent
import app.matthieu.cairngps.ui.waypoints.WaypointsUiState
import app.matthieu.cairngps.ui.waypoints.WaypointsViewModel

/**
 * Route: "Carnet" — tabbed between saved waypoints ("Repères") and recorded sessions ("Sessions").
 * Each tab owns its own ViewModel/list; this composable only owns the shared [Scaffold] chrome and
 * the tab selection. Reached from the Profil hub, so it carries its own back button.
 */
@Composable
fun HistoryRoute(
    waypointRepository: WaypointRepository,
    sessionRepository: SessionRepository,
    onOpenWaypoint: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val waypointsViewModel: WaypointsViewModel =
        viewModel(factory = WaypointsViewModel.factory(waypointRepository))
    val sessionsViewModel: SessionsViewModel =
        viewModel(factory = SessionsViewModel.factory(sessionRepository))

    val waypointsUiState by waypointsViewModel.uiState.collectAsStateWithLifecycle()
    val sessionsUiState by sessionsViewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreen(
        waypointsUiState = waypointsUiState,
        sessionsUiState = sessionsUiState,
        onOpenWaypoint = onOpenWaypoint,
        onOpenSession = onOpenSession,
        onBack = onBack,
        modifier = modifier,
    )
}

private enum class HistoryTab(@StringRes val labelRes: Int) {
    WAYPOINTS(R.string.history_tab_waypoints),
    TRACKS(R.string.history_tab_tracks),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    waypointsUiState: WaypointsUiState,
    sessionsUiState: SessionsUiState,
    onOpenWaypoint: (Long) -> Unit,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HistoryTab.WAYPOINTS) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history)) },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val waypointCount = waypointsUiState.waypoints.orEmpty().size
            val sessionCount = sessionsUiState.sessions.orEmpty().size
            SegmentedToggle(
                options = HistoryTab.entries.map { tab ->
                    val count = if (tab == HistoryTab.WAYPOINTS) waypointCount else sessionCount
                    "${stringResource(tab.labelRes)} · $count"
                },
                selectedIndex = selectedTab.ordinal,
                onSelect = { index -> selectedTab = HistoryTab.entries[index] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when (selectedTab) {
                HistoryTab.WAYPOINTS -> WaypointsListContent(
                    uiState = waypointsUiState,
                    onOpenWaypoint = onOpenWaypoint,
                    modifier = Modifier.fillMaxSize(),
                )

                HistoryTab.TRACKS -> SessionsListContent(
                    uiState = sessionsUiState,
                    onOpenSession = onOpenSession,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SessionsListContent(
    uiState: SessionsUiState,
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isEmpty) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.sessions_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.sessions.orEmpty(), key = { it.session.id }) { sessionWithTrack ->
                SessionRow(
                    sessionWithTrack = sessionWithTrack,
                    onClick = { onOpenSession(sessionWithTrack.session.id) },
                )
            }
        }
    }
}

@Composable
private fun SessionRow(sessionWithTrack: SessionWithTrack, onClick: () -> Unit) {
    val (session, track) = sessionWithTrack

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteSparkline(track = track, modifier = Modifier.size(width = 52.dp, height = 34.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.name, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${formatDistanceKm(session.distanceMeters)} km · " +
                        "${formatDuration(session.durationMillis, showSecondsPastOneHour = false)} · " +
                        "+${formatElevation(session.elevationGain)} m",
                    fontSize = 12.sp,
                    fontFamily = MonoFontFamily,
                    color = LabelMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = formatWaypointShortDateTime(session.startTimestamp),
                    fontSize = 11.5.sp,
                    color = ValueMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = LabelMuted)
        }
    }
}
