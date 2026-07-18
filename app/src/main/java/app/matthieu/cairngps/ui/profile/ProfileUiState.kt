package app.matthieu.cairngps.ui.profile

import app.matthieu.cairngps.domain.gamification.AchievementDef
import app.matthieu.cairngps.ui.gamification.LevelInfo
import app.matthieu.cairngps.ui.gamification.Levels

/**
 * State of the Profil hub (screen 1g): a few lifetime totals plus shortcuts to Carnet / Succès /
 * Records / Réglages. Purely a read model built from existing repositories — no new persistence.
 *
 * @property totalDistanceMeters Sum of every recorded session's distance.
 * @property sessionCount        Total number of recorded sessions.
 * @property waypointCount       Total number of saved waypoints (repères), for the Carnet subtitle.
 * @property unlockedCount       Number of unlocked achievements.
 * @property totalAchievements   Size of the achievement catalog (denominator for "X / total").
 * @property lastUnlocked        The most recently unlocked achievement, or `null` if none yet.
 * @property lastUnlockedAt      When [lastUnlocked] was unlocked, in epoch millis.
 * @property level               The user's level, derived from the XP of every unlocked achievement.
 */
data class ProfileUiState(
    val totalDistanceMeters: Double = 0.0,
    val sessionCount: Int = 0,
    val waypointCount: Int = 0,
    val unlockedCount: Int = 0,
    val totalAchievements: Int = 0,
    val lastUnlocked: AchievementDef? = null,
    val lastUnlockedAt: Long? = null,
    val level: LevelInfo = Levels.forXp(0),
)
