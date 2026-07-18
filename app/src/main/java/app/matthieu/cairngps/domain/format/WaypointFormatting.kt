package app.matthieu.cairngps.domain.format

import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.defaultNameTimestampFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// java.time is available from minSdk 26, so no desugaring is needed here.
//
// Each formatter below is built fresh per call rather than cached in a top-level `val`: the app
// supports switching language in-app (`AppCompatDelegate.setApplicationLocales`) without killing
// the process, so a formatter captured once at class-load time would keep the stale locale.
private fun dateTimeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun shortDateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun shortDateTimeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM '·' HH:mm", Locale.getDefault())

private fun timeOfDayFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** Formats a waypoint timestamp as a localized date + time for display. */
fun formatWaypointTimestamp(epochMillis: Long): String =
    dateTimeFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** Compact "12 juil."-style date, no year/time — for list-row meta lines (screen 1h). */
fun formatWaypointShortDate(epochMillis: Long): String =
    shortDateFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** Compact "12 juil. · 09:02"-style date + time, no year — for session rows (screen 1p). */
fun formatWaypointShortDateTime(epochMillis: Long): String =
    shortDateTimeFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** Just the "09:02"-style time of day — for the session detail header (screen 1j). */
fun formatTimeOfDay(epochMillis: Long): String =
    timeOfDayFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/**
 * A default waypoint name suggested in the save dialog, based on the current date and time,
 * e.g. `Repère 10/07/2026 14:30`.
 */
fun defaultWaypointName(namePrefix: String, epochMillis: Long = System.currentTimeMillis()): String {
    val stamp = defaultNameTimestampFormatter().format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    return "$namePrefix $stamp"
}

/**
 * The "alt m · lat, lon · date" meta line shown under a waypoint's name in list rows (screens
 * 1h, 1j) — shared by [app.matthieu.cairngps.ui.waypoints.WaypointsListContent] and the session
 * detail screen's waypoint rows.
 */
fun formatWaypointMetaLine(waypoint: Waypoint, unitSystem: UnitSystem): String =
    "${formatAltitude(waypoint.altitude, unitSystem)} ${shortUnitLabel(unitSystem)} · " +
        "%.4f, %.4f".format(waypoint.latitude, waypoint.longitude) + " · " +
        formatWaypointShortDate(waypoint.timestamp)
