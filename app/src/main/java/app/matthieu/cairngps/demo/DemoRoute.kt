package app.matthieu.cairngps.demo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Meters per degree of latitude — close enough to constant for the small extents used here. */
private const val METERS_PER_DEG_LAT = 111_320.0

/** One point of a synthetic route: a position with the altitude reached there. */
data class DemoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
)

/**
 * A closed, smoothly varying walking loop around a center point, shared by the live GPS simulator
 * and the seeded history so both produce the same kind of plausible-looking trace.
 *
 * The shape is a circle perturbed by two harmonics, which reads as a hand-walked ridge loop rather
 * than the perfect ellipse a single sine would give. It is a pure function of the loop phase, so
 * the live simulator needs no state: it just samples the curve at the current wall-clock time and
 * every fix is reproducible.
 *
 * @param centerLatitude  Latitude the loop is centered on, in decimal degrees.
 * @param centerLongitude Longitude the loop is centered on, in decimal degrees.
 * @param baseAltitude    Altitude at the middle of the climb, in meters.
 * @param radiusMeters    Nominal radius of the loop, in meters.
 * @param climbMeters     Half-amplitude of the altitude swing over one loop, in meters.
 * @param shape           Distinguishes one route from another: shifts the harmonics so two loops
 *                        with the same radius don't trace the same outline.
 */
class DemoRoute(
    private val centerLatitude: Double,
    private val centerLongitude: Double,
    private val baseAltitude: Double,
    private val radiusMeters: Double,
    private val climbMeters: Double,
    private val shape: Double = 0.0,
) {

    private val metersPerDegLon = METERS_PER_DEG_LAT * cos(centerLatitude * PI / 180.0)

    /** The point reached at [phase] of the loop, where 0.0 and 1.0 are the same starting point. */
    fun pointAt(phase: Double): DemoPoint {
        val u = 2.0 * PI * phase
        val east = radiusMeters * (sin(u) + 0.27 * sin(3.0 * u + shape))
        val north = radiusMeters * (cos(u) + 0.22 * sin(2.0 * u + shape * 1.7))
        val altitude = baseAltitude +
            climbMeters * sin(u - PI / 2.0 + shape * 0.3) +
            climbMeters * 0.18 * sin(3.0 * u + shape)
        return DemoPoint(
            latitude = centerLatitude + north / METERS_PER_DEG_LAT,
            longitude = centerLongitude + east / metersPerDegLon,
            altitude = altitude,
        )
    }
}

/**
 * Planar distance between two nearby points, in meters. An equirectangular approximation rather
 * than a full haversine: over the few hundred meters separating consecutive demo fixes the error
 * is far below the accuracy the app displays, and it keeps this file dependency-free (the real
 * data path uses `Geodesy`/`Location.distanceBetween`).
 */
fun demoDistanceMeters(from: DemoPoint, to: DemoPoint): Double {
    val dLat = (to.latitude - from.latitude) * METERS_PER_DEG_LAT
    val dLon = (to.longitude - from.longitude) *
        METERS_PER_DEG_LAT * cos((from.latitude + to.latitude) / 2.0 * PI / 180.0)
    return kotlin.math.sqrt(dLat * dLat + dLon * dLon)
}
