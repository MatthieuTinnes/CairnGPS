package app.matthieu.cairngps.ui.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.matthieu.cairngps.ui.navigation.Routes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnboardingStepsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `every step resolves a non-blank title and body`() {
        for (step in OnboardingSteps) {
            assertTrue(context.getString(step.titleRes).isNotBlank())
            assertTrue(context.getString(step.bodyRes).isNotBlank())
        }
    }

    @Test
    fun `every step's route is one MainScaffold actually declares`() {
        val knownRoutes = setOf(
            Routes.HOME,
            Routes.COMPASS,
            Routes.SATELLITES,
            Routes.SATELLITE_GLOBE,
            Routes.PROFILE,
            Routes.HISTORY,
            Routes.achievements(),
        )
        for (step in OnboardingSteps) {
            assertTrue("Unexpected route ${step.route}", step.route in knownRoutes)
        }
    }

    @Test
    fun `the tour ends on a step with no target, so the final tap can only finish it`() {
        assertFalse(OnboardingSteps.isEmpty())
        assertTrue(OnboardingSteps.last().target == null)
    }
}
