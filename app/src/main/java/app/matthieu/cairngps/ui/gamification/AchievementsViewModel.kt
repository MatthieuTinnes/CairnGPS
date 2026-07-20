package app.matthieu.cairngps.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.AchievementState
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.gamification.Achievements
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the Succès screen state (badge grid, screen 1k): every [Achievements] catalog entry
 * paired with its unlock date (if any), plus the closest-to-unlocking achievement.
 *
 * Purely a read model — unlocking itself is [app.matthieu.cairngps.data.GamificationManager]'s
 * job, kept decoupled from this UI layer.
 */
class AchievementsViewModel(
    achievementsRepository: AchievementsRepository,
    recordsRepository: RecordsRepository,
    sessionRepository: SessionRepository,
    waypointRepository: WaypointRepository,
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> = combine(
        achievementsRepository.unlocked(),
        recordsRepository.records(),
        sessionRepository.sessions(),
        waypointRepository.waypoints(),
    ) { unlocked, records, sessions, waypoints ->
        buildUiState(unlocked, records, sessions, waypoints)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AchievementsUiState(),
    )

    private fun buildUiState(
        unlocked: List<AchievementState>,
        records: List<RecordEntry>,
        sessions: List<Session>,
        waypoints: List<Waypoint>,
    ): AchievementsUiState {
        val unlockedAtById = unlocked.associate { it.id to it.unlockedAt }
        val metrics = Achievements.metricsFrom(records, sessions, waypoints)

        val items = Achievements.ALL.map { def -> AchievementItem(def, unlockedAtById[def.id]) }
        val next = Achievements.nextAchievement(metrics)?.let { NextAchievementUi(it.def, it.progress) }

        return AchievementsUiState(
            items = items,
            next = next,
            unlockedCount = unlocked.size,
            totalCount = Achievements.ALL.size,
        )
    }

    companion object {
        /** Factory that injects the repositories without needing a DI framework. */
        fun factory(
            achievementsRepository: AchievementsRepository,
            recordsRepository: RecordsRepository,
            sessionRepository: SessionRepository,
            waypointRepository: WaypointRepository,
        ): ViewModelProvider.Factory = factoryOf {
            AchievementsViewModel(achievementsRepository, recordsRepository, sessionRepository, waypointRepository)
        }
    }
}
