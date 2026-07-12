package app.matthieu.cairngps.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.ui.theme.MonoFontFamily

/**
 * The small rounded stat card repeated throughout the design (Position's coordinates/vitesse/
 * altitude/précision blocks, session stats, Profil's totals, Records rows…): an uppercase label
 * over a large monospace value. A single implementation keeps the look consistent and the
 * light/dark theming automatic (backed by [Card], which already tracks `colorScheme.surface`).
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = MonoFontFamily,
                color = valueColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
