package app.matthieu.cairngps.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.location.formatDistanceKm
import app.matthieu.cairngps.ui.location.formatDuration
import app.matthieu.cairngps.ui.location.formatElevation
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.waypoints.WaypointsListContent
import app.matthieu.cairngps.ui.waypoints.WaypointsUiState
import app.matthieu.cairngps.ui.waypoints.WaypointsViewModel
import app.matthieu.cairngps.ui.waypoints.formatWaypointTimestamp

/**
 * Route: "Carnet" — tabbed between saved waypoints ("Repères") and recorded traces ("Traces").
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
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                HistoryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.sessions.orEmpty(), key = { it.id }) { session ->
                SessionRow(session = session, onClick = { onOpenSession(session.id) })
            }
        }
    }
}

@Composable
private fun SessionRow(session: Session, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = session.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = formatWaypointTimestamp(session.startTimestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatDistanceKm(session.distanceMeters)} km · " +
                    "${formatDuration(session.durationMillis)} · " +
                    "D+ ${formatElevation(session.elevationGain)} m",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
