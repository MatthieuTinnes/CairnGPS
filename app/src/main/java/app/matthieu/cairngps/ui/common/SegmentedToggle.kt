package app.matthieu.cairngps.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.CompassDialBorderLight
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.OnGreenButton
import app.matthieu.cairngps.ui.theme.OutlineSubtle
import app.matthieu.cairngps.ui.theme.Sym

/**
 * The rounded, two-or-more-way pill toggle repeated throughout the design (north reference,
 * coordinate format, Carnet's repères/sessions switch…). A single implementation keeps that look
 * consistent everywhere instead of a bespoke `Row` per screen.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val light = LocalIsLightTheme.current
    val dividerColor = if (light) CompassDialBorderLight else OutlineSubtle

    Row(
        // Height follows content (min 40dp via segment padding) rather than a fixed 40dp, so a
        // long label that wraps to two lines (e.g. "Nord géographique" sharing its segment with
        // the checkmark glyph) grows the pill instead of being clipped/overlapping neighbors.
        modifier = modifier
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, dividerColor, RoundedCornerShape(20.dp))
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) CairnGreenDark else Color.Transparent)
                    .selectable(selected = selected, onClick = { onSelect(index) }, role = Role.RadioButton)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Sym(
                        icon = Glyph.Check,
                        contentDescription = null,
                        size = 18.dp,
                        tint = OnGreenButton,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    // The selected pill's background is the fixed CairnGreenDark literal in both
                    // themes, so its text uses the matching fixed OnGreenButton rather than
                    // colorScheme.onSurface — that role flips to near-black in light theme and
                    // would be unreadable on the still-dark-green pill (design 5m/5g).
                    color = if (selected) OnGreenButton else if (light) LightStatusText else CairnStone,
                )
            }
            if (index != options.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(dividerColor),
                )
            }
        }
    }
}
