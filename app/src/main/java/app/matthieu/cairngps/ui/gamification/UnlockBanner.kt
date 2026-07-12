package app.matthieu.cairngps.ui.gamification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.Sym
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Global "achievement unlocked" banner, mounted once at the app root (above the `NavHost`) so it
 * is visible no matter which screen the user is on — deliberately not tied to any single raw-data
 * screen. Purely a display concern: unlocking itself happens in
 * [app.matthieu.cairngps.data.GamificationManager], which this only observes via [unlockedEvents].
 */
@Composable
fun UnlockBanner(unlockedEvents: SharedFlow<AchievementDef>, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<AchievementDef?>(null) }

    LaunchedEffect(unlockedEvents) {
        unlockedEvents.collect { def -> current = def }
    }

    // Restarts the auto-dismiss timer whenever a new achievement takes over the banner, so a
    // burst of near-simultaneous unlocks doesn't hide one before it's had time to be read.
    LaunchedEffect(current) {
        if (current != null) {
            delay(AUTO_DISMISS_MS.milliseconds)
            current = null
        }
    }

    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val def = current
        if (def != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(
                            icon = Glyph.EmojiEvents,
                            contentDescription = null,
                            filled = true,
                            tint = MaterialTheme.colorScheme.onSecondary,
                        )
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = stringResource(R.string.achievement_unlocked_banner_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = stringResource(def.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}

private const val AUTO_DISMISS_MS = 3_500L
