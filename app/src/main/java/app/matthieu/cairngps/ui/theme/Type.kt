package app.matthieu.cairngps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.matthieu.cairngps.R

// Default Material 3 type scale; kept in its own file so screens can reference a single Typography.
val Typography = Typography()

// Tabular numeric values (coordinates, speed, altitude, stats) use Roboto Mono, matching the
// design system, instead of the device's generic system monospace.
val MonoFontFamily = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_medium, FontWeight.Medium),
)
