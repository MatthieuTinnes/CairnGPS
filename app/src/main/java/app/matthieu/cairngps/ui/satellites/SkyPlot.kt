package app.matthieu.cairngps.ui.satellites

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.SatelliteInfo
import app.matthieu.cairngps.ui.theme.CairnAmber
import app.matthieu.cairngps.ui.theme.DarkBackground
import app.matthieu.cairngps.ui.theme.LabelMuted
import app.matthieu.cairngps.ui.theme.SkyPlotFill
import app.matthieu.cairngps.ui.theme.SkyPlotInnerRing
import app.matthieu.cairngps.ui.theme.SkyPlotOuterRing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Polar azimuth/elevation plot (screen 1d "sky plot"): the horizon is the outer ring, zenith is
 * the center, one dot per tracked satellite. A filled dot means it's used in the current fix; a
 * hollow ring means it's only tracked. Uses [SatelliteInfo.azimuthDegrees]/[elevationDegrees]
 * directly — no extra geometry needed, unlike the 3D globe which projects full ECEF positions.
 */
@Composable
fun SkyPlot(satellites: List<SatelliteInfo>, modifier: Modifier = Modifier) {
    val cardinals = stringArrayResource(R.array.compass_rose_labels) // [N, E, S, O]
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f * 0.86f

        // Solid puck fill behind the whole plot, with a thin border.
        drawCircle(color = SkyPlotFill, radius = radius, center = center)
        drawCircle(color = SkyPlotOuterRing, radius = radius, center = center, style = Stroke(width = 1.dp.toPx()))

        // Elevation rings at 30°/60° up to the zenith, plus the N/S/E/O axes.
        listOf(93f / 140f, 47f / 140f).forEach { fraction ->
            drawCircle(
                color = SkyPlotInnerRing,
                radius = radius * fraction,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        drawLine(SkyPlotInnerRing, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
        drawLine(SkyPlotInnerRing, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())

        val labelMargin = 14.dp.toPx()
        cardinals.getOrNull(0)?.let { label ->
            drawText(
                textLayoutResult = textMeasurer.measure(
                    label,
                    TextStyle(color = CairnAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                ),
                topLeft = Offset(center.x - 5.dp.toPx(), center.y - radius - labelMargin),
            )
        }
        cardinals.getOrNull(1)?.let { label ->
            drawText(
                textLayoutResult = textMeasurer.measure(label, TextStyle(color = LabelMuted, fontSize = 12.sp)),
                topLeft = Offset(center.x + radius + 2.dp.toPx(), center.y - 6.dp.toPx()),
            )
        }
        cardinals.getOrNull(2)?.let { label ->
            drawText(
                textLayoutResult = textMeasurer.measure(label, TextStyle(color = LabelMuted, fontSize = 12.sp)),
                topLeft = Offset(center.x - 4.dp.toPx(), center.y + radius + labelMargin - 10.dp.toPx()),
            )
        }
        cardinals.getOrNull(3)?.let { label ->
            drawText(
                textLayoutResult = textMeasurer.measure(label, TextStyle(color = LabelMuted, fontSize = 12.sp)),
                topLeft = Offset(center.x - radius - labelMargin, center.y - 6.dp.toPx()),
            )
        }

        satellites.forEach { satellite ->
            // Elevation below the horizon shouldn't occur in a real GnssStatus snapshot, but guard
            // against a stray negative/zero reading rather than plotting it past the ring.
            val elevation = satellite.elevationDegrees.coerceIn(0f, 90f)
            val r = ((90f - elevation) / 90f) * radius
            val angleRad = Math.toRadians(satellite.azimuthDegrees.toDouble())
            val point = Offset(
                x = center.x + r * sin(angleRad).toFloat(),
                y = center.y - r * cos(angleRad).toFloat(),
            )
            val color = satellite.constellation.color()
            if (satellite.usedInFix) {
                drawCircle(color = color, radius = 6.5.dp.toPx(), center = point)
                drawCircle(color = DarkBackground, radius = 6.5.dp.toPx(), center = point, style = Stroke(width = 1.5.dp.toPx()))
            } else {
                drawCircle(color = color.copy(alpha = 0.75f), radius = 5.5.dp.toPx(), center = point, style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}
