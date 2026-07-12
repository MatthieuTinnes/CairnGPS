package app.matthieu.cairngps.ui.waypoints

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.location.formatAltitude
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.DarkSurface
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.WaypointIconBg

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
        WaypointsListContent(
            uiState = uiState,
            onOpenWaypoint = onOpenWaypoint,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * The waypoints list body (empty state or [LazyColumn] of [WaypointRow]s), without its own
 * [Scaffold]/[TopAppBar]. Shared between the standalone [WaypointsRoute] and the "Repères" tab of
 * the Historique screen, which each own their surrounding chrome.
 */
@Composable
fun WaypointsListContent(
    uiState: WaypointsUiState,
    onOpenWaypoint: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isEmpty) {
        EmptyState(modifier = modifier)
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(uiState.waypoints.orEmpty(), key = { it.id }) { waypoint ->
                WaypointRow(waypoint = waypoint, onClick = { onOpenWaypoint(waypoint.id) })
            }
        }
    }
}

@Composable
private fun WaypointRow(waypoint: Waypoint, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(WaypointIconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Sym(icon = Glyph.Flag, contentDescription = null, filled = true, tint = CairnGreen, size = 20.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = waypoint.name, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${formatAltitude(waypoint.altitude)} m · " +
                        "%.4f, %.4f".format(waypoint.latitude, waypoint.longitude) + " · " +
                        formatWaypointShortDate(waypoint.timestamp),
                    fontSize = 12.5.sp,
                    fontFamily = MonoFontFamily,
                    color = LabelMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = LabelMuted)
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
