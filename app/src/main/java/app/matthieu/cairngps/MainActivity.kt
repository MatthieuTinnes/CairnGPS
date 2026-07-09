package app.matthieu.cairngps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.matthieu.cairngps.ui.location.HomeRoute
import app.matthieu.cairngps.ui.permission.LocationPermissionGate
import app.matthieu.cairngps.ui.theme.CairnGpsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = (application as CairnApplication).locationRepository

        setContent {
            CairnGpsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LocationPermissionGate {
                        HomeRoute(repository = repository)
                    }
                }
            }
        }
    }
}
