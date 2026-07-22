package app.matthieu.cairngps.ui.gamification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.ui.theme.AchievementLabelGold
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.MonoFontFamily
import app.matthieu.cairngps.ui.theme.OnAmberButton
import app.matthieu.cairngps.ui.theme.Sym

/**
 * Route: the Levels screen, listing the full level scale (see [Levels.scale]). Reached by tapping
 * the level card on the Profil hub, so it carries its own back button.
 */
@Composable
fun LevelsRoute(
    achievementsRepository: AchievementsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LevelsViewModel = viewModel(factory = LevelsViewModel.factory(achievementsRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LevelsScreen(
        uiState = uiState,
        onBack = onBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelsScreen(
    uiState: LevelsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.levels_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    IconButton(onClick = onBack) {
                        Sym(icon = Glyph.ArrowBack, contentDescription = backLabel)
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
            items(uiState.items, key = { it.level }) { item ->
                LevelRow(item)
            }
        }
    }
}

@Composable
private fun LevelRow(item: LevelRowItem) {
    // Locked levels are dimmed but still show their title/threshold — the scale is fully revealed,
    // matching the design (only the progress toward them stays hidden, since there's none yet).
    val isLocked = item.state == LevelState.LOCKED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.4f else 1f),
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
                    .size(48.dp)
                    .background(
                        if (item.state == LevelState.LOCKED) MaterialTheme.colorScheme.surfaceVariant else CairnAmber,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val badgeTint = if (item.state == LevelState.LOCKED) MaterialTheme.colorScheme.onSurfaceVariant else OnAmberButton
                if (item.state == LevelState.REACHED) {
                    Sym(icon = Glyph.Check, contentDescription = null, tint = badgeTint)
                } else {
                    Text(
                        text = item.level.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFontFamily,
                        color = badgeTint,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(item.titleRes),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.state == LevelState.CURRENT) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.levels_current_badge).uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = CairnAmber,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.profile_level_xp_fmt, item.minXp),
                    fontSize = 12.5.sp,
                    fontFamily = MonoFontFamily,
                    color = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant else CairnGreen,
                )
                if (item.state == LevelState.CURRENT) {
                    LinearProgressIndicator(
                        progress = { item.fraction },
                        color = CairnAmber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .padding(top = 8.dp),
                    )
                    Text(
                        text = if (item.isMaxLevel) {
                            stringResource(R.string.profile_level_max)
                        } else {
                            stringResource(R.string.profile_level_remaining_fmt, item.xpRemaining ?: 0)
                        },
                        fontSize = 11.sp,
                        color = AchievementLabelGold,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }
}
