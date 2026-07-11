package app.matthieu.cairngps.domain

import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.SatelliteInfo
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in Earth-Centered, Earth-Fixed coordinates, in kilometers. */
data class EcefPosition(val x: Double, val y: Double, val z: Double) {
    val norm: Double get() = sqrt(x * x + y * y + z * z)
}

/**
 * Reconstructs approximate 3D satellite positions from the only angular data [android.location.GnssStatus]
 * provides: azimuth and elevation as seen from the observer.
 *
 * Method: cast a ray from the observer's ECEF position along the satellite's line of sight
 * (ENU direction rotated into ECEF) and intersect it with a sphere centered on Earth whose
 * radius is the constellation's nominal orbital radius. The result is not the satellite's real
 * orbital position (no ephemerides are used), but it is geometrically consistent with what the
 * receiver observes, which is enough for visualization.
 */
object SatelliteGeometry {

    /** Mean Earth radius. A spherical Earth is accurate enough for this visualization. */
    const val EARTH_RADIUS_KM = 6_371.0

    /**
     * Nominal orbital radius (distance from Earth's center) per constellation, in km.
     *
     * Approximation: constellations are treated as single spherical shells. GEO/IGSO/HEO
     * members (SBAS, QZSS, IRNSS/NavIC, and BeiDou's GEO/IGSO satellites) don't share the MEO
     * shell of their siblings, so their reconstructed positions are only indicative — acceptable
     * for a visualization that has no access to real ephemerides.
     */
    fun nominalOrbitRadiusKm(constellation: Constellation): Double = when (constellation) {
        Constellation.GPS -> 26_560.0
        Constellation.GLONASS -> 25_510.0
        Constellation.GALILEO -> 29_600.0
        // MEO shell; BeiDou GEO/IGSO satellites are approximated onto it (see above).
        Constellation.BEIDOU -> 27_900.0
        Constellation.SBAS -> 42_164.0
        Constellation.QZSS -> 42_000.0
        // NavIC is entirely GEO/IGSO.
        Constellation.IRNSS -> 42_164.0
        Constellation.UNKNOWN -> 26_560.0
    }

    /** Converts a geodetic position to ECEF, assuming a spherical Earth of [EARTH_RADIUS_KM]. */
    fun geodeticToEcef(latitudeDeg: Double, longitudeDeg: Double, altitudeMeters: Double): EcefPosition {
        val lat = Math.toRadians(latitudeDeg)
        val lon = Math.toRadians(longitudeDeg)
        val r = EARTH_RADIUS_KM + altitudeMeters / 1_000.0
        return EcefPosition(
            x = r * cos(lat) * cos(lon),
            y = r * cos(lat) * sin(lon),
            z = r * sin(lat),
        )
    }

    /** Convenience overload working directly on the app's domain models. */
    fun satelliteEcef(observer: LocationData, satellite: SatelliteInfo): EcefPosition? =
        satelliteEcef(
            observerLatitudeDeg = observer.latitude,
            observerLongitudeDeg = observer.longitude,
            observerAltitudeMeters = observer.altitude,
            azimuthDeg = satellite.azimuthDegrees.toDouble(),
            elevationDeg = satellite.elevationDegrees.toDouble(),
            orbitRadiusKm = nominalOrbitRadiusKm(satellite.constellation),
        )

    /**
     * Reconstructs the approximate ECEF position of a satellite seen from the observer at the
     * given azimuth/elevation, placed on the sphere of radius [orbitRadiusKm].
     *
     * Returns `null` when the line of sight never reaches the orbital sphere — impossible for an
     * observer near the Earth's surface, but guarded against degenerate inputs.
     */
    fun satelliteEcef(
        observerLatitudeDeg: Double,
        observerLongitudeDeg: Double,
        observerAltitudeMeters: Double,
        azimuthDeg: Double,
        elevationDeg: Double,
        orbitRadiusKm: Double,
    ): EcefPosition? {
        val observer = geodeticToEcef(observerLatitudeDeg, observerLongitudeDeg, observerAltitudeMeters)

        // Line-of-sight unit vector in the observer's local ENU (East-North-Up) frame.
        val az = Math.toRadians(azimuthDeg)
        val el = Math.toRadians(elevationDeg)
        val east = cos(el) * sin(az)
        val north = cos(el) * cos(az)
        val up = sin(el)

        // ENU -> ECEF rotation, function of the observer's latitude/longitude.
        val lat = Math.toRadians(observerLatitudeDeg)
        val lon = Math.toRadians(observerLongitudeDeg)
        val dx = -sin(lon) * east - sin(lat) * cos(lon) * north + cos(lat) * cos(lon) * up
        val dy = cos(lon) * east - sin(lat) * sin(lon) * north + cos(lat) * sin(lon) * up
        val dz = cos(lat) * north + sin(lat) * up

        // Ray/sphere intersection: |observer + t*d|^2 = orbitRadius^2 with |d| = 1, i.e.
        // t^2 + 2*b*t + c = 0 where b = observer.d and c = |observer|^2 - orbitRadius^2.
        // Keep the farthest positive root (the ray exits the orbital sphere ahead of the observer).
        val b = observer.x * dx + observer.y * dy + observer.z * dz
        val c = observer.norm.let { it * it } - orbitRadiusKm * orbitRadiusKm
        val discriminant = b * b - c
        if (discriminant < 0) return null
        val t = -b + sqrt(discriminant)
        if (t <= 0) return null

        return EcefPosition(
            x = observer.x + t * dx,
            y = observer.y + t * dy,
            z = observer.z + t * dz,
        )
    }
}
