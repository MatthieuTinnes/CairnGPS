package app.matthieu.cairngps.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.common.StatTile
import app.matthieu.cairngps.ui.location.formatDistanceKm
import app.matthieu.cairngps.ui.theme.AchievementBannerBg
import app.matthieu.cairngps.ui.theme.AchievementBannerBorder
import app.matthieu.cairngps.ui.theme.AchievementLabelGold
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.OnAmberButton
import app.matthieu.cairngps.ui.theme.OnGreenButton
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
    waypointRepository: WaypointRepository,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(sessionRepository, achievementsRepository, waypointRepository),
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
                            .background(CairnGreenDark, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Sym(icon = Glyph.Explore, contentDescription = null, filled = true, tint = OnGreenButton)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
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
                        value = uiState.unlockedCount.toString(),
                        valueSuffix = "/${uiState.totalAchievements}",
                        label = stringResource(R.string.profile_stat_achievements),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val lastUnlocked = uiState.lastUnlocked
            val lastUnlockedAt = uiState.lastUnlockedAt
            if (lastUnlocked != null && lastUnlockedAt != null) {
                item(key = "last-achievement") {
                    Card(
                        onClick = onOpenAchievements,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(width = 1.dp, color = AchievementBannerBorder, shape = RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = AchievementBannerBg),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(CairnAmber, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Sym(icon = Glyph.EmojiEvents, contentDescription = null, filled = true, tint = OnAmberButton)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.profile_last_achievement_label).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                    color = AchievementLabelGold,
                                )
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                                            append(stringResource(lastUnlocked.titleRes))
                                        }
                                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = CairnStone)) {
                                            append(" · " + formatWaypointTimestamp(lastUnlockedAt))
                                        }
                                    },
                                    fontSize = 15.sp,
                                )
                            }
                            Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = LabelMuted)
                        }
                    }
                }
            }

            item(key = "hub-history") {
                ProfileHubRow(
                    glyph = Glyph.Map,
                    iconTint = CairnGreen,
                    title = stringResource(R.string.tab_history),
                    subtitle = stringResource(
                        R.string.profile_hub_history_subtitle_fmt,
                        uiState.waypointCount,
                        uiState.sessionCount,
                    ),
                    onClick = onOpenHistory,
                )
            }
            item(key = "hub-achievements") {
                ProfileHubRow(
                    glyph = Glyph.MilitaryTech,
                    iconTint = CairnAmber,
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
                    iconTint = CairnGreen,
                    title = stringResource(R.string.records_title),
                    subtitle = stringResource(R.string.profile_hub_records_subtitle),
                    onClick = onOpenRecords,
                )
            }
        }
    }
}

@Composable
private fun ProfileHubRow(glyph: Char, iconTint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(icon = glyph, contentDescription = null, tint = iconTint)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, fontSize = 13.sp, color = LabelMuted)
            }
            Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = LabelMuted)
        }
    }
}
