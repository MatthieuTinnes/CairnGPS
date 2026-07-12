package app.matthieu.cairngps.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.ui.gamification.Achievements
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Combines sessions and unlocked achievements into the Profil hub's lifetime totals. */
class ProfileViewModel(
    sessionRepository: SessionRepository,
    achievementsRepository: AchievementsRepository,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        sessionRepository.sessions(),
        achievementsRepository.unlocked(),
    ) { sessions, unlocked ->
        val lastUnlockedState = unlocked.maxByOrNull { it.unlockedAt }
        val lastUnlockedDef = lastUnlockedState?.let { state ->
            Achievements.ALL.firstOrNull { it.id == state.id }
        }
        ProfileUiState(
            totalDistanceMeters = sessions.sumOf { it.distanceMeters },
            sessionCount = sessions.size,
            unlockedCount = unlocked.size,
            totalAchievements = Achievements.ALL.size,
            lastUnlocked = lastUnlockedDef,
            lastUnlockedAt = lastUnlockedState?.unlockedAt,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(totalAchievements = Achievements.ALL.size),
    )

    companion object {
        /** Factory that injects the repositories without needing a DI framework. */
        fun factory(
            sessionRepository: SessionRepository,
            achievementsRepository: AchievementsRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return ProfileViewModel(sessionRepository, achievementsRepository) as T
                }
            }
    }
}
