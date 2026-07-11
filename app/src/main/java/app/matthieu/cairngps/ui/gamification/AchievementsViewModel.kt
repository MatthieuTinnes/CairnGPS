package app.matthieu.cairngps.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.AchievementState
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes the Succès screen state: every [Achievements] catalog entry grouped by family, each
 * paired with its unlock date (if any) and the family's progress towards its next palier.
 *
 * Purely a read model — unlocking itself is [app.matthieu.cairngps.data.GamificationManager]'s
 * job, kept decoupled from this UI layer.
 */
class AchievementsViewModel(
    achievementsRepository: AchievementsRepository,
    recordsRepository: RecordsRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> = combine(
        achievementsRepository.unlocked(),
        recordsRepository.records(),
        sessionRepository.sessions(),
    ) { unlocked, records, sessions ->
        buildUiState(unlocked, records, sessions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AchievementsUiState(),
    )

    private fun buildUiState(
        unlocked: List<AchievementState>,
        records: List<RecordEntry>,
        sessions: List<Session>,
    ): AchievementsUiState {
        val unlockedAtById = unlocked.associate { it.id to it.unlockedAt }
        val metrics = Achievements.metricsFrom(records, sessions)

        val families = AchievementFamily.entries.map { family ->
            val items = Achievements.ALL
                .filter { it.family == family }
                .sortedBy { it.threshold }
                .map { def -> AchievementItem(def, unlockedAtById[def.id]) }
            AchievementFamilySection(
                family = family,
                items = items,
                progress = Achievements.progressToNext(family, metrics),
            )
        }
        return AchievementsUiState(families)
    }

    companion object {
        /** Factory that injects the repositories without needing a DI framework. */
        fun factory(
            achievementsRepository: AchievementsRepository,
            recordsRepository: RecordsRepository,
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return AchievementsViewModel(achievementsRepository, recordsRepository, sessionRepository) as T
                }
            }
    }
}
