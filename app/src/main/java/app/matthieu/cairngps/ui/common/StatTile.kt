package app.matthieu.cairngps.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.ui.theme.DarkSurface
import app.matthieu.cairngps.ui.theme.MonoFontFamily

/**
 * The small rounded stat card repeated throughout the design (Position's coordinates/vitesse/
 * altitude/précision blocks, session stats, Profil's totals, Records rows…): an uppercase label
 * over a large monospace value. A single implementation keeps the look consistent everywhere.
 *
 * Container color is pinned to the flat [DarkSurface] literal rather than left as the default
 * `Card`, whose tonal elevation otherwise tints it with the primary color and drifts it away from
 * the flat `#161C18` every other card in the design uses.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    // Optional muted, smaller suffix rendered right after the value on the same line — e.g. the
    // "/24" in Profil's "12/24" achievements tile (screen 1g). Unused by every other call site.
    valueSuffix: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = MonoFontFamily,
                    color = valueColor,
                )
                if (valueSuffix != null) {
                    Text(
                        text = valueSuffix,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
