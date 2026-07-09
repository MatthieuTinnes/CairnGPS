package app.matthieu.cairngps.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = CairnGreen,
    onPrimary = DarkBackground,
    primaryContainer = CairnGreenDark,
    secondary = CairnAmber,
    tertiary = CairnStone,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
)

private val LightColors = lightColorScheme(
    primary = CairnGreenDark,
    onPrimary = LightSurface,
    secondary = CairnAmber,
    tertiary = CairnStone,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
)

/**
 * App theme. Defaults to a dark scheme — the app is meant for outdoor / hiking use where a dark,
 * low-glare, OLED-friendly UI is preferable. Pass [darkTheme] = false only to preview light mode.
 */
@Composable
fun CairnGpsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
