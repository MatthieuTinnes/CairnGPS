package app.matthieu.cairngps.demo

import android.hardware.SensorManager
import app.matthieu.cairngps.data.CompassReading
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.SatelliteInfo
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Where the fictional walker is. Deliberately a bare mountain area with no tie to anyone: it reads
 * as a plausible hike (~2400 m, steady climb and descent) without pointing at a real home, trailhead
 * or habit.
 */
private const val DEMO_CENTER_LATITUDE = 45.0195
private const val DEMO_CENTER_LONGITUDE = 6.4062
private const val DEMO_BASE_ALTITUDE = 2412.0

/** One full loop every 25 minutes over a ~1.9 km circuit, i.e. a ~4.5 km/h walking pace. */
private const val DEMO_LOOP_PERIOD_SECONDS = 1_500.0

/** Interval the simulated fixes and GNSS snapshots are emitted at, matching the real 1 Hz GPS. */
private const val FIX_INTERVAL_MS = 1_000L

/** ~20 Hz, matching what `SENSOR_DELAY_UI` delivers for the real rotation vector sensor. */
private const val HEADING_INTERVAL_MS = 50L

/** Seconds between the two samples the instantaneous speed and heading are derived from. */
private const val DERIVATIVE_STEP_SECONDS = 1.0

private val demoRoute = DemoRoute(
    centerLatitude = DEMO_CENTER_LATITUDE,
    centerLongitude = DEMO_CENTER_LONGITUDE,
    baseAltitude = DEMO_BASE_ALTITUDE,
    radiusMeters = 260.0,
    climbMeters = 55.0,
    shape = 0.9,
)

/**
 * The synthetic replacement for the GPS chip, the GNSS engine and the compass while
 * [DemoMode] is on — see [app.matthieu.cairngps.data.LocationRepository] and
 * [app.matthieu.cairngps.data.CompassRepository], which delegate to it instead of touching
 * `LocationManager`/`SensorManager` at all.
 *
 * Every value is a pure function of the wall clock: no state, no accumulated drift, identical
 * across app restarts, and continuous — which is what makes it usable for a screen recording
 * where the numbers must move smoothly rather than jump.
 */
class DemoGpsSource {

    /** Fixes at the real GPS cadence. Emits immediately so a screen never opens on `--`. */
    fun locationUpdates(): Flow<LocationData> = flow {
        while (true) {
            emit(currentFix())
            delay(FIX_INTERVAL_MS.milliseconds)
        }
    }

    /** The current synthetic fix, for the cheap "last known position" path. */
    fun lastKnownLocation(): LocationData = currentFix()

    /** GNSS snapshots at the real ~1 Hz status cadence. */
    fun satelliteUpdates(): Flow<List<SatelliteInfo>> = flow {
        while (true) {
            emit(currentSatellites())
            delay(FIX_INTERVAL_MS.milliseconds)
        }
    }

    /**
     * Compass readings following the direction of travel along the loop, so the dial and the
     * position agree on screen instead of drifting apart. Reported as high accuracy: a demo should
     * never show the "calibrate your compass" warning.
     */
    fun headingUpdates(): Flow<CompassReading> = flow {
        while (true) {
            val seconds = nowSeconds()
            // A slow wobble keeps the needle alive; a perfectly rigid heading looks like a freeze
            // frame in a screen recording.
            val wobble = 2.4 * sin(seconds / 7.0)
            val heading = ((bearingAt(seconds) + wobble) % 360.0 + 360.0) % 360.0
            emit(
                CompassReading(
                    azimuthMagneticDegrees = heading.toFloat(),
                    accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                ),
            )
            delay(HEADING_INTERVAL_MS.milliseconds)
        }
    }

    private fun currentFix(): LocationData {
        val seconds = nowSeconds()
        val point = pointAt(seconds)
        val previous = pointAt(seconds - DERIVATIVE_STEP_SECONDS)
        return LocationData(
            latitude = point.latitude,
            longitude = point.longitude,
            altitude = point.altitude,
            speed = (demoDistanceMeters(previous, point) / DERIVATIVE_STEP_SECONDS).toFloat(),
            // Comfortably inside the green band the UI uses below 5 m, with enough movement that
            // the readout doesn't look frozen.
            horizontalAccuracy = (3.1 + 0.55 * sin(seconds / 17.0)).toFloat(),
            verticalAccuracy = (4.4 + 1.1 * sin(seconds / 23.0)).toFloat(),
            timestamp = System.currentTimeMillis(),
        )
    }

    private fun currentSatellites(): List<SatelliteInfo> {
        val seconds = nowSeconds()
        return satelliteSeeds.map { seed ->
            // Real satellites sweep the sky over hours; these drift just fast enough to be visible
            // on the sky plot during a capture without looking like they are orbiting in seconds.
            val azimuth = ((seed.azimuthBase + seconds * 0.02 + 4.0 * sin(seconds / 90.0 + seed.phase)) % 360.0 + 360.0) % 360.0
            val elevation = (seed.elevationBase + 3.0 * sin(seconds / 70.0 + seed.phase)).coerceIn(3.0, 88.0)
            val cn0 = (seed.cn0Base + 2.5 * sin(seconds / 13.0 + seed.phase)).coerceIn(18.0, 51.0)
            SatelliteInfo(
                constellation = seed.constellation,
                svid = seed.svid,
                cn0DbHz = cn0.toFloat(),
                usedInFix = elevation >= 12.0 && cn0 >= 30.0,
                azimuthDegrees = azimuth.toFloat(),
                elevationDegrees = elevation.toFloat(),
            )
        }
    }

    private fun pointAt(seconds: Double): DemoPoint =
        demoRoute.pointAt(seconds / DEMO_LOOP_PERIOD_SECONDS)

    /** Direction of travel along the loop at [seconds], in degrees clockwise from north. */
    private fun bearingAt(seconds: Double): Double {
        val from = pointAt(seconds - DERIVATIVE_STEP_SECONDS)
        val to = pointAt(seconds + DERIVATIVE_STEP_SECONDS)
        val north = to.latitude - from.latitude
        val east = (to.longitude - from.longitude) *
            kotlin.math.cos(DEMO_CENTER_LATITUDE * PI / 180.0)
        return atan2(east, north) * 180.0 / PI
    }

    private fun nowSeconds(): Double = System.currentTimeMillis() / 1_000.0
}

/** The unchanging part of a simulated satellite; the time-varying part is applied per snapshot. */
private data class DemoSatelliteSeed(
    val constellation: Constellation,
    val svid: Int,
    val azimuthBase: Double,
    val elevationBase: Double,
    val cn0Base: Double,
    val phase: Double,
)

/**
 * A fixed multi-constellation sky: 33 satellites, of which around two thirds pass the
 * "used in fix" bar — what a recent dual-frequency phone actually sees in the open. Built from a
 * fixed seed so the sky plot and the globe look the same in every capture.
 */
private val satelliteSeeds: List<DemoSatelliteSeed> = run {
    val random = Random(20260801)
    // constellation, satellite count, first svid — svids stay inside each constellation's real range.
    listOf(
        Triple(Constellation.GPS, 10, 2),
        Triple(Constellation.GALILEO, 8, 3),
        Triple(Constellation.GLONASS, 7, 1),
        Triple(Constellation.BEIDOU, 6, 5),
        Triple(Constellation.SBAS, 2, 120),
    ).flatMap { (constellation, count, firstSvid) ->
        List(count) { index ->
            DemoSatelliteSeed(
                constellation = constellation,
                svid = firstSvid + index * 3 + random.nextInt(3),
                azimuthBase = random.nextDouble() * 360.0,
                // Biased towards the upper sky via sqrt: a uniform elevation would crowd the
                // horizon ring of the sky plot and leave the middle empty.
                elevationBase = 6.0 + sqrt(random.nextDouble()) * 76.0,
                cn0Base = 25.0 + random.nextDouble() * 21.0,
                phase = random.nextDouble() * 2.0 * PI,
            )
        }
    }
}
