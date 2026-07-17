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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.domain.format.formatWaypointMetaLine
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.WaypointIconBg

/**
 * The waypoints list body (empty state or [LazyColumn] of [WaypointRow]s), without its own
 * [androidx.compose.material3.Scaffold]/[androidx.compose.material3.TopAppBar]. Used by the
 * "Repères" tab of the Historique screen, which owns the surrounding chrome.
 */
@Composable
fun WaypointsListContent(
    uiState: WaypointsUiState,
    unitSystem: UnitSystem,
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
                WaypointRow(waypoint = waypoint, unitSystem = unitSystem, onClick = { onOpenWaypoint(waypoint.id) })
            }
        }
    }
}

@Composable
private fun WaypointRow(waypoint: Waypoint, unitSystem: UnitSystem, onClick: () -> Unit) {
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
                    .size(40.dp)
                    .background(WaypointIconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Sym(icon = WaypointIcons.glyphFor(waypoint.icon), contentDescription = null, filled = true, tint = CairnGreen, size = 20.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = waypoint.name, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = formatWaypointMetaLine(waypoint, unitSystem),
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
