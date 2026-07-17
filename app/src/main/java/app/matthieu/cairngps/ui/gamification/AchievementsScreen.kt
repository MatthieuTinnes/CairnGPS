package app.matthieu.cairngps.ui.gamification

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.format.distanceUnitLabel
import app.matthieu.cairngps.domain.format.formatAltitude
import app.matthieu.cairngps.domain.format.formatDistance
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.formatSpeed
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.domain.format.speedUnitLabel
import app.matthieu.cairngps.ui.settings.SettingsViewModel
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.Sym

/**
 * Route: "Succès" (screen 1k) — a badge grid over the whole achievement catalog. Reached from the
 * Profil hub, so it carries its own back button.
 */
@Composable
fun AchievementsRoute(
    achievementsRepository: AchievementsRepository,
    recordsRepository: RecordsRepository,
    sessionRepository: SessionRepository,
    waypointRepository: WaypointRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AchievementsViewModel = viewModel(
        factory = AchievementsViewModel.factory(
            achievementsRepository,
            recordsRepository,
            sessionRepository,
            waypointRepository,
        ),
    )
    val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModel.factory(settingsRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    AchievementsScreen(
        uiState = uiState,
        unitSystem = settings.unitSystem,
        onBack = onBack,
        modifier = modifier,
    )
}

/** Material Symbols glyph per family. */
private val AchievementFamily.glyph: Char
    get() = when (this) {
        AchievementFamily.ALTITUDE -> Glyph.Landscape
        AchievementFamily.SPEED -> Glyph.Speed
        AchievementFamily.SATELLITES -> Glyph.SatelliteAlt
        AchievementFamily.DISTANCE -> Glyph.Route
        AchievementFamily.SESSIONS -> Glyph.Map
        AchievementFamily.GEO -> Glyph.Public
        AchievementFamily.TIME -> Glyph.WbTwilight
    }

private val AchievementFamily.titleRes: Int
    get() = when (this) {
        AchievementFamily.ALTITUDE -> R.string.achievement_family_altitude
        AchievementFamily.SPEED -> R.string.achievement_family_speed
        AchievementFamily.SATELLITES -> R.string.achievement_family_satellites
        AchievementFamily.DISTANCE -> R.string.achievement_family_distance
        AchievementFamily.SESSIONS -> R.string.achievement_family_sessions
        AchievementFamily.GEO -> R.string.achievement_family_geo
        AchievementFamily.TIME -> R.string.achievement_family_time
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementsScreen(
    uiState: AchievementsUiState,
    unitSystem: UnitSystem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_achievements)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
                    }
                },
                actions = {
                    Text(
                        text = stringResource(
                            R.string.achievements_counter_fmt,
                            uiState.unlockedCount,
                            uiState.totalCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = MonoFontFamily,
                        color = CairnAmber,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        val items = uiState.items
        if (items == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.achievements_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            uiState.next?.let { next ->
                item(span = { GridItemSpan(maxLineSpan) }) { NextAchievementCard(next, unitSystem) }
            }
            items(items, key = { it.def.id }) { item -> AchievementBadge(item, unitSystem) }
        }
    }
}

@Composable
private fun NextAchievementCard(next: NextAchievementUi, unitSystem: UnitSystem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = CairnGreenDark, shape = RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Sym(icon = next.def.family.glyph, contentDescription = null, filled = true, tint = CairnGreen)
            }
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.achievements_next_label_fmt,
                        stringResource(R.string.achievement_xp_fmt, next.def.xp),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CairnGreen,
                )
                Text(
                    text = stringResource(next.def.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val animatedFraction by animateFloatAsState(targetValue = next.progress.fraction)
                    LinearProgressIndicator(
                        progress = { animatedFraction },
                        color = CairnGreen,
                        modifier = Modifier.weight(1f),
                    )
                    val nextThreshold = next.progress.nextThreshold
                    if (nextThreshold != null) {
                        Text(
                            text = formatFamilyValue(next.def.family, nextThreshold, unitSystem),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFontFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(item: AchievementItem, unitSystem: UnitSystem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.isUnlocked) 1f else 0.4f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 14.dp, start = 10.dp, end = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Sym(
                    icon = item.def.family.glyph,
                    contentDescription = null,
                    filled = item.isUnlocked,
                    size = 28.dp,
                    tint = if (item.isUnlocked) CairnGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(item.def.titleRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = achievementDescription(item.def, unitSystem),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.achievement_xp_fmt, item.def.xp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MonoFontFamily,
                fontWeight = FontWeight.Bold,
                color = CairnAmber,
            )
        }
    }
}

/** Formats a raw metric value in the unit its family is best expressed in, for the progress caption. */
private fun formatFamilyValue(family: AchievementFamily, value: Double, unitSystem: UnitSystem): String = when (family) {
    AchievementFamily.ALTITUDE -> "${formatElevation(value, unitSystem)} ${shortUnitLabel(unitSystem)}"
    AchievementFamily.SPEED -> "${formatSpeed(value.toFloat(), unitSystem)} ${speedUnitLabel(unitSystem)}"
    AchievementFamily.SATELLITES -> "%.0f".format(value)
    AchievementFamily.DISTANCE -> "${formatDistance(value, unitSystem)} ${distanceUnitLabel(unitSystem)}"
    AchievementFamily.SESSIONS -> "%.0f".format(value)
    AchievementFamily.GEO -> "" // GEO has no progress bar (see Achievements.progressToNext)
    AchievementFamily.TIME -> "" // TIME has no progress bar (see Achievements.progressToNext)
}

/**
 * The achievement's description, with its family's threshold converted to the display unit
 * system — [AchievementDef.descRes] is a format string taking the formatted value and its unit
 * label for ALTITUDE/SPEED/DISTANCE, and a plain string otherwise.
 */
@Composable
private fun achievementDescription(def: AchievementDef, unitSystem: UnitSystem): String = when (def.family) {
    AchievementFamily.ALTITUDE -> stringResource(def.descRes, formatAltitude(def.threshold, unitSystem), shortUnitLabel(unitSystem))
    AchievementFamily.SPEED -> stringResource(def.descRes, formatSpeed(def.threshold.toFloat(), unitSystem), speedUnitLabel(unitSystem))
    AchievementFamily.DISTANCE -> stringResource(def.descRes, formatDistance(def.threshold, unitSystem), distanceUnitLabel(unitSystem))
    else -> stringResource(def.descRes)
}
