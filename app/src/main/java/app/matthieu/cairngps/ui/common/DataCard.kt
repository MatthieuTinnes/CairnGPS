package app.matthieu.cairngps.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.MonoFontFamily

/**
 * A rounded surface card with consistent padding for a single data group (Position screen's
 * coordinates/altitude/vitesse/précision blocks, recording stats…).
 * When [onClick] is provided the whole card becomes tappable (disabled while [enabled] is false).
 */
@Composable
fun DataCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Material3's default Card container pulls from the surfaceContainer* roles, which Compose
    // derives from primary/neutral tones rather than our explicit colorScheme.surface — so left
    // at its default it drifts from the design's flat #161C18. Force it back to colorScheme.surface
    // (which *is* DarkSurface / #161C18, set explicitly in Theme.kt) to match the mock exactly.
    // The disabled variant needs the same override: CoordinatesCard is disabled while there's no
    // fix, and without this it would fall back to Material3's dimmed disabled container, making it
    // look different from the other (always-enabled) cards — the design keeps every card the same
    // flat color regardless of fix state.
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        disabledContentColor = MaterialTheme.colorScheme.onSurface,
    )
    if (onClick != null) {
        Card(onClick = onClick, enabled = enabled, colors = colors, modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    } else {
        Card(colors = colors, modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        }
    }
}

/** Uppercase, muted label heading a [DataCard]'s content. */
@Composable
fun CardTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = LabelMuted,
    )
}

/** A large numeric value with a smaller trailing unit, baseline-aligned. */
@Composable
fun BigValue(
    value: String,
    unit: String,
    valueColor: Color = Color.Unspecified,
    unitColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            fontFamily = MonoFontFamily,
            color = valueColor,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.titleMedium,
            color = unitColor,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

/** A pulsing colored dot — used for the recording badge and the "session en cours" indicator. */
@Composable
fun PulsingDot(color: Color, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "rec-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "rec-pulse-alpha",
    )
    Box(
        modifier = Modifier
            .size(size)
            .background(color.copy(alpha = alpha), CircleShape),
    )
}
