package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for unlocked achievements, wrapping the Room [AchievementDao]. The
 * achievement catalog itself (id, title, description, condition) is not persisted here — see
 * `domain.gamification.Achievements` — this repository only remembers which catalog ids are unlocked.
 *
 * ViewModels talk to this repository and never touch the DAO or [AppDatabase] directly, keeping
 * persistence details out of the UI layer.
 */
class AchievementsRepository(private val dao: AchievementDao) {

    /** Cold flow of every unlocked achievement; re-emits whenever the table changes. */
    fun unlocked(): Flow<List<AchievementState>> = dao.observeAll()

    /**
     * Marks achievement [id] as unlocked at [at]. Returns `true` if this call is what unlocked it
     * (a fresh unlock — the signal [GamificationManager] uses to fire the unlock banner); `false`
     * if it was already unlocked.
     */
    suspend fun markUnlocked(id: String, at: Long = System.currentTimeMillis()): Boolean =
        dao.insert(AchievementState(id = id, unlockedAt = at)) != -1L
}
