package app.matthieu.cairngps.ui.gamification

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.ui.location.formatElevation
import app.matthieu.cairngps.ui.waypoints.formatWaypointTimestamp

/** Route: the "Succès" tab. Owns the [AchievementsViewModel] and opens Records from here. */
@Composable
fun AchievementsRoute(
    achievementsRepository: AchievementsRepository,
    recordsRepository: RecordsRepository,
    sessionRepository: SessionRepository,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AchievementsViewModel = viewModel(
        factory = AchievementsViewModel.factory(achievementsRepository, recordsRepository, sessionRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AchievementsScreen(
        uiState = uiState,
        onOpenRecords = onOpenRecords,
        modifier = modifier,
    )
}

/** Emoji glyph per family — same "text glyph, no icon library" convention as the rest of the UI. */
private val AchievementFamily.glyph: String
    get() = when (this) {
        AchievementFamily.ALTITUDE -> "⛰"
        AchievementFamily.SPEED -> "⚡"
        AchievementFamily.SATELLITES -> "🛰"
        AchievementFamily.DISTANCE -> "🥾"
        AchievementFamily.SESSIONS -> "🗺"
        AchievementFamily.GEO -> "🌍"
    }

private val AchievementFamily.titleRes: Int
    get() = when (this) {
        AchievementFamily.ALTITUDE -> R.string.achievement_family_altitude
        AchievementFamily.SPEED -> R.string.achievement_family_speed
        AchievementFamily.SATELLITES -> R.string.achievement_family_satellites
        AchievementFamily.DISTANCE -> R.string.achievement_family_distance
        AchievementFamily.SESSIONS -> R.string.achievement_family_sessions
        AchievementFamily.GEO -> R.string.achievement_family_geo
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementsScreen(
    uiState: AchievementsUiState,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_achievements)) },
                actions = {
                    TextButton(onClick = onOpenRecords) {
                        Text(stringResource(R.string.achievements_open_records))
                    }
                },
            )
        },
    ) { innerPadding ->
        val families = uiState.families
        if (families == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.achievements_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(families, key = { it.family }) { section ->
                FamilySection(section)
            }
        }
    }
}

@Composable
private fun FamilySection(section: AchievementFamilySection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = section.family.glyph, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(section.family.titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        section.progress?.let { progress ->
            val animatedFraction by animateFloatAsState(targetValue = progress.fraction)
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier.fillMaxWidth(),
            )
            if (progress.nextThreshold != null) {
                Text(
                    text = stringResource(
                        R.string.achievements_progress_fmt,
                        formatFamilyValue(section.family, progress.currentValue),
                        formatFamilyValue(section.family, progress.nextThreshold),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        section.items.forEach { item -> AchievementRow(item) }
    }
}

@Composable
private fun AchievementRow(item: AchievementItem) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (item.isUnlocked) "✅" else "🔒",
                style = MaterialTheme.typography.titleLarge,
            )
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val contentColor = if (item.isUnlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = stringResource(item.def.titleRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
                Text(
                    text = stringResource(item.def.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val unlockedAt = item.unlockedAt
                if (unlockedAt != null) {
                    Text(
                        text = stringResource(
                            R.string.achievement_unlocked_on_fmt,
                            formatWaypointTimestamp(unlockedAt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Formats a raw metric value in the unit its family is best expressed in, for the progress caption. */
private fun formatFamilyValue(family: AchievementFamily, value: Double): String = when (family) {
    AchievementFamily.ALTITUDE -> "${formatElevation(value)} m"
    AchievementFamily.SPEED -> "%.0f km/h".format(value * 3.6)
    AchievementFamily.SATELLITES -> "%.0f".format(value)
    AchievementFamily.DISTANCE -> "%.1f km".format(value / 1000.0)
    AchievementFamily.SESSIONS -> "%.0f".format(value)
    AchievementFamily.GEO -> "" // GEO has no progress bar (see Achievements.progressToNext)
}
