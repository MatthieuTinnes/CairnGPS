package app.matthieu.cairngps.ui.location

import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Placeholder shown for any value before the first GPS fix arrives. */
const val DASH: String = "—"

/** How latitude/longitude are rendered. */
enum class CoordinateFormat {
    /** Decimal degrees, e.g. `47.123456°`. */
    DECIMAL,

    /** Degrees / minutes / seconds, e.g. `47°07'24.4"N`. */
    DMS,
    ;

    fun toggled(): CoordinateFormat = if (this == DECIMAL) DMS else DECIMAL
}

/** Quality bucket for a horizontal accuracy radius, used to drive the visual indicator. */
enum class AccuracyQuality { GOOD, MEDIUM, POOR, UNKNOWN }

/** green < 5 m, orange 5–15 m, red > 15 m. */
fun accuracyQuality(accuracyMeters: Float?): AccuracyQuality = when {
    accuracyMeters == null -> AccuracyQuality.UNKNOWN
    accuracyMeters < 5f -> AccuracyQuality.GOOD
    accuracyMeters <= 15f -> AccuracyQuality.MEDIUM
    else -> AccuracyQuality.POOR
}

/** Formats a coordinate, returning [DASH] when [value] is null. */
fun formatCoordinate(value: Double?, isLatitude: Boolean, format: CoordinateFormat): String = when {
    value == null -> DASH
    format == CoordinateFormat.DECIMAL -> "%.6f°".format(value)
    else -> formatDms(value, isLatitude)
}

/**
 * Converts decimal degrees to a `D°MM'SS.s"H` string.
 *
 * Computes everything in integer tenths-of-an-arc-second so that rounding never produces
 * `60"` or `60'` carry artifacts.
 */
private fun formatDms(value: Double, isLatitude: Boolean): String {
    val hemisphere = when {
        isLatitude -> if (value >= 0) "N" else "S"
        else -> if (value >= 0) "E" else "O" // O = Ouest (French)
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

/** Altitude as whole meters, or [DASH]. Unit is displayed separately. */
fun formatAltitude(altitudeMeters: Double?): String =
    altitudeMeters?.roundToInt()?.toString() ?: DASH

/** Speed converted to km/h with one decimal, or [DASH]. */
fun formatSpeedKmh(speedMetersPerSecond: Float?): String =
    speedMetersPerSecond?.let { "%.1f".format(it * 3.6f) } ?: DASH

/** Raw speed in m/s with one decimal, or [DASH]. */
fun formatSpeedMs(speedMetersPerSecond: Float?): String =
    speedMetersPerSecond?.let { "%.1f".format(it) } ?: DASH

/** Horizontal accuracy radius in meters with one decimal, or [DASH]. */
fun formatAccuracy(accuracyMeters: Float?): String =
    accuracyMeters?.let { "%.1f".format(it) } ?: DASH

/** Coordinates as plain decimal degrees for the clipboard, e.g. `47.123456, 6.123456`. */
fun formatCoordinatesForClipboard(latitude: Double, longitude: Double): String =
    "%.6f, %.6f".format(latitude, longitude)
