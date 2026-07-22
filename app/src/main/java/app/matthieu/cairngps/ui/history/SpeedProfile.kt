package app.matthieu.cairngps.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.domain.distanceAndBearing
import app.matthieu.cairngps.domain.format.formatSpeed
import app.matthieu.cairngps.domain.format.speedUnitLabel
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme

/**
 * Per-sample speed (m/s), derived from consecutive track points since [TrackPoint] itself only
 * records position — not speed. Uses [distanceAndBearing] (same geodesic as the data layer) over
 * the time delta between samples.
 *
 * Raw point-to-point speed is noisy — downsampled GPS fixes a few seconds apart turn small
 * position jitter into large speed swings — so this applies a light centered moving average
 * (window of 3) to keep the curve readable, the same spirit as the altitude profile's shape.
 */
private fun computeSmoothedSpeeds(track: List<TrackPoint>): List<Float> {
    if (track.size < 2) return List(track.size) { 0f }

    val raw = FloatArray(track.size)
    for (i in 1 until track.size) {
        val previous = track[i - 1]
        val current = track[i]
        val distanceMeters = distanceAndBearing(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude,
        ).distanceMeters
        val dtSeconds = (current.timestamp - previous.timestamp) / 1000.0
        raw[i] = if (dtSeconds > 0) (distanceMeters / dtSeconds).toFloat() else 0f
    }
    raw[0] = raw[1] // no segment before the first point; echo the first known speed

    return raw.indices.map { i ->
        val start = (i - 1).coerceAtLeast(0)
        val end = (i + 1).coerceAtMost(raw.size - 1)
        (start..end).sumOf { raw[it].toDouble() }.div(end - start + 1).toFloat()
    }
}

/**
 * Speed-over-time profile (screen 1j "PROFIL DE VITESSE"), built on top of the shared
 * [TrackMetricProfile] chart. See that composable for the cursor/sync behaviour: [selectedIndex]
 * and [onSelectedIndexChange] are what keep this in step with the altitude profile and the route
 * trace, all three driven by the same selected index.
 */
@Composable
fun SpeedProfile(
    track: List<TrackPoint>,
    unitSystem: UnitSystem,
    selectedIndex: Int?,
    onSelectedIndexChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (track.isEmpty()) return

    val light = LocalIsLightTheme.current
    val lineColor = if (light) CairnGreenDark else CairnGreen
    val speeds = remember(track) { computeSmoothedSpeeds(track) }
    val minSpeed = speeds.min()
    val maxSpeed = speeds.max()

    TrackMetricProfile(
        track = track,
        values = speeds,
        lineColor = lineColor,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
        bubbleValueLabel = { index -> "${formatSpeed(speeds[index], unitSystem)} ${speedUnitLabel(unitSystem)}" },
        minLabel = "min ${formatSpeed(minSpeed, unitSystem)} ${speedUnitLabel(unitSystem)}",
        maxLabel = "max ${formatSpeed(maxSpeed, unitSystem)} ${speedUnitLabel(unitSystem)}",
        modifier = modifier,
    )
}
