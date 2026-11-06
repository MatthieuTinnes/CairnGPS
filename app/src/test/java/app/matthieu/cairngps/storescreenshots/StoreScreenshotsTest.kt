package app.matthieu.cairngps.storescreenshots

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.ui.navigation.MainScaffold
import app.matthieu.cairngps.ui.navigation.Routes
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.util.Locale

/**
 * Writes the Play Store / F-Droid screenshots and feature graphic straight into
 * `fastlane/metadata/android`, rendering the app's real screens on synthetic data inside frames
 * styled after the website. Excluded from `test`; run it with
 * `./gradlew :app:testDebugUnitTest -PstoreScreenshots` (see docs/store-screenshots.md).
 *
 * One single test on purpose: the settings DataStore is a process-wide singleton, so every
 * locale and device shares one [StoreContainer] instead of each test building its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var container: StoreContainer

    private val record = RoborazziOptions(taskType = RoborazziTaskType.Record)
    private val metadataDir = File("../fastlane/metadata/android").canonicalFile

    @After
    fun tearDown() {
        if (::container.isInitialized) container.close()
    }

    @Test
    fun generate() {
        check(metadataDir.isDirectory) { "fastlane metadata not found at $metadataDir" }
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        container = StoreContainer(app, clockMs = DEMO_INSTANT_MS)
        runBlocking {
            container.settingsRepository.setGnssInterferenceDetection(false)
            container.seed()
        }
        // GamificationManager derives records and achievements from the seeded history on its
        // own; give it time before the first capture.
        settle(3_000)

        val sessions = runBlocking { container.sessionRepository.sessions().first() }
        val waypoints = runBlocking { container.waypointRepository.waypoints().first() }
        val showcase = sessions.showcase()
        container.navigationTargetRepository.setTarget(waypoints.navigationTarget().id)

        val fonts = loadFonts()
        val icon = loadIcon()

        for (locale in StoreLocale.entries) {
            for (target in DeviceTarget.entries) {
                render(locale, target.qualifiers) { set ->
                    storeFrames(locale.copy, showcase.id).forEachIndexed { index, frame ->
                        container.spoofed = frame.spoofed
                        if (frame.spoofed) awaitSpoofingAlert()
                        set { frame.render(fonts, icon, target.device) }
                        settle(frame.settleMs)
                        capture(File(metadataDir, "${locale.folder}/images/${target.folder}/${index + 1}.png"))
                        if (frame.spoofed) runBlocking { container.settingsRepository.setGnssInterferenceDetection(false) }
                    }
                }
            }
            render(locale, BANNER_QUALIFIERS) { set ->
                set {
                    BannerFrame(
                        copy = locale.copy.banner,
                        fonts = fonts,
                        icon = icon,
                        screens = listOf({ AppScreen(container) }, { AppScreen(container, Routes.SATELLITE_GLOBE) }),
                        darkScreens = listOf(false, true),
                    )
                }
                settle(2_500)
                capture(File(metadataDir, "${locale.folder}/images/featureGraphic.png"))
            }
        }
    }

    /**
     * Turns the detection on and waits for its spoofing verdict before the frame is shown. The
     * alert must already be there on the screen's first composition: arriving later, it would be
     * inserted above the sky plot, which the list keeps anchored at the top, i.e. off screen.
     * The shared detector outlives this collector by a few seconds, enough for the screen to
     * pick the verdict up from its replay.
     */
    @SuppressLint("MissingPermission")
    private fun awaitSpoofingAlert() = runBlocking {
        container.settingsRepository.setGnssInterferenceDetection(true)
        withTimeout(15_000) {
            container.gnssInterferenceRepository.status().first { it?.isAlert == true }
        }
    }

    /** A frame of the series, with what it needs set up before its capture. */
    private class StoreFrameSpec(
        val spoofed: Boolean = false,
        val settleMs: Long = 2_500,
        val render: @Composable (StoreFonts, ImageBitmap, DeviceKind) -> Unit,
    )

    private fun storeFrames(copy: StoreCopy, showcaseSessionId: Long): List<StoreFrameSpec> = listOf(
        feature(copy.position, Backdrop.FOREST, Contours(0.02f, 0.30f), Screen(Routes.HOME, dark = false)),
        feature(
            copy.satellites, Backdrop.CREAM, Contours(0.95f, 0.62f),
            Screen(Routes.SATELLITE_GLOBE, dark = true), Screen(Routes.SATELLITES, dark = false),
        ),
        feature(
            copy.tracks, Backdrop.FOREST, Contours(0.08f, 0.78f),
            Screen(Routes.sessionDetail(showcaseSessionId), dark = false), Screen(Routes.HISTORY, dark = false),
        ),
        feature(copy.compass, Backdrop.CREAM, Contours(0.92f, 0.22f), Screen(Routes.COMPASS, dark = true)),
        feature(copy.achievements, Backdrop.FOREST, Contours(0.95f, 0.72f), Screen(Routes.achievements(), dark = false)),
        feature(
            copy.interference, Backdrop.CREAM, Contours(0.05f, 0.45f), Screen(Routes.SATELLITES, dark = true),
            spoofed = true,
        ),
        StoreFrameSpec { fonts, icon, _ -> ClosingFrame(copy.closing, fonts, icon) },
    )

    private data class Screen(val route: String, val dark: Boolean)

    private fun feature(
        copy: FeatureCopy,
        backdrop: Backdrop,
        contours: Contours,
        vararg screens: Screen,
        spoofed: Boolean = false,
        settleMs: Long = 2_500,
    ) = StoreFrameSpec(spoofed = spoofed, settleMs = settleMs) { fonts, _, device ->
        // Tablets get a single, larger device: two overlapping tablets don't fit the canvas.
        val shown = if (device == DeviceKind.TABLET) screens.take(1) else screens.toList()
        val slots = when {
            device == DeviceKind.TABLET -> listOf(DeviceSlot(x = 22f, top = 252f, width = 316f))
            shown.size == 1 -> listOf(DeviceSlot(x = 46f, top = 262f, width = 268f))
            else -> listOf(DeviceSlot(x = 12f, top = 262f, width = 192f), DeviceSlot(x = 158f, top = 314f, width = 192f))
        }
        FeatureFrame(
            copy = copy,
            backdrop = backdrop,
            contours = contours,
            fonts = fonts,
            device = device,
            slots = slots,
            screens = shown.map { screen -> @Composable { AppScreen(container, screen.route) } },
            darkScreens = shown.map { it.dark },
        )
    }

    /**
     * Launches a fresh activity under [qualifiers] (locale, size, density) and hands [block] a
     * setter that swaps the whole content, so every frame starts from a new composition: new
     * navigation stacks, new view models.
     */
    private fun render(locale: StoreLocale, qualifiers: String, block: ((@Composable () -> Unit) -> Unit) -> Unit) {
        RuntimeEnvironment.setQualifiers("${locale.qualifier}-$qualifiers")
        Locale.setDefault(locale.locale)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var current by mutableStateOf<Pair<Int, @Composable () -> Unit>?>(null)
            scenario.onActivity { activity ->
                activity.setContent { current?.let { (id, content) -> key(id) { content() } } }
            }
            var counter = 0
            block { content ->
                scenario.onActivity { current = ++counter to content }
            }
        }
    }

    private fun capture(file: File) {
        file.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(file.path, record)
    }

    /**
     * Lets real time pass for the simulated GPS flows (which tick on Dispatchers.Default) while
     * advancing the main looper and Compose clock for everything else.
     */
    private fun settle(ms: Long) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            Thread.sleep(STEP_MS)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(STEP_MS))
            compose.waitForIdle()
        }
    }

    private fun loadFonts(): StoreFonts {
        fun font(name: String, weight: FontWeight, style: FontStyle = FontStyle.Normal): Font {
            val file = File.createTempFile(name, ".ttf").apply { deleteOnExit() }
            checkNotNull(javaClass.getResourceAsStream("/store-fonts/$name.ttf")) { "Missing font $name" }
                .use { input -> file.outputStream().use(input::copyTo) }
            return Font(file, weight, style)
        }
        return StoreFonts(
            serif = FontFamily(
                font("fraunces_medium", FontWeight.Medium),
                font("fraunces_medium_italic", FontWeight.Medium, FontStyle.Italic),
            ),
            sans = FontFamily(
                font("manrope_regular", FontWeight.Normal),
                font("manrope_semibold", FontWeight.SemiBold),
            ),
            mono = FontFamily(
                Font(R.font.roboto_mono_regular, FontWeight.Normal),
                Font(R.font.roboto_mono_medium, FontWeight.Medium),
            ),
        )
    }

    private fun loadIcon(): ImageBitmap =
        BitmapFactory.decodeFile(File(metadataDir, "en-US/images/icon.png").path).asImageBitmap()

    private enum class StoreLocale(val qualifier: String, val locale: Locale, val folder: String, val copy: StoreCopy) {
        FRENCH("fr-rFR", Locale.FRANCE, "fr-FR", FrenchCopy),
        ENGLISH("en-rUS", Locale.US, "en-US", EnglishCopy),
    }

    /** Canvas sizes: 1080 x 2160, 1200 x 1920 and 1600 x 2560 — all within Play's 2:1 limit. */
    private enum class DeviceTarget(val folder: String, val qualifiers: String, val device: DeviceKind) {
        PHONE("phoneScreenshots", "w360dp-h720dp-notnight-xxhdpi", DeviceKind.PHONE),
        SEVEN_INCH("sevenInchScreenshots", "w400dp-h640dp-notnight-xxhdpi", DeviceKind.TABLET),
        TEN_INCH("tenInchScreenshots", "w400dp-h640dp-notnight-xxxhdpi", DeviceKind.TABLET),
    }

    private companion object {
        /** An arbitrary fixed instant for the simulated walk and sky: 2026-06-15, 10:40 UTC. */
        const val DEMO_INSTANT_MS = 1_781_520_000_000L
        const val STEP_MS = 50L
        const val BANNER_QUALIFIERS = "w512dp-h250dp-notnight-xhdpi"
    }
}

/** One of the app's real screens, on its own navigation stack, opened straight at [route]. */
@Composable
private fun AppScreen(container: StoreContainer, route: String? = null) {
    val navController = rememberNavController()
    MainScaffold(
        container = container,
        navController = navController,
        showBanners = false,
        satelliteGlobeInitialZoom = 2.6f,
    )
    LaunchedEffect(route) {
        if (route == null) return@LaunchedEffect
        withFrameNanos { }
        navController.navigate(route)
    }
}

/** The session with the most climb: the most telling altitude profile for the Traces frame. */
private fun List<Session>.showcase(): Session = maxBy { it.elevationGain }

/**
 * The waypoint closest to the simulated walk (DemoGpsSource's centre), so the compass shows a
 * walkable distance rather than one to another massif hundreds of kilometres away.
 */
private fun List<Waypoint>.navigationTarget(): Waypoint =
    minBy { (it.latitude - 45.0195) * (it.latitude - 45.0195) + (it.longitude - 6.4062) * (it.longitude - 6.4062) }
