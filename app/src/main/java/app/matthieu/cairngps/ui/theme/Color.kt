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

// Light equivalents, matching the design's light-theme variant (screen 1o).
val LightBackground = Color(0xFFF4F7F1)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF141A12)

// Accuracy quality indicator: green < 5 m, orange 5–15 m, red > 15 m.
// Slightly desaturated so they stay comfortable on the dark background.
val QualityGood = Color(0xFF4CAF50)
val QualityMedium = Color(0xFFFFA726)
val QualityPoor = Color(0xFFE53935)
val QualityUnknown = Color(0xFF6E756B)

// Position screen (design 1a): muted greys for secondary/label text.
val LabelMuted = Color(0xFF8B958C)
val ValueMuted = Color(0xFF6E7A6F)

// Icon tint for the two bottom action buttons (design 1a), which use fixed brand fills rather
// than the Material color scheme. Only the icon is tinted — button text stays the default
// onSurface color in both cases, per the design.
val OnGreenButton = Color(0xFFD6F2CB)
val OnAmberButton = Color(0xFF241A05)

// Recording chip in the Position top bar.
val RecChipBg = Color(0xFF2A1715)
val RecChipBorder = Color(0xFF4A2320)
val RecChipText = Color(0xFFF0B8B4)

// Position screen, no-fix state (design 1b): big values render as dashes in these darker greys
// instead of the normal onSurface tone, and the idle "Démarrer" button swaps to a neutral fill
// with muted content color (rather than amber) since starting a recording without a fix isn't a
// meaningful action yet.
val DashText = Color(0xFF5B655C)
val DashMuted = Color(0xFF485249)
val IdleButtonBg = Color(0xFF232B25)

// Position screen, light theme (design 1o): light-theme variants of the fixed dark literals above
// (buttons, status line, recording chip), which don't adapt on their own since the mockup uses
// fixed values in both themes rather than theme-adaptive roles.
val LightStatusText = Color(0xFF525E52) // "Fix 3D · …" status line
val LightSaveButtonBg = Color(0xFFD7EBCB) // green "Marquer un repère" fill
val LightOnSaveButton = Color(0xFF2E5E24) // its icon + text
val LightStartButtonBg = Color(0xFF2E5E24) // green "Démarrer" fill
val LightOnStartButton = Color(0xFFFFFFFF) // its icon + text

// Bottom navigation (tab bar), light theme.
val LightNavBar = Color(0xFFEBF0E7)
val LightNavIndicator = Color(0xFFB7DBA4)
val LightNavSelectedIcon = Color(0xFF1E3D17)
val LightNavSelectedText = Color(0xFF1A2018)
val LightNavUnselected = Color(0xFF525E52)

// Recording chip (timer), light theme — light equivalent of RecChip* (no explicit 1o mock; derived
// to match the app's recording-red on a light surface).
val LightRecChipBg = Color(0xFFFBE4E2)
val LightRecChipBorder = Color(0xFFF3C3BE)
val LightRecChipText = Color(0xFFB3261E)

// Compass dial (design 1c): the dial's own puck fill/border and its rotating rose's tick tones —
// distinct from the general palette since the mockup uses fixed literals here rather than
// theme-adaptive roles.
val CompassDialFill = Color(0xFF121813)
val CompassDialBorder = Color(0xFF232B25)
val CompassTickMajor = Color(0xFF5B655C)
val CompassTickMinor = Color(0xFF37403A)

// Compass dial, light theme (design 5b): light equivalents of the four literals above.
val CompassDialFillLight = Color(0xFFEDF2E9)
val CompassDialBorderLight = Color(0xFFC9D2C3)
val CompassTickMajorLight = Color(0xFF6E7A6B)
val CompassTickMinorLight = Color(0xFFC9D2C3)

// Satellites sky plot (design 1d): fixed literals for the polar chart's fill/rings and the
// screen's status chip / shortcut button border — distinct from the general palette since the
// mockup uses fixed values here rather than theme-adaptive roles.
val SkyPlotFill = Color(0xFF10150F)
val SkyPlotOuterRing = Color(0xFF2A322C)
val SkyPlotInnerRing = Color(0xFF232B25)
val StatusChipBg = Color(0xFF1C231E)
val OutlineSubtle = Color(0xFF37403A)

// Satellite globe legend (design 1e): chip border — reuses design 1d's status-chip background
// (StatusChipBg) but with its own border and brighter text, per the mockup.
val GlobeLegendBorder = Color(0xFF232B25)

// Satellites sky plot, light theme (design 5c): light equivalents of the SkyPlot*/status literals
// above. LightBorderSubtle also covers 5c's signal-bar track and 5d's globe legend chip border.
val SkyPlotFillLight = Color(0xFFE9EFE5)
val SkyPlotOuterRingLight = Color(0xFF6E7A6B)
val SkyPlotRingLight = Color(0xFF5F6D5D) // inner elevation rings + N/S/E/O axes
val SkyPlotNorthLight = Color(0xFFA66A00) // darker amber "N" label, readable on the light plot
val LightBorderSubtle = Color(0xFFE1E7DE)

// Profile hub last-achievement banner (design 1g): its own amber-tinted surface, distinct from the
// general palette since the mockup uses fixed literals here rather than theme-adaptive roles.
val AchievementBannerBg = Color(0xFF2B2410)
val AchievementBannerBorder = Color(0xFF493C15)
val AchievementLabelGold = Color(0xFFC9A34A)

// Profile hub last-achievement banner, light theme (design 5f): light equivalents of the three
// literals above (subtitle text reuses LightStatusText, no new constant needed for it).
val LightAchievementBannerBg = Color(0xFFFDF3DC)
val LightAchievementBannerBorder = Color(0xFFE8CE8C)
val LightAchievementLabelGold = Color(0xFF8A6410)

// Carnet waypoint row icon circle (design 1h): a distinct muted-green background, different from
// the CairnGreenDark used for the Compass target card / Profil avatar.
val WaypointIconBg = Color(0xFF1F2A1C)

// Session waypoint-row icon circle, light theme (design 5j): light equivalent of WaypointIconBg.
val LightWaypointIconBg = Color(0xFFDCEBD3)

// Waypoint detail (design 1i): the navigate button's dark-green content color (distinct from
// OnGreenButton's lighter tint used elsewhere) and this screen's soft-red delete color (distinct
// from QualityPoor, used for live GNSS accuracy).
val OnGreenButtonDark = Color(0xFF12240E)
val SoftError = Color(0xFFE57373)

// One distinct hue per GNSS constellation, all bright enough to read on the dark background.
// GLONASS/Galileo/BeiDou/QZSS match the design system's own palette so the sky plot, 3D globe and
// constellation legend all read as the same hues; SBAS/IRNSS/UNKNOWN aren't in that design (it only
// covers the five constellations relevant to this app's markets) so they keep their prior values.
val ConstellationGps = Color(0xFF5C9CE6)
val ConstellationGlonass = Color(0xFFE06666)
val ConstellationGalileo = Color(0xFF3EC9C0)
val ConstellationBeidou = Color(0xFFF0954B)
val ConstellationQzss = Color(0xFF9B7BE0)
val ConstellationSbas = Color(0xFFA1887F)
val ConstellationIrnss = Color(0xFFFFF176)
val ConstellationUnknown = Color(0xFF6E756B)
