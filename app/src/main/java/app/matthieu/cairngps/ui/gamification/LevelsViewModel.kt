package app.matthieu.cairngps.ui.gamification

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.domain.gamification.Achievements
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Whether a level in the scale is behind, is, or is ahead of the user's current level. */
enum class LevelState { REACHED, CURRENT, LOCKED }

/**
 * One row of the Levels screen. [fraction], [xpRemaining] and [isMaxLevel] are only meaningful for
 * the [LevelState.CURRENT] row — they mirror [LevelInfo], carried over from [Levels.forXp].
 */
data class LevelRowItem(
    val level: Int,
    @StringRes val titleRes: Int,
    val minXp: Int,
    val state: LevelState,
    val fraction: Float = 0f,
    val xpRemaining: Int? = null,
    val isMaxLevel: Boolean = false,
)

data class LevelsUiState(
    val currentLevel: Int = 1,
    val items: List<LevelRowItem> = emptyList(),
)

/** Exposes the full level scale (see [Levels.scale]) for the Levels screen, reached from the Profil hub. */
class LevelsViewModel(achievementsRepository: AchievementsRepository) : ViewModel() {

    val uiState: StateFlow<LevelsUiState> = achievementsRepository.unlocked()
        .map { unlocked ->
            val totalXp = Achievements.xpFor(unlocked.map { it.id }.toSet())
            val current = Levels.forXp(totalXp)
            LevelsUiState(
                currentLevel = current.level,
                // Highest level first, matching the screen's top-to-bottom reading order.
                items = Levels.scale.sortedByDescending { it.level }.map { entry ->
                    LevelRowItem(
                        level = entry.level,
                        titleRes = entry.titleRes,
                        minXp = entry.minXp,
                        state = when {
                            entry.level == current.level -> LevelState.CURRENT
                            entry.level < current.level -> LevelState.REACHED
                            else -> LevelState.LOCKED
                        },
                        fraction = if (entry.level == current.level) current.fraction else 0f,
                        xpRemaining = if (entry.level == current.level) current.xpRemaining else null,
                        isMaxLevel = if (entry.level == current.level) current.isMaxLevel else false,
                    )
                },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevelsUiState(),
        )

    companion object {
        /** Factory that injects the [AchievementsRepository] without needing a DI framework. */
        fun factory(achievementsRepository: AchievementsRepository): ViewModelProvider.Factory =
            factoryOf { LevelsViewModel(achievementsRepository) }
    }
}
