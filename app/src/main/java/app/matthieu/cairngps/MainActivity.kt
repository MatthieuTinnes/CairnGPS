package app.matthieu.cairngps

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.ThemeMode
import app.matthieu.cairngps.ui.navigation.MainScaffold
import app.matthieu.cairngps.ui.permission.LocationPermissionGate
import app.matthieu.cairngps.ui.theme.CairnGpsTheme

// AppCompatActivity (rather than plain ComponentActivity) is required for per-app language
// switching via AppCompatDelegate.setApplicationLocales (Settings > Language).
class MainActivity : AppCompatActivity() {

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
