package app.matthieu.cairngps.ui.waypoints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightWaypointIconBg
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.theme.WaypointIconBg

/**
 * Bottom sheet to pick a waypoint's icon (screens 6a dark / 6b light). A "Sélection actuelle"
 * preview shows [selectedKey]'s current icon and label, followed by a 5-column grid of every
 * [WaypointIcons.all] entry; tapping one calls [onSelect] with its key. Reachable from the
 * rename dialog (screen 3a, saved together with the name) and the waypoint detail header (1i,
 * saved immediately) — see [WaypointDetailScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointIconPickerSheet(
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = LocalIsLightTheme.current
    val accent = if (light) CairnGreenDark else CairnGreen
    val current = WaypointIcons.iconFor(selectedKey)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.waypoint_icon_sheet_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (light) accent.copy(alpha = 0.12f) else WaypointIconBg)
                        .border(2.dp, accent, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(icon = current.glyph, contentDescription = null, tint = accent, size = 28.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.waypoint_icon_current_selection),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = stringResource(current.labelRes), fontSize = 12.5.sp, color = LabelMuted)
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                items(items = WaypointIcons.all, key = { it.key }) { icon ->
                    IconGridCell(
                        icon = icon,
                        selected = icon.key == selectedKey,
                        onClick = { onSelect(icon.key) },
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

/** One cell of the icon grid (screens 6a/6b): a square tile, bordered in the accent color when selected. */
@Composable
private fun IconGridCell(icon: WaypointIcon, selected: Boolean, onClick: () -> Unit) {
    val light = LocalIsLightTheme.current
    val accent = if (light) CairnGreenDark else CairnGreen
    val label = stringResource(icon.labelRes)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected && light -> accent.copy(alpha = 0.12f)
                    selected -> WaypointIconBg
                    light -> LightWaypointIconBg
                    else -> WaypointIconBg
                },
            )
            .then(
                if (selected) Modifier.border(2.dp, accent, RoundedCornerShape(16.dp)) else Modifier,
            )
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Sym(
            icon = icon.glyph,
            contentDescription = label,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            size = 24.dp,
        )
    }
}
