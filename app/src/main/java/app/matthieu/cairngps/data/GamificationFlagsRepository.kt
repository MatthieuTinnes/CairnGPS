package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for persisted [AchievementType.ETAT][app.matthieu.cairngps.domain.gamification.AchievementType.ETAT]
 * / [EVENEMENT][app.matthieu.cairngps.domain.gamification.AchievementType.EVENEMENT] flags,
 * wrapping the Room [GamificationFlagDao]. Each flag only ever gets set, never cleared — matching
 * [AchievementsRepository]'s "unlocked, never re-locked" semantics.
 *
 * ViewModels talk to this repository and never touch the DAO or [AppDatabase] directly.
 */
class GamificationFlagsRepository(private val dao: GamificationFlagDao) {

    /** Cold flow of every set flag key; re-emits whenever the table changes. */
    fun flags(): Flow<Set<String>> = dao.observeAll().map { flags -> flags.map { it.key }.toSet() }

    /** Marks [key] as set at [at]. A no-op if it was already set. */
    suspend fun set(key: String, at: Long = System.currentTimeMillis()) {
        dao.insert(GamificationFlag(key = key, setAt = at))
    }
}
