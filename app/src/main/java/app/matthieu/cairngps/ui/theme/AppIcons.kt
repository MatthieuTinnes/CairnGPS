package app.matthieu.cairngps.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.matthieu.cairngps.R

// Material Symbols Outlined, subset at build time to only the glyphs this app uses (~40, see
// [Glyph] below) so we get real vector icons — matching the design system — without pulling in
// the multi-megabyte material-icons-extended artifact the rest of the UI historically avoided.
// The two families differ only in the variable "FILL" axis (0 = outlined, 1 = filled), matching
// how the design uses filled glyphs for active/primary state and outlined for the rest.
@OptIn(ExperimentalTextApi::class)
private val MaterialSymbolsOutlined = FontFamily(
    Font(
        R.font.material_symbols_outlined,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 0f)),
    ),
)
@OptIn(ExperimentalTextApi::class)
private val MaterialSymbolsFilled = FontFamily(
    Font(
        R.font.material_symbols_outlined,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 1f)),
    ),
)

/**
 * Codepoints for the Material Symbols glyphs bundled in `res/font/material_symbols_outlined.ttf`.
 * Rendered by codepoint (not ligature name), so the subset font doesn't need its layout tables.
 */
object Glyph {
    const val MyLocation = ''
    const val Explore = ''
    const val SatelliteAlt = ''
    const val Person = ''
    const val AddLocationAlt = ''
    const val Stop = ''
    const val PlayArrow = ''
    const val WbTwilight = ''
    const val Settings = ''
    const val EmojiEvents = ''
    const val Close = ''
    const val Check = ''
    const val Flag = ''
    const val Navigation = ''
    const val Warning = ''
    const val Public = ''
    const val Info = ''
    const val ArrowBack = ''
    const val Edit = ''
    const val Share = ''
    const val ChevronRight = ''
    const val TrendingUp = ''
    const val Map = ''
    const val MilitaryTech = ''
    const val Leaderboard = ''
    const val Cabin = ''
    const val WaterDrop = ''
    const val PhotoCamera = ''
    const val LocalParking = ''
    const val Delete = ''
    const val Landscape = ''
    const val Route = ''

    // Material Symbols maps GpsFixed to the same outline as MyLocation — not a typo.
    const val GpsFixed = ''
    const val DarkMode = ''
    const val Hiking = ''
    const val Elevation = ''
    const val Speed = ''
    const val Timer = ''
    const val LocationOff = ''
    const val Lock = ''
    const val BatterySaver = ''
}

/**
 * Renders a single Material Symbols glyph from [Glyph], matching the design's icon set.
 *
 * [contentDescription] should describe the icon's meaning for screen readers, or be left null
 * when the icon is purely decorative (e.g. sitting next to a text label that already conveys the
 * same meaning).
 */
@Composable
fun Sym(
    icon: Char,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val sizeSp = with(LocalDensity.current) { size.toSp() }
    Text(
        text = icon.toString(),
        fontFamily = if (filled) MaterialSymbolsFilled else MaterialSymbolsOutlined,
        fontSize = sizeSp,
        lineHeight = sizeSp,
        color = tint,
        textAlign = TextAlign.Center,
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
    )
}
