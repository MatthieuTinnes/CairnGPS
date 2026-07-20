package app.matthieu.cairngps.domain

import android.location.Location

/** Distance et cap initial (relatif au nord vrai) entre deux coordonnées. */
data class DistanceBearing(val distanceMeters: Double, val bearingTrueDegrees: Float)

/**
 * Wraps [Location.distanceBetween] (geodesic on WGS84) so callers share the exact same
 * computation as the data layer without repeating the FloatArray plumbing.
 */
fun distanceAndBearing(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): DistanceBearing {
    val results = FloatArray(2)
    Location.distanceBetween(startLatitude, startLongitude, endLatitude, endLongitude, results)
    return DistanceBearing(distanceMeters = results[0].toDouble(), bearingTrueDegrees = results[1])
}
