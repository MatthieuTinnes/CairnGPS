package app.matthieu.cairngps.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.matthieu.cairngps.CairnApplication
import app.matthieu.cairngps.R
import app.matthieu.cairngps.ui.about.AboutScreen
import app.matthieu.cairngps.ui.about.LicenseScreen
import app.matthieu.cairngps.ui.about.ThirdPartyScreen
import app.matthieu.cairngps.ui.compass.CompassRoute
import app.matthieu.cairngps.ui.gamification.AchievementsRoute
import app.matthieu.cairngps.ui.gamification.LevelsRoute
import app.matthieu.cairngps.ui.gamification.RecordsRoute
import app.matthieu.cairngps.ui.gamification.UnlockBanner
import app.matthieu.cairngps.ui.history.HistoryRoute
import app.matthieu.cairngps.ui.history.SessionDetailRoute
import app.matthieu.cairngps.ui.location.HomeRoute
import app.matthieu.cairngps.ui.profile.ProfileRoute
import app.matthieu.cairngps.ui.recording.DiscardedRecordingBanner
import app.matthieu.cairngps.ui.satellites.ConstellationInfoScreen
import app.matthieu.cairngps.ui.satellites.SatelliteGlobeRoute
import app.matthieu.cairngps.ui.satellites.SatellitesRoute
import app.matthieu.cairngps.ui.settings.SettingsRoute
import app.matthieu.cairngps.ui.theme.Glyph
import app.matthieu.cairngps.ui.theme.LightNavBar
import app.matthieu.cairngps.ui.theme.LightNavIndicator
import app.matthieu.cairngps.ui.theme.LightNavSelectedIcon
import app.matthieu.cairngps.ui.theme.LightNavSelectedText
import app.matthieu.cairngps.ui.theme.LightNavUnselected
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme
import app.matthieu.cairngps.ui.theme.Sym
import app.matthieu.cairngps.ui.waypoints.WaypointDetailRoute

private object Routes {
    const val HOME = "home"
    const val COMPASS = "compass"
    const val SATELLITES = "satellites"
    const val PROFILE = "profile"
    const val HISTORY = "history"
    const val ACHIEVEMENTS = "achievements"
    const val RECORDS = "records"
    const val LEVELS = "levels"
    const val CONSTELLATION_INFO = "constellation_info"
    const val SATELLITE_GLOBE = "satellite_globe"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val LICENSE = "license"
    const val THIRD_PARTY = "third_party"

    const val WAYPOINT_ID_ARG = "waypointId"
    const val WAYPOINT_DETAIL = "waypoint_detail/{$WAYPOINT_ID_ARG}"
    fun waypointDetail(id: Long): String = "waypoint_detail/$id"

    const val SESSION_ID_ARG = "sessionId"
    const val SESSION_DETAIL = "session_detail/{$SESSION_ID_ARG}"
    fun sessionDetail(id: Long): String = "session_detail/$id"
}

/**
 * Top-level destinations reachable from the bottom navigation bar. Icons come from the bundled
 * Material Symbols subset (see [Glyph]), matching the design system.
 *
 * Carnet/Succès/Records/Réglages are deliberately not top-level tabs: they're reached through the
 * Profil hub instead (matching the design), each carrying its own back button.
 */
private enum class TopLevelTab(
    val route: String,
    val glyph: Char,
    @StringRes val labelRes: Int,
) {
    HOME(Routes.HOME, Glyph.MyLocation, R.string.tab_home),
    COMPASS(Routes.COMPASS, Glyph.Explore, R.string.tab_compass),
    SATELLITES(Routes.SATELLITES, Glyph.SatelliteAlt, R.string.tab_satellites),
    PROFILE(Routes.PROFILE, Glyph.Person, R.string.tab_profile),
}

/**
 * The app's nav graph plus its bottom navigation bar and the floating achievement-unlock banner.
 * The only entry point [app.matthieu.cairngps.MainActivity] needs — it otherwise just owns the
 * theme and the location-permission gate.
 */
@Composable
fun MainScaffold(app: CairnApplication) {
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
                    val light = LocalIsLightTheme.current
                    NavigationBar(
                        containerColor = if (light) LightNavBar else NavigationBarDefaults.containerColor,
                    ) {
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
                                    Sym(
                                        icon = tab.glyph,
                                        contentDescription = null,
                                        filled = selected,
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (light) LightNavSelectedIcon else MaterialTheme.colorScheme.onPrimaryContainer,
                                    indicatorColor = if (light) LightNavIndicator else MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = if (light) LightNavUnselected else MaterialTheme.colorScheme.onSurfaceVariant,
                                    selectedTextColor = if (light) LightNavSelectedText else MaterialTheme.colorScheme.onSurface,
                                    unselectedTextColor = if (light) LightNavUnselected else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
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
                    )
                }
                composable(Routes.COMPASS) {
                    CompassRoute(
                        compassRepository = app.compassRepository,
                        locationRepository = app.locationRepository,
                        settingsRepository = app.settingsRepository,
                        waypointRepository = app.waypointRepository,
                        navigationTargetRepository = app.navigationTargetRepository,
                        recordingRepository = app.recordingRepository,
                    )
                }
                composable(Routes.SATELLITES) {
                    SatellitesRoute(
                        locationRepository = app.locationRepository,
                        onOpenInfo = { navController.navigate(Routes.CONSTELLATION_INFO) },
                        onOpenGlobe = { navController.navigate(Routes.SATELLITE_GLOBE) },
                    )
                }
                composable(Routes.SATELLITE_GLOBE) {
                    SatelliteGlobeRoute(
                        locationRepository = app.locationRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.PROFILE) {
                    ProfileRoute(
                        sessionRepository = app.sessionRepository,
                        achievementsRepository = app.achievementsRepository,
                        waypointRepository = app.waypointRepository,
                        settingsRepository = app.settingsRepository,
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenHistory = { navController.navigate(Routes.HISTORY) },
                        onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                        onOpenRecords = { navController.navigate(Routes.RECORDS) },
                        onOpenLevels = { navController.navigate(Routes.LEVELS) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryRoute(
                        waypointRepository = app.waypointRepository,
                        sessionRepository = app.sessionRepository,
                        settingsRepository = app.settingsRepository,
                        onOpenWaypoint = { id -> navController.navigate(Routes.waypointDetail(id)) },
                        onOpenSession = { id -> navController.navigate(Routes.sessionDetail(id)) },
                        onBack = { navController.popBackStack() },
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
                        locationRepository = app.locationRepository,
                        settingsRepository = app.settingsRepository,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { sessionId -> navController.navigate(Routes.sessionDetail(sessionId)) },
                        onNavigate = { targetId ->
                            app.navigationTargetRepository.setTarget(targetId)
                            navController.navigate(Routes.COMPASS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
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
                        settingsRepository = app.settingsRepository,
                        gamificationFlagsRepository = app.gamificationFlagsRepository,
                        onBack = { navController.popBackStack() },
                        onOpenWaypoint = { waypointId -> navController.navigate(Routes.waypointDetail(waypointId)) },
                    )
                }
                composable(Routes.ACHIEVEMENTS) {
                    AchievementsRoute(
                        achievementsRepository = app.achievementsRepository,
                        recordsRepository = app.recordsRepository,
                        sessionRepository = app.sessionRepository,
                        waypointRepository = app.waypointRepository,
                        settingsRepository = app.settingsRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.RECORDS) {
                    RecordsRoute(
                        recordsRepository = app.recordsRepository,
                        settingsRepository = app.settingsRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.LEVELS) {
                    LevelsRoute(
                        achievementsRepository = app.achievementsRepository,
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
                        backupRepository = app.backupRepository,
                        gamificationFlagsRepository = app.gamificationFlagsRepository,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLicense = { navController.navigate(Routes.LICENSE) },
                        onOpenThirdParty = { navController.navigate(Routes.THIRD_PARTY) },
                    )
                }
                composable(Routes.LICENSE) {
                    LicenseScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.THIRD_PARTY) {
                    ThirdPartyScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
        UnlockBanner(
            unlockedEvents = app.gamificationManager.unlockedEvents,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
        DiscardedRecordingBanner(
            discardedEvents = app.recordingRepository.discardedEvents,
            settingsRepository = app.settingsRepository,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}
