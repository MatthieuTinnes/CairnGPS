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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.format.distanceUnitLabel
import app.matthieu.cairngps.domain.format.formatDistance
import app.matthieu.cairngps.domain.format.formatWaypointTimestamp
import app.matthieu.cairngps.ui.common.StatTile
import app.matthieu.cairngps.ui.gamification.LevelInfo
import app.matthieu.cairngps.ui.settings.SettingsViewModel
import app.matthieu.cairngps.ui.theme.AchievementBannerBg
import app.matthieu.cairngps.ui.theme.AchievementBannerBorder
import app.matthieu.cairngps.ui.theme.AchievementLabelGold
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.CairnStone
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.LightAchievementBannerBg
import app.matthieu.cairngps.ui.theme.LightAchievementBannerBorder
import app.matthieu.cairngps.ui.theme.LightAchievementLabelGold
import app.matthieu.cairngps.ui.theme.LightStatusText
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OnAmberButton
import app.matthieu.cairngps.ui.theme.Sym

/**
 * Route: the "Profil" tab — a hub of lifetime totals and shortcuts to Carnet, Succès, Records and
 * Réglages (screen 1g). Owns the [ProfileViewModel], a pure read model over existing repositories.
 */
@Composable
fun ProfileRoute(
    sessionRepository: SessionRepository,
    achievementsRepository: AchievementsRepository,
    waypointRepository: WaypointRepository,
    settingsRepository: SettingsRepository,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenLevels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(sessionRepository, achievementsRepository, waypointRepository),
    )
    val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(settingsRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    ProfileScreen(
        uiState = uiState,
        unitSystem = settings.unitSystem,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onOpenAchievements = onOpenAchievements,
        onOpenRecords = onOpenRecords,
        onOpenLevels = onOpenLevels,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    uiState: ProfileUiState,
    unitSystem: UnitSystem,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenRecords: () -> Unit,
    onOpenLevels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_profile)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            item(key = "level") {
                LevelCard(uiState.level, onClick = onOpenLevels)
            }

            item(key = "stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(
                        value = formatDistance(uiState.totalDistanceMeters, unitSystem),
                        label = stringResource(R.string.profile_stat_total_km_fmt, distanceUnitLabel(unitSystem)),
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
                    // The banner's amber-tinted surface is a fixed dark literal in the design (1g)
                    // that stays dark in light theme (5f), so its background/border/label/subtitle
                    // pick light-theme-specific values here.
                    val light = LocalIsLightTheme.current
                    Card(
                        onClick = onOpenAchievements,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (light) LightAchievementBannerBorder else AchievementBannerBorder,
                                shape = RoundedCornerShape(20.dp),
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (light) LightAchievementBannerBg else AchievementBannerBg,
                        ),
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
                                    color = if (light) LightAchievementLabelGold else AchievementLabelGold,
                                )
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                                            append(stringResource(lastUnlocked.titleRes))
                                        }
                                        withStyle(
                                            SpanStyle(
                                                fontWeight = FontWeight.Normal,
                                                color = if (light) LightStatusText else CairnStone,
                                            ),
                                        ) {
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

/**
 * The gold Level card (screen 1g): level number, title, XP total, progress bar to next level.
 * Tapping it opens the full level scale (see [app.matthieu.cairngps.ui.gamification.LevelsRoute]).
 */
@Composable
private fun LevelCard(level: LevelInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(CairnAmber, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.profile_level_label),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = OnAmberButton,
                    )
                    Text(
                        text = level.level.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFontFamily,
                        color = OnAmberButton,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(level.titleRes),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.profile_level_xp_fmt, level.totalXp),
                    fontSize = 12.5.sp,
                    fontFamily = MonoFontFamily,
                    color = AchievementLabelGold,
                )
                LinearProgressIndicator(
                    progress = { level.fraction },
                    color = CairnAmber,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(top = 8.dp),
                )
                Text(
                    text = if (level.isMaxLevel) {
                        stringResource(R.string.profile_level_max)
                    } else {
                        stringResource(R.string.profile_level_remaining_fmt, level.xpRemaining ?: 0)
                    },
                    fontSize = 11.sp,
                    color = AchievementLabelGold,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            Sym(icon = Glyph.ChevronRight, contentDescription = null, tint = LabelMuted)
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
