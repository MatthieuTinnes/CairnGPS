package app.matthieu.cairngps.ui.gamification

import androidx.annotation.StringRes
import app.matthieu.cairngps.R

/**
 * The user's level derived from [totalXp] (see [Achievements.xpFor]) — purely a read model, no
 * new persistence: XP is recomputed from the already-persisted unlocked-achievement set every
 * time, so there is nothing to migrate when the level scale itself changes.
 *
 * @property level          1-based level number.
 * @property titleRes       French title for this level (e.g. "Randonneur").
 * @property totalXp        Lifetime XP total this level was derived from.
 * @property xpIntoLevel    XP earned since the start of the current level.
 * @property xpForNextLevel XP span of the current level (distance to the next one), or `null` at
 *                          the max level.
 * @property xpRemaining    XP still needed to reach the next level, or `null` at the max level.
 * @property fraction       0f..1f progress bar towards the next level; 1f at the max level.
 * @property isMaxLevel     Whether this is the highest level in [Levels].
 */
data class LevelInfo(
    val level: Int,
    @StringRes val titleRes: Int,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int?,
    val xpRemaining: Int?,
    val fraction: Float,
    val isMaxLevel: Boolean,
)

/**
 * One entry of the level scale, for screens that display it in full (see [Levels.scale]).
 *
 * @property level    1-based level number.
 * @property titleRes Level title.
 * @property minXp    Lifetime XP at which this level starts.
 */
data class LevelScaleEntry(
    val level: Int,
    @StringRes val titleRes: Int,
    val minXp: Int,
)

/**
 * The level scale: an ordered list of XP bands, each starting at [Band.minXp]. [forXp] picks the
 * highest band the total XP has reached.
 */
object Levels {

    private data class Band(val minXp: Int, @StringRes val titleRes: Int)

    private val BANDS = listOf(
        Band(0, R.string.level_title_1),
        Band(50, R.string.level_title_2),
        Band(130, R.string.level_title_3),
        Band(250, R.string.level_title_4),
        Band(410, R.string.level_title_5),
        Band(610, R.string.level_title_6),
        Band(850, R.string.level_title_7),
        Band(1130, R.string.level_title_8),
        Band(1450, R.string.level_title_9),
        Band(1850, R.string.level_title_10),
    )

    /** The full level scale, e.g. for a screen listing every level (see [LevelScaleEntry]). */
    val scale: List<LevelScaleEntry> = BANDS.mapIndexed { index, band ->
        LevelScaleEntry(level = index + 1, titleRes = band.titleRes, minXp = band.minXp)
    }

    /** Derives the [LevelInfo] for a lifetime total of [totalXp]. */
    fun forXp(totalXp: Int): LevelInfo {
        val index = BANDS.indexOfLast { totalXp >= it.minXp }.coerceAtLeast(0)
        val band = BANDS[index]
        val nextBand = BANDS.getOrNull(index + 1)
        val xpIntoLevel = totalXp - band.minXp
        val xpForNextLevel = nextBand?.let { it.minXp - band.minXp }
        val xpRemaining = nextBand?.let { it.minXp - totalXp }
        val fraction = if (xpForNextLevel == null || xpForNextLevel <= 0) {
            1f
        } else {
            (xpIntoLevel.toFloat() / xpForNextLevel).coerceIn(0f, 1f)
        }
        return LevelInfo(
            level = index + 1,
            titleRes = band.titleRes,
            totalXp = totalXp,
            xpIntoLevel = xpIntoLevel,
            xpForNextLevel = xpForNextLevel,
            xpRemaining = xpRemaining,
            fraction = fraction,
            isMaxLevel = nextBand == null,
        )
    }
}
