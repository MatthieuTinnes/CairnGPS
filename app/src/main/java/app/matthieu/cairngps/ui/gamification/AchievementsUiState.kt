package app.matthieu.cairngps.ui.gamification

/** One catalog entry paired with its unlock date, or `null` while still locked. */
data class AchievementItem(
    val def: AchievementDef,
    val unlockedAt: Long?,
) {
    val isUnlocked: Boolean get() = unlockedAt != null
}

/** The closest-to-unlocking achievement, for the "Prochain succès" highlight card. */
data class NextAchievementUi(
    val def: AchievementDef,
    val progress: FamilyProgress,
)

/**
 * State of the Succès screen (screen 1k): a flat badge grid over the whole catalog, in catalog
 * order, plus the closest-to-unlocking achievement for the highlight card and the unlocked/total
 * counter shown in the app bar.
 *
 * @property items          One entry per [Achievements.ALL] catalog def, or `null` while the
 *                           first combined load (achievements + records + sessions + waypoints)
 *                           is still in flight.
 * @property next           The locked achievement closest to unlocking, or `null` if every
 *                           progress-tracked achievement is already unlocked.
 * @property unlockedCount   Number of unlocked achievements.
 * @property totalCount      Size of the achievement catalog (denominator for "X/Y").
 */
data class AchievementsUiState(
    val items: List<AchievementItem>? = null,
    val next: NextAchievementUi? = null,
    val unlockedCount: Int = 0,
    val totalCount: Int = Achievements.ALL.size,
)
