package app.matthieu.cairngps.ui.recording

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.StopResult
import app.matthieu.cairngps.domain.format.formatAccuracy
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.Sym
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Global "track not saved" banner, mounted once at the app root (above the `NavHost`) next to
 * [app.matthieu.cairngps.ui.gamification.UnlockBanner] — same rationale: a recording can be
 * stopped from any screen (or from the notification, while the app is foregrounded), so the
 * notice needs to be visible regardless of which screen is on top when it fires.
 *
 * Purely a display concern: the actual discard decision happens in
 * [app.matthieu.cairngps.data.RecordingRepository.stop], this only observes [discardedEvents].
 */
@Composable
fun DiscardedRecordingBanner(
    discardedEvents: SharedFlow<StopResult.Discarded>,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<StopResult.Discarded?>(null) }
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    LaunchedEffect(discardedEvents) {
        discardedEvents.collect { event -> current = event }
    }

    // Restarts the auto-dismiss timer whenever a new discard takes over the banner, so a second
    // discarded recording shortly after the first doesn't cut the first message short.
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
        val event = current
        if (event != null) {
            val unitSystem = settings.unitSystem
            val message = event.lastRejectedAccuracyMeters?.let { accuracy ->
                stringResource(
                    R.string.recording_discarded_message_fmt,
                    formatAccuracy(accuracy, unitSystem),
                    shortUnitLabel(unitSystem),
                )
            } ?: stringResource(R.string.recording_discarded_message_no_fix)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Sym(
                        icon = Glyph.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = stringResource(R.string.recording_discarded_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}
private const val AUTO_DISMISS_MS = 6_000L
