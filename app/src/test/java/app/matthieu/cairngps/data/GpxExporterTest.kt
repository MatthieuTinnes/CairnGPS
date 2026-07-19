package app.matthieu.cairngps.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Locale

class GpxExporterTest {

    private val exporter = GpxExporter()

    private fun session(name: String = "Morning hike") = Session(
        id = 1,
        name = name,
        startTimestamp = 1_700_000_000_000L,
        endTimestamp = 1_700_003_600_000L,
        distanceMeters = 5_000.0,
        averageSpeed = 1.4f,
        maxSpeed = 2.1f,
        elevationGain = 120.0,
        elevationLoss = 80.0,
        minAltitude = 900.0,
        maxAltitude = 1_035.5,
        latitudeMax = 45.91,
        latitudeMin = 45.9,
        longitudeMax = 6.88,
        longitudeMin = 6.87,
    )

    private fun trackPoint(lat: Double = 45.9, lon: Double = 6.87, ele: Double = 1035.5) = TrackPoint(
        id = 1,
        sessionId = 1,
        timestamp = 1_700_000_000_000L,
        latitude = lat,
        longitude = lon,
        altitude = ele,
    )

    private fun waypoint(name: String = "Summit") = Waypoint(
        id = 1,
        name = name,
        latitude = 45.91,
        longitude = 6.88,
        altitude = 1_035.5,
        speed = 0f,
        horizontalAccuracy = 4f,
        satellitesUsedInFix = 8,
        timestamp = 1_700_001_000_000L,
        sessionId = 1,
    )

    private suspend fun export(
        session: Session = session(),
        track: List<TrackPoint> = listOf(trackPoint()),
        waypoints: List<Waypoint> = listOf(waypoint()),
    ): String {
        val output = ByteArrayOutputStream()
        exporter.export(session, track, waypoints, output)
        return output.toString(Charsets.UTF_8.name())
    }

    @Test
    fun `export produces a well-formed gpx document with metadata, waypoints and track`() = runTest {
        val gpx = export()

        assertTrue(gpx.contains("""<gpx version="1.1" creator="CairnGPS""""))
        assertTrue(gpx.contains("<metadata>"))
        assertTrue(gpx.contains("<name>Morning hike</name>"))
        assertTrue(gpx.contains("""<wpt lat="45.910000" lon="6.880000">"""))
        assertTrue(gpx.contains("<name>Summit</name>"))
        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.contains("<trkseg>"))
        assertTrue(gpx.contains("""<trkpt lat="45.900000" lon="6.870000">"""))
        assertTrue(gpx.trim().endsWith("</gpx>"))
    }

    @Test
    fun `export formats decimals with a dot regardless of the default locale`() = runTest {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val gpx = export()

            assertFalse(gpx.contains(","))
            assertTrue(gpx.contains("<ele>1035.5</ele>"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `export renders timestamps as ISO-8601 UTC`() = runTest {
        val gpx = export()

        assertTrue(gpx.contains("<time>2023-11-14T22:13:20Z</time>"))
    }

    @Test
    fun `export escapes special characters in names`() = runTest {
        val gpx = export(session = session(name = "Alpe & <Col> \"Test\""))

        assertTrue(gpx.contains("<name>Alpe &amp; &lt;Col&gt; &quot;Test&quot;</name>"))
        assertFalse(gpx.contains("<Col>"))
    }

    @Test
    fun `export with an empty track still produces valid gpx without a trkseg`() = runTest {
        val gpx = export(track = emptyList())

        assertTrue(gpx.contains("<trk>"))
        assertFalse(gpx.contains("<trkseg>"))
        assertTrue(gpx.trim().endsWith("</gpx>"))
    }

    @Test
    fun `export with no waypoints omits wpt elements`() = runTest {
        val gpx = export(waypoints = emptyList())

        assertFalse(gpx.contains("<wpt"))
    }
}
