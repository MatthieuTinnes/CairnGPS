package app.matthieu.cairngps.ui.waypoints

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository

/** Route: wires up the [WaypointsViewModel] and renders the saved-waypoints list. */
@Composable
fun WaypointsRoute(
    waypointRepository: WaypointRepository,
    onOpenWaypoint: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WaypointsViewModel =
        viewModel(factory = WaypointsViewModel.factory(waypointRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WaypointsListScreen(
        uiState = uiState,
        onOpenWaypoint = onOpenWaypoint,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaypointsListScreen(
    uiState: WaypointsUiState,
    onOpenWaypoint: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.waypoints_title)) })
        },
    ) { innerPadding ->
        if (uiState.isEmpty) {
            EmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.waypoints.orEmpty(), key = { it.id }) { waypoint ->
                    WaypointRow(waypoint = waypoint, onClick = { onOpenWaypoint(waypoint.id) })
                }
            }
        }
    }
}

@Composable
private fun WaypointRow(waypoint: Waypoint, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = waypoint.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "%.6f, %.6f".format(waypoint.latitude, waypoint.longitude),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatWaypointTimestamp(waypoint.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.waypoints_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
