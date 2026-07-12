package app.matthieu.cairngps.ui.profile

import app.matthieu.cairngps.ui.gamification.AchievementDef

/**
 * State of the Profil hub (screen 1g): a few lifetime totals plus shortcuts to Carnet / Succès /
 * Records / Réglages. Purely a read model built from existing repositories — no new persistence.
 *
 * @property totalDistanceMeters Sum of every recorded session's distance.
 * @property sessionCount        Total number of recorded sessions.
 * @property unlockedCount       Number of unlocked achievements.
 * @property totalAchievements   Size of the achievement catalog (denominator for "X / total").
 * @property lastUnlocked        The most recently unlocked achievement, or `null` if none yet.
 * @property lastUnlockedAt      When [lastUnlocked] was unlocked, in epoch millis.
 */
data class ProfileUiState(
    val totalDistanceMeters: Double = 0.0,
    val sessionCount: Int = 0,
    val unlockedCount: Int = 0,
    val totalAchievements: Int = 0,
    val lastUnlocked: AchievementDef? = null,
    val lastUnlockedAt: Long? = null,
)
