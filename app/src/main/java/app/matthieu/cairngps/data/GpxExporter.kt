package app.matthieu.cairngps.data

import java.io.OutputStream
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Serializes a [Session] — its track and attached waypoints — to a GPX 1.1 file, for use with
 * other GPS tools (Strava, Komoot, Garmin, QGIS...). See the session detail screen.
 *
 * Plain `java.io.OutputStream` in, no Android UI concerns, matching [BackupRepository]'s shape;
 * the caller opens the stream (via the system save dialog) and this closes it once written.
 */
class GpxExporter {

    /** Writes [session]'s track and [waypoints] to [output] as a single GPX 1.1 document. */
    suspend fun export(
        session: Session,
        track: List<TrackPoint>,
        waypoints: List<Waypoint>,
        output: OutputStream,
    ) = withContext(Dispatchers.IO) {
        output.writer(Charsets.UTF_8).use { writer ->
            writer.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            writer.appendLine(
                """<gpx version="1.1" creator="CairnGPS" xmlns="http://www.topografix.com/GPX/1/1">""",
            )

            writer.appendLine("  <metadata>")
            writer.appendLine("    <name>${escape(session.name)}</name>")
            writer.appendLine("    <time>${isoTime(session.startTimestamp)}</time>")
            writer.appendLine("  </metadata>")

            waypoints.forEach { waypoint ->
                writer.appendLine(
                    """  <wpt lat="${decimal(waypoint.latitude)}" lon="${decimal(waypoint.longitude)}">""",
                )
                writer.appendLine("    <ele>${elevation(waypoint.altitude)}</ele>")
                writer.appendLine("    <time>${isoTime(waypoint.timestamp)}</time>")
                writer.appendLine("    <name>${escape(waypoint.name)}</name>")
                writer.appendLine("  </wpt>")
            }

            writer.appendLine("  <trk>")
            writer.appendLine("    <name>${escape(session.name)}</name>")
            if (track.isNotEmpty()) {
                writer.appendLine("    <trkseg>")
                track.forEach { point ->
                    writer.appendLine(
                        """      <trkpt lat="${decimal(point.latitude)}" lon="${decimal(point.longitude)}">""",
                    )
                    writer.appendLine("        <ele>${elevation(point.altitude)}</ele>")
                    writer.appendLine("        <time>${isoTime(point.timestamp)}</time>")
                    writer.appendLine("      </trkpt>")
                }
                writer.appendLine("    </trkseg>")
            }
            writer.appendLine("  </trk>")

            writer.appendLine("</gpx>")
        }
    }

    // Locale.US regardless of the device's language setting: GPX requires a '.' decimal
    // separator, but the app's French locale would otherwise format doubles with a ','.
    private fun decimal(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun elevation(value: Double): String = String.format(Locale.US, "%.1f", value)

    private fun isoTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
