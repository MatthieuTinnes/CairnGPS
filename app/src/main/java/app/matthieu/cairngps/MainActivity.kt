package app.matthieu.cairngps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.ThemeMode
import app.matthieu.cairngps.ui.compass.CompassRoute
import app.matthieu.cairngps.ui.gamification.AchievementsRoute
import app.matthieu.cairngps.ui.gamification.RecordsRoute
import app.matthieu.cairngps.ui.gamification.UnlockBanner
import app.matthieu.cairngps.ui.history.HistoryRoute
import app.matthieu.cairngps.ui.history.SessionDetailRoute
import app.matthieu.cairngps.ui.location.HomeRoute
import app.matthieu.cairngps.ui.permission.LocationPermissionGate
import app.matthieu.cairngps.ui.satellites.ConstellationInfoScreen
import app.matthieu.cairngps.ui.satellites.SatellitesRoute
import app.matthieu.cairngps.ui.settings.SettingsRoute
import app.matthieu.cairngps.ui.theme.CairnGpsTheme
import app.matthieu.cairngps.ui.waypoints.WaypointDetailRoute

private object Routes {
    const val HOME = "home"
    const val COMPASS = "compass"
    const val SATELLITES = "satellites"
    const val HISTORY = "history"
    const val ACHIEVEMENTS = "achievements"
    const val RECORDS = "records"
    const val CONSTELLATION_INFO = "constellation_info"
    const val SETTINGS = "settings"

    const val WAYPOINT_ID_ARG = "waypointId"
    const val WAYPOINT_DETAIL = "waypoint_detail/{$WAYPOINT_ID_ARG}"
    fun waypointDetail(id: Long): String = "waypoint_detail/$id"

    const val SESSION_ID_ARG = "sessionId"
    const val SESSION_DETAIL = "session_detail/{$SESSION_ID_ARG}"
    fun sessionDetail(id: Long): String = "session_detail/$id"
}

/**
 * Top-level destinations reachable from the bottom navigation bar. Icons use text glyphs to avoid
 * pulling in the large material-icons-extended artifact (same choice as the rest of the UI).
 */
private enum class TopLevelTab(
    val route: String,
    val glyph: String,
    @StringRes val labelRes: Int,
) {
    HOME(Routes.HOME, "📍", R.string.tab_home),
    COMPASS(Routes.COMPASS, "🧭", R.string.tab_compass),
    SATELLITES(Routes.SATELLITES, "🛰", R.string.tab_satellites),
    HISTORY(Routes.HISTORY, "🗺", R.string.tab_history),
    ACHIEVEMENTS(Routes.ACHIEVEMENTS, "🏆", R.string.tab_achievements),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CairnApplication

        setContent {
            // Reactive to preference changes so switching theme in Settings applies instantly,
            // without recreating the activity.
            val settings by app.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            CairnGpsTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LocationPermissionGate {
                        MainScaffold(app)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(app: CairnApplication) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    // The bottom bar is only shown on top-level tabs; secondary screens (Settings) take the full
    // height and rely on their own back navigation.
    val showBottomBar = TopLevelTab.entries.any { it.route == currentRoute }

    // The unlock banner is mounted here, above the NavHost, so it floats over every screen
    // (including the bottom bar) regardless of which tab triggered the achievement.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        TopLevelTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            // Standard bottom-nav behaviour: keep a single instance
                                            // per tab and preserve each tab's state across switches.
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Text(tab.glyph, style = MaterialTheme.typography.titleLarge)
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                // Offset content above the bottom bar and mark those insets consumed so the
                // per-screen Scaffolds don't add the navigation-bar inset a second time.
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                composable(Routes.HOME) {
                    HomeRoute(
                        locationRepository = app.locationRepository,
                        settingsRepository = app.settingsRepository,
                        waypointRepository = app.waypointRepository,
                        recordingRepository = app.recordingRepository,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(Routes.COMPASS) {
                    CompassRoute(
                        compassRepository = app.compassRepository,
                        locationRepository = app.locationRepository,
                        settingsRepository = app.settingsRepository,
                    )
                }
                composable(Routes.SATELLITES) {
                    SatellitesRoute(
                        locationRepository = app.locationRepository,
                        onOpenInfo = { navController.navigate(Routes.CONSTELLATION_INFO) },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryRoute(
                        waypointRepository = app.waypointRepository,
                        sessionRepository = app.sessionRepository,
                        onOpenWaypoint = { id -> navController.navigate(Routes.waypointDetail(id)) },
                        onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                    )
                }
                composable(
                    Routes.WAYPOINT_DETAIL,
                    arguments = listOf(navArgument(Routes.WAYPOINT_ID_ARG) { type = NavType.LongType }),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong(Routes.WAYPOINT_ID_ARG) ?: return@composable
                    WaypointDetailRoute(
                        waypointId = id,
                        waypointRepository = app.waypointRepository,
                        sessionRepository = app.sessionRepository,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { sessionId -> navController.navigate(Routes.sessionDetail(sessionId)) },
                    )
                }
                composable(
                    Routes.SESSION_DETAIL,
                    arguments = listOf(navArgument(Routes.SESSION_ID_ARG) { type = NavType.LongType }),
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong(Routes.SESSION_ID_ARG) ?: return@composable
                    SessionDetailRoute(
                        sessionId = id,
                        sessionRepository = app.sessionRepository,
                        waypointRepository = app.waypointRepository,
                        onBack = { navController.popBackStack() },
                        onOpenWaypoint = { waypointId -> navController.navigate(Routes.waypointDetail(waypointId)) },
                    )
                }
                composable(Routes.ACHIEVEMENTS) {
                    AchievementsRoute(
                        achievementsRepository = app.achievementsRepository,
                        recordsRepository = app.recordsRepository,
                        sessionRepository = app.sessionRepository,
                        onOpenRecords = { navController.navigate(Routes.RECORDS) },
                    )
                }
                composable(Routes.RECORDS) {
                    RecordsRoute(
                        recordsRepository = app.recordsRepository,
                        settingsRepository = app.settingsRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.CONSTELLATION_INFO) {
                    ConstellationInfoScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsRoute(
                        repository = app.settingsRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        UnlockBanner(
            unlockedEvents = app.gamificationManager.unlockedEvents,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
