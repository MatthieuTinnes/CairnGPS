package app.matthieu.cairngps.ui.gamification

/** One catalog entry paired with its unlock date, or `null` while still locked. */
data class AchievementItem(
    val def: AchievementDef,
    val unlockedAt: Long?,
) {
    val isUnlocked: Boolean get() = unlockedAt != null
}

/** One [AchievementFamily] section of the Succès screen: its paliers and progress bar. */
data class AchievementFamilySection(
    val family: AchievementFamily,
    val items: List<AchievementItem>,
    val progress: FamilyProgress?,
)

/**
 * State of the Succès screen.
 *
 * @property families One section per [AchievementFamily], in catalog order, or `null` while the
 *                     first combined load (achievements + records + sessions) is still in flight.
 */
data class AchievementsUiState(
    val families: List<AchievementFamilySection>? = null,
)
