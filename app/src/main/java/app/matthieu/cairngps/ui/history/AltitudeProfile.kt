package app.matthieu.cairngps.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.domain.format.formatElevation
import app.matthieu.cairngps.domain.format.shortUnitLabel
import app.matthieu.cairngps.ui.theme.CairnGreen
import app.matthieu.cairngps.ui.theme.CairnGreenDark
import app.matthieu.cairngps.ui.theme.LocalIsLightTheme

/**
 * Elevation-over-time profile (screen 1j "PROFIL D'ALTITUDE"), built on top of the shared
 * [TrackMetricProfile] chart. See that composable for the cursor/sync behaviour: [selectedIndex]
 * and [onSelectedIndexChange] are what keep this in step with the speed profile and the route
 * trace, all three driven by the same selected index.
 */
@Composable
fun AltitudeProfile(
    track: List<TrackPoint>,
    unitSystem: UnitSystem,
    selectedIndex: Int?,
    onSelectedIndexChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (track.isEmpty()) return

    val light = LocalIsLightTheme.current
    val lineColor = if (light) CairnGreenDark else CairnGreen
    val minAltitude = track.minOf { it.altitude }
    val maxAltitude = track.maxOf { it.altitude }

    TrackMetricProfile(
        track = track,
        values = track.map { it.altitude.toFloat() },
        lineColor = lineColor,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = onSelectedIndexChange,
        bubbleValueLabel = { index ->
            "${formatElevation(track[index].altitude, unitSystem)} ${shortUnitLabel(unitSystem)}"
        },
        minLabel = "min ${formatElevation(minAltitude, unitSystem)} ${shortUnitLabel(unitSystem)}",
        maxLabel = "max ${formatElevation(maxAltitude, unitSystem)} ${shortUnitLabel(unitSystem)}",
        modifier = modifier,
    )
}
