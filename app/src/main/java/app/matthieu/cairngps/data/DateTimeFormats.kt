package app.matthieu.cairngps.data

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared pattern used to timestamp auto-generated default names (waypoints, sessions), e.g.
 * `10/07/2026 14:30`. Kept in the data layer, plain `java.time` with no UI dependency, so both
 * [RecordingRepository] and `ui.waypoints.WaypointFormatting` can reuse it.
 */
val DefaultNameTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
