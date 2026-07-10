package app.matthieu.cairngps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.matthieu.cairngps.ui.location.HomeRoute
import app.matthieu.cairngps.ui.permission.LocationPermissionGate
import app.matthieu.cairngps.ui.settings.SettingsRoute
import app.matthieu.cairngps.ui.theme.CairnGpsTheme

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CairnApplication

        setContent {
            CairnGpsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LocationPermissionGate {
                        val navController = rememberNavController()
                        NavHost(navController = navController, startDestination = Routes.HOME) {
                            composable(Routes.HOME) {
                                HomeRoute(
                                    locationRepository = app.locationRepository,
                                    settingsRepository = app.settingsRepository,
                                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
                }
            }
        }
    }
}
