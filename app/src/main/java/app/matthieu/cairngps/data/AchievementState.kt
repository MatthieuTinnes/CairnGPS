package app.matthieu.cairngps.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Marks an achievement as unlocked. The catalog of achievements (id, title, description,
 * condition) lives in code (`ui.gamification.Achievements`) rather than in this table — Room only
 * needs to remember which ids are unlocked and when, so adding a new achievement never requires a
 * migration. Presence of a row is the unlocked state; there is no "locked" row.
 *
 * @property id          Catalog id of the unlocked achievement (`Achievements.AchievementDef.id`).
 * @property unlockedAt  When it was unlocked, in milliseconds since the epoch.
 */
@Entity(tableName = "achievements")
@Serializable
data class AchievementState(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)
