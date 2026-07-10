package app.matthieu.cairngps.ui.theme

import androidx.compose.ui.graphics.Color

// Outdoor-oriented palette: high-contrast greens and warm accents that stay readable in
// sunlight, on a near-black background to save battery on OLED screens during long hikes.
val CairnGreen = Color(0xFF7FC96B)
val CairnGreenDark = Color(0xFF2E5E24)
val CairnAmber = Color(0xFFF2B84B)
val CairnStone = Color(0xFFB9C0B4)

val DarkBackground = Color(0xFF0E1310)
val DarkSurface = Color(0xFF161C18)
val DarkOnSurface = Color(0xFFE6EAE3)

// Light equivalents (used only if the system/user forces light mode).
val LightBackground = Color(0xFFF7FAF5)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF191D17)

// Accuracy quality indicator: green < 5 m, orange 5–15 m, red > 15 m.
// Slightly desaturated so they stay comfortable on the dark background.
val QualityGood = Color(0xFF4CAF50)
val QualityMedium = Color(0xFFFFA726)
val QualityPoor = Color(0xFFE53935)
val QualityUnknown = Color(0xFF6E756B)

// One distinct hue per GNSS constellation, all bright enough to read on the dark background.
val ConstellationGps = Color(0xFF5C9CE6)
val ConstellationGlonass = Color(0xFFE57373)
val ConstellationGalileo = Color(0xFF4DB6AC)
val ConstellationBeidou = Color(0xFFFFB74D)
val ConstellationQzss = Color(0xFFBA68C8)
val ConstellationSbas = Color(0xFFA1887F)
val ConstellationIrnss = Color(0xFFFFF176)
val ConstellationUnknown = Color(0xFF6E756B)
