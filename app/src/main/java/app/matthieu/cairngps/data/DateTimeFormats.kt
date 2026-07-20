package app.matthieu.cairngps.data

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared pattern used to timestamp auto-generated default names (waypoints, sessions), e.g.
 * `10/07/2026 14:30`. Kept in the data layer, plain `java.time` with no UI dependency, so both
 * [RecordingRepository] and `ui.waypoints.WaypointFormatting` can reuse it.
 *
 * A function, not a top-level `val`: the app supports switching language in-app
 * (`AppCompatDelegate.setApplicationLocales`) without killing the process, so a formatter built
 * once at class-load time would keep using the locale active at first use.
 */
fun defaultNameTimestampFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
