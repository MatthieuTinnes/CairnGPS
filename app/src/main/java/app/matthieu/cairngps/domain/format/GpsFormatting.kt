package app.matthieu.cairngps.domain.format

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.UnitSystem
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val METERS_TO_FEET = 3.28084
private const val KM_TO_MILES = 0.621371
private const val MPS_TO_MPH = 2.236936

/**
 * Placeholder shown for any value before the first GPS fix arrives.
 *
 * There are two placeholder conventions in the app by design, not by accident: this em dash for
 * regular-sized text (formatters, detail/records screens), and full-width digit templates like
 * `HomeScreen.DASH_COORD_DECIMAL`/`DASH_SPEED` for the large monospace readouts on the Position
 * screen, where a single short dash would visually collapse relative to the digits it replaces.
 */
const val DASH: String = "—"

/** Quality bucket for a horizontal accuracy radius, used to drive the visual indicator. */
enum class AccuracyQuality { GOOD, MEDIUM, POOR, UNKNOWN }

/** Unit label for altitude/elevation/accuracy readings: meters (metric) or feet (imperial). */
fun shortUnitLabel(unitSystem: UnitSystem): String = if (unitSystem == UnitSystem.METRIC) "m" else "ft"

/** Unit label for the main speed readout: km/h (metric) or mph (imperial). */
fun speedUnitLabel(unitSystem: UnitSystem): String = if (unitSystem == UnitSystem.METRIC) "km/h" else "mph"

/** Unit label for the secondary speed readout: m/s (metric) or ft/s (imperial). */
fun speedSecondaryUnitLabel(unitSystem: UnitSystem): String = if (unitSystem == UnitSystem.METRIC) "m/s" else "ft/s"

/** Unit label for long-distance totals: km (metric) or mi (imperial). */
fun distanceUnitLabel(unitSystem: UnitSystem): String = if (unitSystem == UnitSystem.METRIC) "km" else "mi"

/**
 * green < 5 m, orange 5–15 m, red > 15 m.
 *
 * Thresholds always compare the raw metric radius, regardless of the display unit system — this
 * gates a visual indicator, not a number shown to the user.
 */
fun accuracyQuality(accuracyMeters: Float?): AccuracyQuality = when {
    accuracyMeters == null -> AccuracyQuality.UNKNOWN
    accuracyMeters < 5f -> AccuracyQuality.GOOD
    accuracyMeters <= 15f -> AccuracyQuality.MEDIUM
    else -> AccuracyQuality.POOR
}

/**
 * Formats a coordinate, returning [DASH] when [value] is null.
 *
 * @param westLabel Localized label for the western hemisphere (only the DMS format shows it);
 * N/E/S are identical across the app's supported locales, so only West needs one. Defaults to
 * the international `"W"` for callers that haven't been updated to pass
 * `stringResource(R.string.hemisphere_west)`.
 */
fun formatCoordinate(
    value: Double?,
    isLatitude: Boolean,
    format: CoordinateFormat,
    westLabel: String = "W",
): String = when {
    value == null -> DASH
    format == CoordinateFormat.DECIMAL -> "%.6f°".format(value)
    else -> formatDms(value, isLatitude, westLabel)
}

/**
 * Converts decimal degrees to a `D°MM'SS.s"H` string.
 *
 * Computes everything in integer tenths-of-an-arc-second so that rounding never produces
 * `60"` or `60'` carry artifacts.
 */
private fun formatDms(value: Double, isLatitude: Boolean, westLabel: String): String {
    val hemisphere = when {
        isLatitude -> if (value >= 0) "N" else "S"
        else -> if (value >= 0) "E" else westLabel
    }

    val totalTenths = (value.absoluteValue * 3600.0 * 10.0).roundToLong()
    val tenths = (totalTenths % 10).toInt()
    val totalSeconds = totalTenths / 10
    val seconds = (totalSeconds % 60).toInt()
    val totalMinutes = totalSeconds / 60
    val minutes = (totalMinutes % 60).toInt()
    val degrees = (totalMinutes / 60).toInt()

    return "%d°%02d'%02d.%d\"%s".format(degrees, minutes, seconds, tenths, hemisphere)
}

/** Altitude as a whole number, meters or feet depending on [unitSystem], or [DASH]. */
fun formatAltitude(altitudeMeters: Double?, unitSystem: UnitSystem): String = altitudeMeters?.let {
    val value = if (unitSystem == UnitSystem.METRIC) it else it * METERS_TO_FEET
    value.roundToInt().toString()
} ?: DASH

/** Speed converted to km/h or mph (per [unitSystem]) with one decimal, or [DASH]. */
fun formatSpeed(speedMetersPerSecond: Float?, unitSystem: UnitSystem): String = speedMetersPerSecond?.let {
    val value = if (unitSystem == UnitSystem.METRIC) it * 3.6f else it * MPS_TO_MPH.toFloat()
    "%.1f".format(value)
} ?: DASH

/** Secondary speed readout in m/s or ft/s (per [unitSystem]) with one decimal, or [DASH]. */
fun formatSpeedSecondary(speedMetersPerSecond: Float?, unitSystem: UnitSystem): String = speedMetersPerSecond?.let {
    val value = if (unitSystem == UnitSystem.METRIC) it else it * METERS_TO_FEET.toFloat()
    "%.1f".format(value)
} ?: DASH

/** Horizontal accuracy radius in meters or feet (per [unitSystem]) with one decimal, or [DASH]. */
fun formatAccuracy(accuracyMeters: Float?, unitSystem: UnitSystem): String = accuracyMeters?.let {
    val value = if (unitSystem == UnitSystem.METRIC) it else it * METERS_TO_FEET.toFloat()
    "%.1f".format(value)
} ?: DASH

/**
 * Coordinates as plain decimal degrees for the clipboard, e.g. `47.123456, 6.123456`.
 *
 * Always [Locale.US] regardless of app language: this is an exchange format meant to be pasted
 * into Google Maps and similar tools, which expect a dot decimal separator — not the display
 * format, which follows the user's locale.
 */
fun formatCoordinatesForClipboard(latitude: Double, longitude: Double): String =
    "%.6f, %.6f".format(Locale.US, latitude, longitude)

/**
 * Copies [latitude]/[longitude] to the system clipboard. Android 13+ shows its own "copied"
 * confirmation UI, so a toast is only needed on older versions.
 */
fun Context.copyCoordinates(latitude: Double, longitude: Double) {
    val text = formatCoordinatesForClipboard(latitude, longitude)
    getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("coordinates", text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.coordinates_copied, Toast.LENGTH_SHORT).show()
    }
}

/**
 * A duration as `H:MM:SS` (or `MM:SS` under an hour). Always defined, unlike a GPS reading.
 *
 * @param showSecondsPastOneHour Whether to keep the seconds component past the one-hour mark
 * (`H:MM:SS`) or drop it (`H:MM`). The live recording chip needs the tick for feedback that it's
 * still running; completed-session summaries (Carnet, session detail) don't, and read cleaner
 * without it.
 */
fun formatDuration(durationMs: Long, showSecondsPastOneHour: Boolean = true): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && showSecondsPastOneHour -> "%d:%02d:%02d".format(hours, minutes, seconds)
        hours > 0 -> "%d:%02d".format(hours, minutes)
        else -> "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * A distance in meters, formatted with two decimals for readability during a recording: km
 * (metric) or mi (imperial), per [unitSystem].
 */
fun formatDistance(distanceMeters: Double, unitSystem: UnitSystem): String {
    val km = distanceMeters / 1000.0
    val value = if (unitSystem == UnitSystem.METRIC) km else km * KM_TO_MILES
    return "%.2f".format(value)
}

/**
 * The (value, unit) pair for a short-range distance display (target/current-distance cards):
 * whole small units below the long-unit threshold, two-decimal long units above. Metric switches
 * from meters to km at 1000 m; imperial switches from feet to mi at 528 ft (0.1 mi).
 */
fun shortDistanceValueAndUnit(distanceMeters: Double, unitSystem: UnitSystem): Pair<String, String> = when (unitSystem) {
    UnitSystem.METRIC -> if (distanceMeters >= 1000.0) {
        formatDistance(distanceMeters, unitSystem) to distanceUnitLabel(unitSystem)
    } else {
        distanceMeters.roundToInt().toString() to shortUnitLabel(unitSystem)
    }
    UnitSystem.IMPERIAL -> {
        val feet = distanceMeters * METERS_TO_FEET
        if (feet >= 528.0) {
            formatDistance(distanceMeters, unitSystem) to distanceUnitLabel(unitSystem)
        } else {
            feet.roundToInt().toString() to shortUnitLabel(unitSystem)
        }
    }
}

/**
 * A distance for a short-range display (target/current-distance cards), as a single string —
 * unlike [formatDistance] this includes the unit, since callers show it standalone rather than
 * next to a separate label. See [shortDistanceValueAndUnit] for the threshold rules.
 */
fun formatShortDistance(distanceMeters: Double, unitSystem: UnitSystem): String {
    val (value, unit) = shortDistanceValueAndUnit(distanceMeters, unitSystem)
    return "$value $unit"
}

/** An elevation gain/loss (D+/D-) as a whole number, meters or feet depending on [unitSystem]. */
fun formatElevation(elevationMeters: Double, unitSystem: UnitSystem): String {
    val value = if (unitSystem == UnitSystem.METRIC) elevationMeters else elevationMeters * METERS_TO_FEET
    return value.roundToInt().toString()
}
