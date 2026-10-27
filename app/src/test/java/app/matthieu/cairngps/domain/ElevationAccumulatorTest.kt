package app.matthieu.cairngps.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ElevationAccumulatorTest {

    private val t0 = 1_700_000_000_000L

    private class Totals(val gain: Double, val loss: Double)

    private fun ElevationAccumulator.feed(samples: List<Triple<Long, Double, Boolean>>): Totals {
        var gain = 0.0
        var loss = 0.0
        for ((timestamp, altitude, moving) in samples) {
            val delta = add(timestamp, altitude, moving)
            if (delta > 0) gain += delta else loss -= delta
        }
        return Totals(gain, loss)
    }

    /** Feeds 1 Hz moving samples at [altitude] from [start] for [seconds] + 1 samples; returns the net counted change. */
    private fun ElevationAccumulator.steady(start: Long, altitude: Double, seconds: Int = 40): Double =
        (0..seconds).sumOf { i -> add(start + i * 1_000L, altitude, isMoving = true) }

    @Test
    fun `reference is only set once the window spans the warm-up`() {
        val accumulator = ElevationAccumulator()

        accumulator.steady(t0, 500.0, seconds = 29)
        assertNull(accumulator.referenceAltitude)

        assertEquals(0.0, accumulator.add(t0 + 30_000, 500.0, isMoving = true), 0.0)
        assertEquals(500.0, accumulator.referenceAltitude!!, 0.0)
    }

    @Test
    fun `a noisy sample right after a gap is not counted`() {
        val accumulator = ElevationAccumulator()
        accumulator.steady(t0, 100.0)

        // Back after a 5 min pause: the window restarts, the first sample is 8 m off.
        assertEquals(0.0, accumulator.add(t0 + 340_000, 108.0, isMoving = true), 0.0)
        val net = accumulator.steady(t0 + 341_000, 100.0)

        assertEquals(0.0, net, 0.0)
        assertEquals(100.0, accumulator.referenceAltitude!!, 0.0)
    }

    @Test
    fun `jitter around a constant altitude is not counted`() {
        val samples = (0 until 600).map { i ->
            Triple(t0 + i * 1_000L, if (i % 2 == 0) 100.0 else 108.0, true)
        }

        val totals = ElevationAccumulator().feed(samples)

        assertEquals(0.0, totals.gain, 0.0)
        assertEquals(0.0, totals.loss, 0.0)
    }

    @Test
    fun `steady climb is counted once the smoothed altitude settles`() {
        // +0.2 m/s for 300 s (60 m), then flat long enough for the window to catch up.
        val samples = (0..400).map { i ->
            Triple(t0 + i * 1_000L, 100.0 + 0.2 * minOf(i, 300), true)
        }

        val totals = ElevationAccumulator().feed(samples)

        // Whatever is left below the threshold since the last counted step is not counted yet.
        assertTrue("gain=${totals.gain}", totals.gain > 60.0 - ElevationAccumulator.DEFAULT_THRESHOLD_METERS)
        assertTrue("gain=${totals.gain}", totals.gain <= 60.0 + 1e-9)
        assertEquals(0.0, totals.loss, 0.0)
    }

    @Test
    fun `stationary samples are ignored but the climb made meanwhile is kept`() {
        val accumulator = ElevationAccumulator()
        accumulator.steady(t0, 100.0)

        // Drift while stopped: ignored, the reference stays at 100 m.
        assertEquals(0.0, accumulator.add(t0 + 60_000, 150.0, isMoving = false), 0.0)
        assertEquals(0.0, accumulator.add(t0 + 70_000, 80.0, isMoving = false), 0.0)
        assertEquals(100.0, accumulator.referenceAltitude!!, 0.0)

        // Moving again, past the window: counted against the held reference.
        assertEquals(20.0, accumulator.steady(t0 + 200_000, 120.0), 1e-9)
    }

    @Test
    fun `nothing is referenced until the first moving sample`() {
        val accumulator = ElevationAccumulator()

        accumulator.add(t0, 500.0, isMoving = false)

        assertNull(accumulator.referenceAltitude)
    }

    @Test
    fun `resumed accumulator counts against the restored reference`() {
        val accumulator = ElevationAccumulator(referenceAltitude = 200.0)

        assertEquals(10.0, accumulator.steady(t0, 210.0), 1e-9)
    }

    /**
     * Real hikes recorded by CairnGPS (exported GPX, one point every 5-15 s), against reference D+
     * values: 446 m measured for the loop, 620 m from komoot (DEM-based) for the summit hike.
     * The previous algorithm (4 m threshold on raw fixes) gave 588 m and 738 m.
     *
     * Anonymized: every longitude is shifted by a constant (latitudes untouched, so distances are
     * preserved exactly) and timestamps are moved to 2000-01-01, intervals unchanged.
     *
     * The GPX carries no Doppler speed, so "moving" is derived from consecutive positions here.
     * The summit GPS track itself spans 1063 m -> 1713 m, so no smoothing can go much below
     * ~645 m: the remaining gap to komoot is a GPS altitude bias, hence the 5 % tolerance.
     */
    @Test
    fun `loop hike matches the measured elevation gain`() {
        assertWithinPercent(expected = 446.0, actual = gainOf("/gpx/hike-loop.gpx"), percent = 5.0)
    }

    @Test
    fun `summit hike matches the komoot elevation gain`() {
        assertWithinPercent(expected = 620.0, actual = gainOf("/gpx/hike-summit.gpx"), percent = 5.0)
    }

    private fun assertWithinPercent(expected: Double, actual: Double, percent: Double) {
        val error = abs(actual - expected) / expected * 100
        assertTrue("D+=$actual m, expected $expected m ±$percent % (error $error %)", error <= percent)
    }

    private class GpxPoint(val latitude: Double, val longitude: Double, val altitude: Double, val timestampMs: Long)

    private fun gainOf(resource: String): Double {
        val points = loadGpx(resource)
        val samples = points.mapIndexed { i, point ->
            val moving = i == 0 || run {
                val previous = points[i - 1]
                val seconds = ((point.timestampMs - previous.timestampMs) / 1000.0).coerceAtLeast(1.0)
                haversineMeters(previous, point) / seconds > MOVING_SPEED_THRESHOLD_MS
            }
            Triple(point.timestampMs, point.altitude, moving)
        }
        return ElevationAccumulator().feed(samples).gain
    }

    private fun loadGpx(resource: String): List<GpxPoint> {
        val stream = requireNotNull(javaClass.getResourceAsStream(resource)) { "missing $resource" }
        val document = stream.use {
            DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(it)
        }
        val nodes = document.getElementsByTagNameNS("*", "trkpt")
        return (0 until nodes.length).map { i ->
            val node = nodes.item(i) as Element
            GpxPoint(
                latitude = node.getAttribute("lat").toDouble(),
                longitude = node.getAttribute("lon").toDouble(),
                altitude = node.getElementsByTagNameNS("*", "ele").item(0).textContent.toDouble(),
                timestampMs = Instant.parse(node.getElementsByTagNameNS("*", "time").item(0).textContent).toEpochMilli(),
            )
        }
    }

    private fun haversineMeters(a: GpxPoint, b: GpxPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
    }

    private companion object {
        const val MOVING_SPEED_THRESHOLD_MS = 0.5
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
