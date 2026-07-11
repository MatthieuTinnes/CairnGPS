package app.matthieu.cairngps.domain

import app.matthieu.cairngps.data.Constellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.math.acos
import kotlin.math.sqrt

class SatelliteGeometryTest {

    private val earthRadius = SatelliteGeometry.EARTH_RADIUS_KM

    // --- geodeticToEcef -------------------------------------------------------------------

    @Test
    fun `observer at equator and prime meridian maps to positive x axis`() {
        val ecef = SatelliteGeometry.geodeticToEcef(0.0, 0.0, 0.0)
        assertEquals(earthRadius, ecef.x, 1e-6)
        assertEquals(0.0, ecef.y, 1e-6)
        assertEquals(0.0, ecef.z, 1e-6)
    }

    @Test
    fun `north pole maps to positive z axis`() {
        val ecef = SatelliteGeometry.geodeticToEcef(90.0, 0.0, 0.0)
        assertEquals(0.0, ecef.x, 1e-6)
        assertEquals(0.0, ecef.y, 1e-6)
        assertEquals(earthRadius, ecef.z, 1e-6)
    }

    @Test
    fun `altitude in meters is added to the radius in kilometers`() {
        val ecef = SatelliteGeometry.geodeticToEcef(0.0, 0.0, 2_000.0)
        assertEquals(earthRadius + 2.0, ecef.x, 1e-6)
    }

    @Test
    fun `longitude 90 east maps to positive y axis`() {
        val ecef = SatelliteGeometry.geodeticToEcef(0.0, 90.0, 0.0)
        assertEquals(0.0, ecef.x, 1e-6)
        assertEquals(earthRadius, ecef.y, 1e-6)
        assertEquals(0.0, ecef.z, 1e-6)
    }

    // --- satelliteEcef --------------------------------------------------------------------

    @Test
    fun `satellite at zenith lies along the observer direction at the orbital radius`() {
        val gpsRadius = SatelliteGeometry.nominalOrbitRadiusKm(Constellation.GPS)
        val sat = SatelliteGeometry.satelliteEcef(
            observerLatitudeDeg = 0.0,
            observerLongitudeDeg = 0.0,
            observerAltitudeMeters = 0.0,
            azimuthDeg = 123.0, // Azimuth is irrelevant at 90 degrees elevation.
            elevationDeg = 90.0,
            orbitRadiusKm = gpsRadius,
        )
        assertNotNull(sat)
        assertEquals(gpsRadius, sat!!.x, 1e-6)
        assertEquals(0.0, sat.y, 1e-6)
        assertEquals(0.0, sat.z, 1e-6)
    }

    @Test
    fun `satellite on northern horizon from the equator sits in the x-z plane`() {
        val orbitRadius = 26_560.0
        val sat = SatelliteGeometry.satelliteEcef(
            observerLatitudeDeg = 0.0,
            observerLongitudeDeg = 0.0,
            observerAltitudeMeters = 0.0,
            azimuthDeg = 0.0,
            elevationDeg = 0.0,
            orbitRadiusKm = orbitRadius,
        )
        assertNotNull(sat)
        // Ray starts at (R, 0, 0) pointing north (+z): x stays at the Earth radius, y stays 0
        // and z grows until the ray meets the orbital sphere.
        assertEquals(earthRadius, sat!!.x, 1e-6)
        assertEquals(0.0, sat.y, 1e-6)
        val expectedZ = sqrt(orbitRadius * orbitRadius - earthRadius * earthRadius)
        assertEquals(expectedZ, sat.z, 1e-6)
    }

    @Test
    fun `result always lies on the requested orbital sphere`() {
        val orbitRadius = 29_600.0
        for (azimuth in 0..350 step 35) {
            for (elevation in 0..90 step 15) {
                val sat = SatelliteGeometry.satelliteEcef(
                    observerLatitudeDeg = 45.5,
                    observerLongitudeDeg = 6.5,
                    observerAltitudeMeters = 1_800.0,
                    azimuthDeg = azimuth.toDouble(),
                    elevationDeg = elevation.toDouble(),
                    orbitRadiusKm = orbitRadius,
                )
                assertNotNull("az=$azimuth el=$elevation", sat)
                assertEquals("az=$azimuth el=$elevation", orbitRadius, sat!!.norm, 1e-6)
            }
        }
    }

    @Test
    fun `elevation angle is recovered from the reconstructed position`() {
        val observerLat = 43.2
        val observerLon = 5.4
        val elevationDeg = 35.0
        val observer = SatelliteGeometry.geodeticToEcef(observerLat, observerLon, 0.0)
        val sat = SatelliteGeometry.satelliteEcef(
            observerLatitudeDeg = observerLat,
            observerLongitudeDeg = observerLon,
            observerAltitudeMeters = 0.0,
            azimuthDeg = 220.0,
            elevationDeg = elevationDeg,
            orbitRadiusKm = 26_560.0,
        )!!
        // Angle between the local vertical (observer direction, spherical Earth) and the line
        // of sight must equal 90 - elevation.
        val losX = sat.x - observer.x
        val losY = sat.y - observer.y
        val losZ = sat.z - observer.z
        val losNorm = sqrt(losX * losX + losY * losY + losZ * losZ)
        val dot = (losX * observer.x + losY * observer.y + losZ * observer.z) /
            (losNorm * observer.norm)
        val zenithAngleDeg = Math.toDegrees(acos(dot))
        assertEquals(90.0 - elevationDeg, zenithAngleDeg, 1e-6)
    }

    @Test
    fun `negative elevation still intersects the orbital sphere`() {
        // GnssStatus can report slightly negative elevations near the horizon.
        val sat = SatelliteGeometry.satelliteEcef(
            observerLatitudeDeg = 0.0,
            observerLongitudeDeg = 0.0,
            observerAltitudeMeters = 0.0,
            azimuthDeg = 90.0,
            elevationDeg = -2.0,
            orbitRadiusKm = 26_560.0,
        )
        assertNotNull(sat)
        assertEquals(26_560.0, sat!!.norm, 1e-6)
    }

    @Test
    fun `every constellation has a nominal radius beyond the Earth radius`() {
        Constellation.entries.forEach { constellation ->
            val radius = SatelliteGeometry.nominalOrbitRadiusKm(constellation)
            assertEquals("radius for $constellation", true, radius > earthRadius)
        }
    }
}
