package app.matthieu.cairngps.storescreenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.ui.theme.CairnGpsTheme
import java.util.Locale

/** The website's palette (cairngps-website, assets/css/styles.css). */
object SitePalette {
    val Forest = Color(0xFF0D3B30)
    val ForestRule = Color(0xFF2E5A4C)
    val OnForest = Color(0xFFF3EBDD)
    val OnForestMuted = Color(0xFFCAD6CC)
    val OnForestFaint = Color(0xFFB9CDBF)
    val Peach = Color(0xFFFFA36B)
    val Cream = Color(0xFFF3EBDD)
    val Rule = Color(0xFFC9BEA8)
    val Ink = Color(0xFF0D3B30)
    val Muted = Color(0xFF4D5F57)
    val Clay = Color(0xFFC2490F)
    val Bezel = Color(0xFF121A16)
}

data class StoreFonts(val serif: FontFamily, val sans: FontFamily, val mono: FontFamily)

enum class Backdrop(
    val background: Color,
    val text: Color,
    val muted: Color,
    val eyebrow: Color,
    val accent: Color,
    val contour: Color,
) {
    FOREST(
        SitePalette.Forest, SitePalette.OnForest, SitePalette.OnForestMuted,
        SitePalette.OnForestFaint, SitePalette.Peach, SitePalette.OnForest.copy(alpha = 0.08f),
    ),
    CREAM(
        SitePalette.Cream, SitePalette.Ink, SitePalette.Muted,
        SitePalette.Clay, SitePalette.Clay, SitePalette.Ink.copy(alpha = 0.07f),
    ),
}

/**
 * A device the real screens are rendered in. [screenWidthDp] x [screenHeightDp] is the logical
 * size the app lays itself out at; the device is then drawn at whatever width the frame gives it.
 */
enum class DeviceKind(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val bezelDp: Int,
    val outerRadiusDp: Int,
    val innerRadiusDp: Int,
    val punchHole: Boolean,
) {
    PHONE(360, 800, 12, 56, 46, punchHole = true),
    TABLET(720, 1152, 22, 48, 28, punchHole = false),
}

/** Frame size unit: 1/360 of the canvas width, so the layout scales with the canvas. */
@JvmInline
value class Unit360(val dpPerUnit: Float) {
    operator fun invoke(units: Number): Dp = (units.toFloat() * dpPerUnit).dp
    fun sp(units: Number): TextUnit = (units.toFloat() * dpPerUnit).sp
}

/** Where the topographic rings sit, as a fraction of the canvas, and how big they are. */
data class Contours(val centerX: Float, val centerY: Float, val size: Float = 1.25f)

/** One device of a frame: its horizontal position and width in [Unit360], its top in units. */
data class DeviceSlot(val x: Float, val top: Float, val width: Float)

@Composable
fun FeatureFrame(
    copy: FeatureCopy,
    backdrop: Backdrop,
    contours: Contours,
    fonts: StoreFonts,
    device: DeviceKind,
    slots: List<DeviceSlot>,
    screens: List<@Composable () -> Unit>,
    darkScreens: List<Boolean>,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop.background)
            .clipToBounds(),
    ) {
        val u = Unit360(maxWidth.value / 360f)
        ContourRings(contours, backdrop.contour)
        Column(modifier = Modifier.padding(start = u(30), end = u(26), top = u(50))) {
            Eyebrow(copy.eyebrow, backdrop.eyebrow, fonts, u)
            Spacer(Modifier.height(u(16)))
            HeadlineText(
                headline = copy.headline,
                color = backdrop.text,
                accent = backdrop.accent,
                fonts = fonts,
                fontSize = u.sp(35),
                lineHeight = u.sp(37),
            )
            Spacer(Modifier.height(u(16)))
            Text(
                text = copy.subtitle,
                color = backdrop.muted,
                style = TextStyle(fontFamily = fonts.sans, fontSize = u.sp(15), lineHeight = u.sp(21)),
            )
        }
        // Later slots are drawn first so the first device overlaps the second, like the
        // website's phone pairs.
        slots.indices.reversed().forEach { index ->
            val slot = slots[index]
            DeviceFrame(
                kind = device,
                dark = darkScreens[index],
                modifier = Modifier
                    .offset(x = u(slot.x), y = u(slot.top))
                    .width(u(slot.width))
                    .wrapContentHeight(align = Alignment.Top, unbounded = true),
                content = screens[index],
            )
        }
    }
}

@Composable
fun ClosingFrame(copy: ClosingCopy, fonts: StoreFonts, icon: ImageBitmap) {
    val backdrop = Backdrop.FOREST
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop.background)
            .clipToBounds(),
    ) {
        val u = Unit360(maxWidth.value / 360f)
        ContourRings(Contours(centerX = 0.5f, centerY = 0.55f, size = 1.5f), backdrop.contour)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = u(36)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(u(96))
                    .shadow(u(18), RoundedCornerShape(u(24)))
                    .clip(RoundedCornerShape(u(24))),
            )
            Spacer(Modifier.height(u(30)))
            HeadlineText(
                headline = copy.headline,
                color = backdrop.text,
                accent = backdrop.accent,
                fonts = fonts,
                fontSize = u.sp(40),
                lineHeight = u.sp(42),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(u(36)))
            Column(modifier = Modifier.fillMaxWidth()) {
                copy.facts.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .topRule(color = SitePalette.ForestRule, width = u(1)),
                    ) {
                        row.forEach { fact ->
                            Column(modifier = Modifier.weight(1f).padding(vertical = u(18))) {
                                Text(
                                    text = fact.value,
                                    color = backdrop.text,
                                    style = TextStyle(fontFamily = fonts.serif, fontSize = u.sp(44), lineHeight = u.sp(46)),
                                )
                                Spacer(Modifier.height(u(4)))
                                Text(
                                    text = fact.label,
                                    color = backdrop.muted,
                                    style = TextStyle(fontFamily = fonts.sans, fontSize = u.sp(13), lineHeight = u.sp(18)),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(u(34)))
            Text(
                text = copy.footer.uppercase(Locale.ROOT),
                color = backdrop.eyebrow,
                style = TextStyle(fontFamily = fonts.mono, fontSize = u.sp(12), letterSpacing = 0.08.em),
            )
        }
    }
}

/**
 * The Play Store feature graphic (1024 x 500). Laid out in dp on a 512 x 250 dp canvas rendered
 * at 2x.
 */
@Composable
fun BannerFrame(
    copy: BannerCopy,
    fonts: StoreFonts,
    icon: ImageBitmap,
    screens: List<@Composable () -> Unit>,
    darkScreens: List<Boolean>,
) {
    val backdrop = Backdrop.FOREST
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backdrop.background)
            .clipToBounds(),
    ) {
        ContourRings(Contours(centerX = 0.12f, centerY = 0.95f, size = 1.1f), backdrop.contour)
        Column(modifier = Modifier.padding(start = 34.dp, top = 40.dp).width(268.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "CairnGPS",
                    color = backdrop.text,
                    style = TextStyle(fontFamily = fonts.serif, fontSize = 21.sp),
                )
            }
            Spacer(Modifier.height(20.dp))
            HeadlineText(
                headline = copy.headline,
                color = backdrop.text,
                accent = backdrop.accent,
                fonts = fonts,
                fontSize = 33.sp,
                lineHeight = 35.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = copy.tagline.uppercase(Locale.ROOT),
                color = backdrop.eyebrow,
                style = TextStyle(fontFamily = fonts.mono, fontSize = 10.sp, letterSpacing = 0.08.em),
            )
        }
        val slots = listOf(DeviceSlot(x = 322f, top = 30f, width = 104f), DeviceSlot(x = 398f, top = 62f, width = 104f))
        slots.indices.reversed().forEach { index ->
            val slot = slots[index]
            DeviceFrame(
                kind = DeviceKind.PHONE,
                dark = darkScreens[index],
                modifier = Modifier
                    .offset(x = slot.x.dp, y = slot.top.dp)
                    .width(slot.width.dp)
                    .wrapContentHeight(align = Alignment.Top, unbounded = true),
                content = screens[index],
            )
        }
    }
}

@Composable
private fun Eyebrow(text: String, color: Color, fonts: StoreFonts, u: Unit360) {
    Text(
        text = text.uppercase(Locale.ROOT),
        color = color,
        style = TextStyle(
            fontFamily = fonts.mono,
            fontWeight = FontWeight.Medium,
            fontSize = u.sp(12.5),
            letterSpacing = 0.08.em,
        ),
    )
}

@Composable
private fun HeadlineText(
    headline: Headline,
    color: Color,
    accent: Color,
    fonts: StoreFonts,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = headline.annotated(accent),
        color = color,
        style = TextStyle(
            fontFamily = fonts.serif,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = (-0.015).em,
            textAlign = textAlign,
        ),
    )
}

private fun Headline.annotated(accentColor: Color): AnnotatedString = buildAnnotatedString {
    val accentStyle = SpanStyle(fontStyle = FontStyle.Italic, color = accentColor)
    if (accentFirst) {
        withStyle(accentStyle) { append(accent) }
        append(plain)
    } else {
        append(plain)
        withStyle(accentStyle) { append(accent) }
    }
}

private fun Modifier.topRule(color: Color, width: Dp): Modifier = drawBehind {
    drawRect(color = color, size = Size(size.width, width.toPx()))
}

/**
 * The website hero's topographic rings: one closed blob drawn seven times, each smaller, shifted
 * and rotated a little more than the previous (same transforms as the site's SVG).
 */
@Composable
private fun ContourRings(contours: Contours, color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val blob = Path().apply {
            moveTo(0f, -200f)
            cubicTo(120f, -210f, 215f, -130f, 208f, -20f)
            cubicTo(202f, 90f, 150f, 185f, 20f, 196f)
            cubicTo(-110f, 207f, -220f, 140f, -210f, 10f)
            cubicTo(-200f, -115f, -120f, -192f, 0f, -200f)
            close()
        }
        // The site's viewBox is 700 wide; map it to [Contours.size] canvas widths.
        val k = size.width * contours.size / 700f
        val strokePx = size.width / 360f * 1.3f
        translate(left = size.width * contours.centerX, top = size.height * contours.centerY) {
            RINGS.forEach { ring ->
                translate(left = ring.dx * k, top = ring.dy * k) {
                    rotate(degrees = ring.rotation, pivot = Offset.Zero) {
                        scale(scale = ring.scale * k, pivot = Offset.Zero) {
                            drawPath(blob, color, style = Stroke(width = strokePx / (ring.scale * k)))
                        }
                    }
                }
            }
        }
    }
}

private data class Ring(val dx: Float, val dy: Float, val scale: Float, val rotation: Float)

private val RINGS = listOf(
    Ring(0f, 0f, 1.6f, 8f),
    Ring(8f, -6f, 1.4f, 14f),
    Ring(16f, -12f, 1.2f, 22f),
    Ring(26f, -18f, 1.0f, 30f),
    Ring(36f, -24f, 0.8f, 40f),
    Ring(46f, -30f, 0.6f, 52f),
    Ring(54f, -36f, 0.4f, 64f),
)

/**
 * A plain Android device drawn around a real app screen. The screen is laid out at the device's
 * logical size and rendered at a reduced density rather than scaled as a bitmap, so text and
 * strokes stay sharp at any frame size.
 */
@Composable
fun DeviceFrame(
    kind: DeviceKind,
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        // Bezel and radii are expressed in the device's own logical dp, so they scale with it.
        val totalWidthDp = kind.screenWidthDp + 2 * kind.bezelDp
        val scale = maxWidth.value / totalWidthDp
        val deviceDensity = Density(density.density * scale, fontScale = 1f)
        CompositionLocalProvider(LocalDensity provides deviceDensity) {
            val outer = RoundedCornerShape(kind.outerRadiusDp.dp)
            val inner = RoundedCornerShape(kind.innerRadiusDp.dp)
            Box(
                modifier = Modifier
                    .shadow(elevation = 60.dp, shape = outer, spotColor = SitePalette.Forest, ambientColor = SitePalette.Forest)
                    .background(SitePalette.Bezel, outer)
                    .border(3.dp, Color(0xFF2A332D), outer)
                    .padding(kind.bezelDp.dp),
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(kind.screenWidthDp.dp, kind.screenHeightDp.dp)
                        .clip(inner),
                ) {
                    CairnGpsTheme(darkTheme = dark) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Column {
                                FakeStatusBar(punchHole = kind.punchHole)
                                Box(modifier = Modifier.weight(1f)) { content() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A neutral status bar: fixed 12:00, full signal and battery, no notification icons. */
@Composable
private fun FakeStatusBar(punchHole: Boolean) {
    val color = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 24.dp),
    ) {
        Text(
            text = "12:00",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        if (punchHole) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(14.dp)
                    .background(Color.Black, CircleShape),
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 52.dp, height = 14.dp),
        ) {
            val unit = size.height / 14f
            // Signal: four rising bars.
            for (i in 0 until 4) {
                val barHeight = (5 + i * 3) * unit
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * 4.5f * unit, size.height - barHeight),
                    size = Size(3f * unit, barHeight),
                    cornerRadius = CornerRadius(unit),
                )
            }
            // Battery: outline, full fill and terminal nub.
            val left = 26f * unit
            val bodyWidth = 22f * unit
            val top = 2f * unit
            val bodyHeight = 11f * unit
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(3f * unit),
                style = Stroke(width = 1.4f * unit),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(left + 2f * unit, top + 2f * unit),
                size = Size(bodyWidth - 4f * unit, bodyHeight - 4f * unit),
                cornerRadius = CornerRadius(1.5f * unit),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(left + bodyWidth + 1f * unit, top + 3.5f * unit),
                size = Size(2f * unit, 4f * unit),
                cornerRadius = CornerRadius(unit),
            )
        }
    }
}
