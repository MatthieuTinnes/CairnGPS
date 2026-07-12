package app.matthieu.cairngps.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.ui.common.StatTile
import app.matthieu.cairngps.ui.location.formatDistanceKm
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.waypoints.formatWaypointTimestamp

/**
 * Route: the "Profil" tab — a hub of lifetime totals and shortcuts to Carnet, Succès, Records and
 * Réglages (screen 1g). Owns the [ProfileViewModel], a pure read model over existing repositories.
 */
@Composable
fun ProfileRoute(
    sessionRepository: SessionRepository,
    achievementsRepository: AchievementsRepository,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(sessionRepository, achievementsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        uiState = uiState,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onOpenAchievements = onOpenAchievements,
        onOpenRecords = onOpenRecords,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    uiState: ProfileUiState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_profile)) },
                actions = {
                    val settingsLabel = stringResource(R.string.action_open_settings)
                    IconButton(onClick = onOpenSettings) {
                        Sym(icon = Glyph.Settings, contentDescription = settingsLabel)
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "avatar") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(icon = Glyph.Explore, contentDescription = null, filled = true, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        value = formatDistanceKm(uiState.totalDistanceMeters),
                        label = stringResource(R.string.profile_stat_total_km),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = uiState.sessionCount.toString(),
                        label = stringResource(R.string.profile_stat_sessions),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = "${uiState.unlockedCount}/${uiState.totalAchievements}",
                        label = stringResource(R.string.profile_stat_achievements),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val lastUnlocked = uiState.lastUnlocked
            val lastUnlockedAt = uiState.lastUnlockedAt
            if (lastUnlocked != null && lastUnlockedAt != null) {
                item(key = "last-achievement") {
                    Card(onClick = onOpenAchievements, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(CairnAmber, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Sym(icon = Glyph.EmojiEvents, contentDescription = null, filled = true, tint = MaterialTheme.colorScheme.background)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.profile_last_achievement_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${stringResource(lastUnlocked.titleRes)} · " +
                                        formatWaypointTimestamp(lastUnlockedAt),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "hub-history") {
                ProfileHubRow(
                    glyph = Glyph.Map,
                    title = stringResource(R.string.tab_history),
                    subtitle = stringResource(
                        R.string.profile_hub_history_subtitle_fmt,
                        uiState.sessionCount,
                    ),
                    onClick = onOpenHistory,
                )
            }
            item(key = "hub-achievements") {
                ProfileHubRow(
                    glyph = Glyph.MilitaryTech,
                    title = stringResource(R.string.tab_achievements),
                    subtitle = stringResource(
                        R.string.profile_hub_achievements_subtitle_fmt,
                        uiState.unlockedCount,
                        uiState.totalAchievements,
                    ),
                    onClick = onOpenAchievements,
                )
            }
            item(key = "hub-records") {
                ProfileHubRow(
                    glyph = Glyph.Leaderboard,
                    title = stringResource(R.string.records_title),
                    subtitle = stringResource(R.string.profile_hub_records_subtitle),
                    onClick = onOpenRecords,
                )
            }
        }
    }
}

@Composable
private fun ProfileHubRow(glyph: Char, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon = glyph, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
