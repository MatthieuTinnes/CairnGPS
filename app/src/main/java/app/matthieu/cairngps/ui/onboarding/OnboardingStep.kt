package app.matthieu.cairngps.ui.onboarding

import androidx.annotation.StringRes
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.navigation.Routes

/**
 * One stop of the onboarding tour: which real screen it's shown on ([route]), which element it
 * spotlights ([target], `null` to center the bubble with no cut-out), and its copy.
 */
data class OnboardingStep(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val route: String,
    val target: OnboardingTarget? = null,
)

/**
 * The tour's fixed script, walking Position → Naviguer → Satellites → Globe 3D → Profil → Carnet →
 * Succès on the synthetic data seeded by [OnboardingContainer]. [OnboardingHost] drives its own
 * [androidx.navigation.NavHostController] to each step's [OnboardingStep.route] in turn.
 */
val OnboardingSteps: List<OnboardingStep> = listOf(
    OnboardingStep(R.string.onboarding_step_1_title, R.string.onboarding_step_1_body, Routes.HOME),
    OnboardingStep(
        R.string.onboarding_step_2_title, R.string.onboarding_step_2_body,
        Routes.HOME, OnboardingTarget.COORDINATES_CARD,
    ),
    OnboardingStep(
        R.string.onboarding_step_3_title, R.string.onboarding_step_3_body,
        Routes.HOME, OnboardingTarget.ALTITUDE_CARD,
    ),
    OnboardingStep(
        R.string.onboarding_step_4_title, R.string.onboarding_step_4_body,
        Routes.HOME, OnboardingTarget.HOME_ACTIONS,
    ),
    OnboardingStep(
        R.string.onboarding_step_5_title, R.string.onboarding_step_5_body,
        Routes.HOME, OnboardingTarget.TAB_COMPASS,
    ),
    OnboardingStep(
        R.string.onboarding_step_6_title, R.string.onboarding_step_6_body,
        Routes.COMPASS, OnboardingTarget.COMPASS_DIAL,
    ),
    OnboardingStep(
        R.string.onboarding_step_7_title, R.string.onboarding_step_7_body,
        Routes.COMPASS, OnboardingTarget.TAB_SATELLITES,
    ),
    OnboardingStep(
        R.string.onboarding_step_8_title, R.string.onboarding_step_8_body,
        Routes.SATELLITES, OnboardingTarget.SKY_PLOT,
    ),
    OnboardingStep(
        R.string.onboarding_step_9_title, R.string.onboarding_step_9_body,
        Routes.SATELLITES, OnboardingTarget.GLOBE_BUTTON,
    ),
    OnboardingStep(
        R.string.onboarding_step_10_title, R.string.onboarding_step_10_body,
        Routes.SATELLITE_GLOBE, OnboardingTarget.GLOBE_CANVAS,
    ),
    OnboardingStep(
        R.string.onboarding_step_11_title, R.string.onboarding_step_11_body,
        Routes.PROFILE, OnboardingTarget.TAB_PROFILE,
    ),
    OnboardingStep(
        R.string.onboarding_step_12_title, R.string.onboarding_step_12_body,
        Routes.PROFILE, OnboardingTarget.PROFILE_LOGBOOK,
    ),
    OnboardingStep(
        R.string.onboarding_step_13_title, R.string.onboarding_step_13_body,
        Routes.HISTORY, OnboardingTarget.LOGBOOK_TOGGLE,
    ),
    OnboardingStep(
        R.string.onboarding_step_14_title, R.string.onboarding_step_14_body,
        Routes.PROFILE, OnboardingTarget.PROFILE_ACHIEVEMENTS,
    ),
    OnboardingStep(
        R.string.onboarding_step_15_title, R.string.onboarding_step_15_body,
        Routes.achievements(), OnboardingTarget.ACHIEVEMENT_GRID,
    ),
    OnboardingStep(R.string.onboarding_step_16_title, R.string.onboarding_step_16_body, Routes.achievements()),
)
