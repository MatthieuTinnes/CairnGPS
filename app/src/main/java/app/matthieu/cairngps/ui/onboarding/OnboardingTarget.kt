package app.matthieu.cairngps.ui.onboarding

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Every real-screen element the onboarding tour can spotlight, in no particular order. */
enum class OnboardingTarget {
    TAB_HOME,
    TAB_COMPASS,
    TAB_SATELLITES,
    TAB_PROFILE,
    COORDINATES_CARD,
    ALTITUDE_CARD,
    STATUS_LINE,
    HOME_ACTIONS,
    COMPASS_DIAL,
    COMPASS_TARGET,
    SKY_PLOT,
    GLOBE_BUTTON,
    GLOBE_CANVAS,
    PROFILE_LEVEL,
    PROFILE_LOGBOOK,
    PROFILE_ACHIEVEMENTS,
    LOGBOOK_TOGGLE,
    ACHIEVEMENT_GRID,
}

/**
 * Tracks the on-screen bounds of every tagged [OnboardingTarget], reported live by
 * [Modifier.onboardingTarget] as the real screens compose and recompose. Root-relative, matching
 * what [OnboardingOverlay] draws against.
 */
class OnboardingTargets {
    private val bounds = mutableStateMapOf<OnboardingTarget, Rect>()

    internal fun report(target: OnboardingTarget, rect: Rect) {
        bounds[target] = rect
    }

    /** The current bounds of [target], or `null` if it hasn't been composed (yet, or at all). */
    fun boundsOf(target: OnboardingTarget?): Rect? = target?.let { bounds[it] }
}

/**
 * `null` everywhere except under [OnboardingHost], so tagging a composable with
 * [Modifier.onboardingTarget] costs nothing and changes no behaviour in the real app.
 */
val LocalOnboardingTargets = compositionLocalOf<OnboardingTargets?> { null }

/**
 * Reports this composable's root-relative bounds to the active [OnboardingTargets], if any, so the
 * onboarding tour can spotlight it as [target]. A no-op outside of [OnboardingHost].
 */
fun Modifier.onboardingTarget(target: OnboardingTarget): Modifier = composed {
    val targets = LocalOnboardingTargets.current ?: return@composed this
    onGloballyPositioned { coordinates -> targets.report(target, coordinates.boundsInRoot()) }
}
