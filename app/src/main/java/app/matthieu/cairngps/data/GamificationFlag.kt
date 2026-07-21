package app.matthieu.cairngps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persistent, never-cleared flag backing an [app.matthieu.cairngps.domain.gamification.AchievementType.ETAT]
 * or [app.matthieu.cairngps.domain.gamification.AchievementType.EVENEMENT] achievement condition —
 * e.g. "has used the light theme", "has exported a GPX trace", "is within 100 m of a lat/lon
 * confluence". Presence of a row is the flag being set; there is no "unset" row, mirroring
 * [AchievementState]'s "unlocked" semantics.
 *
 * @property key    Stable flag identifier (e.g. `"theme_light"`, `"app_export"`), matched against
 *                  in `Achievements.ALL`'s predicates via [app.matthieu.cairngps.domain.gamification.GamificationMetrics.flags].
 * @property setAt  When this flag was first set, in milliseconds since the epoch.
 */
@Entity(tableName = "gamification_flags")
data class GamificationFlag(
    @PrimaryKey val key: String,
    val setAt: Long,
)
