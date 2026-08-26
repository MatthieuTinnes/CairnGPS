package app.matthieu.cairngps.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import app.matthieu.cairngps.ui.navigation.MainScaffold

/** Zoomed in past the globe's normal 1x default, so its spotlighted satellites read clearly. */
private const val ONBOARDING_GLOBE_ZOOM = 2.5f

/**
 * Drives the first-launch onboarding tour: renders the app's real screens ([MainScaffold]) against
 * an [OnboardingContainer] seeded with synthetic data, and layers [OnboardingOverlay] on top to
 * script the walk through [OnboardingSteps] step by step.
 *
 * Owns a [OnboardingContainer] and [androidx.navigation.NavHostController] of its own — entirely
 * separate from the real app's — so the tour can be entered and dismissed (from Settings' replay
 * action) without ever touching the user's real data or navigation state.
 */
@Composable
fun OnboardingHost(onFinish: () -> Unit) {
    val context = LocalContext.current
    val container = remember { OnboardingContainer(context) }
    DisposableEffect(container) {
        onDispose { container.close() }
    }
    LaunchedEffect(container) { container.seed() }

    val navController = rememberNavController()
    val targets = remember { OnboardingTargets() }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = OnboardingSteps[stepIndex]

    // Fires only when the *route* actually changes between consecutive steps — several consecutive
    // steps share a screen (e.g. every Position step), and re-navigating to an already-displayed
    // route would just push a wasteful duplicate entry. The very first composition is a no-op:
    // the NavHost already starts on the first step's route.
    var hasNavigatedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(step.route) {
        if (!hasNavigatedOnce) {
            hasNavigatedOnce = true
            return@LaunchedEffect
        }
        navController.navigate(step.route) { launchSingleTop = true }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalOnboardingTargets provides targets) {
            MainScaffold(
                container = container,
                navController = navController,
                showBanners = false,
                satelliteGlobeInitialZoom = ONBOARDING_GLOBE_ZOOM,
            )
        }
        OnboardingOverlay(
            step = step,
            stepIndex = stepIndex,
            totalSteps = OnboardingSteps.size,
            targetRect = targets.boundsOf(step.target),
            onNext = {
                if (stepIndex < OnboardingSteps.lastIndex) stepIndex++ else onFinish()
            },
            onSkip = onFinish,
            onBack = {
                if (stepIndex > 0) stepIndex-- else onFinish()
            },
        )
    }
}
