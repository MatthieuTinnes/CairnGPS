package app.matthieu.cairngps.ui.waypoints

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// java.time is available from minSdk 26, so no desugaring is needed here.
private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private val defaultNameFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())

/** Formats a waypoint timestamp as a localized date + time for display. */
fun formatWaypointTimestamp(epochMillis: Long): String =
    dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/**
 * A default waypoint name suggested in the save dialog, based on the current date and time,
 * e.g. `Repère 10/07/2026 14:30`.
 */
fun defaultWaypointName(namePrefix: String, epochMillis: Long = System.currentTimeMillis()): String {
    val stamp = defaultNameFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    return "$namePrefix $stamp"
}
