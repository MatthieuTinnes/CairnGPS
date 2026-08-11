package app.matthieu.cairngps.data

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocationDataTest {

    private fun locationOf(
        latitude: Double = 45.9,
        longitude: Double = 6.87,
        altitude: Double = 1035.0,
        speed: Float = 2.5f,
        accuracy: Float = 4.2f,
        verticalAccuracy: Float? = 3.1f,
        time: Long = 1_700_000_000_000L,
    ): Location = Location("gps").apply {
        this.latitude = latitude
        this.longitude = longitude
        this.altitude = altitude
        this.speed = speed
        this.accuracy = accuracy
        this.time = time
        if (verticalAccuracy != null) this.verticalAccuracyMeters = verticalAccuracy
    }

    @Test
    fun `toLocationData maps every field`() {
        val location = locationOf()

        val result = location.toLocationData(geoidSeparationMeters = 0.0)

        assertEquals(45.9, result.latitude, 0.0)
        assertEquals(6.87, result.longitude, 0.0)
        assertEquals(1035.0, result.altitude, 0.0)
        assertEquals(2.5f, result.speed)
        assertEquals(4.2f, result.horizontalAccuracy)
        assertEquals(1_700_000_000_000L, result.timestamp)
    }

    @Test
    fun `toLocationData subtracts the geoid separation from altitude`() {
        val location = locationOf(altitude = 150.0)

        val result = location.toLocationData(geoidSeparationMeters = 45.0)

        assertEquals(105.0, result.altitude, 0.0)
    }

    @Test
    fun `toLocationData exposes vertical accuracy when the fix has one`() {
        val location = locationOf(verticalAccuracy = 3.1f)

        val result = location.toLocationData(geoidSeparationMeters = 0.0)

        assertEquals(3.1f, result.verticalAccuracy)
    }

    @Test
    fun `toLocationData vertical accuracy is null when the fix does not report one`() {
        val location = locationOf(verticalAccuracy = null)

        val result = location.toLocationData(geoidSeparationMeters = 0.0)

        assertNull(result.verticalAccuracy)
    }

    @Test
    fun `toLocationData altitude defaults to zero minus the separation when never set`() {
        val location = Location("gps") // altitude never assigned: framework default is 0.0

        val result = location.toLocationData(geoidSeparationMeters = 17.2)

        assertEquals(-17.2, result.altitude, 0.0)
    }

    @Test
    fun `isAccurateEnough is true well within the threshold`() {
        val fix = locationOf(accuracy = 5f).toLocationData(geoidSeparationMeters = 0.0)

        assertEquals(true, fix.isAccurateEnough())
    }

    @Test
    fun `isAccurateEnough is true exactly at the threshold`() {
        val fix = locationOf(accuracy = 20f).toLocationData(geoidSeparationMeters = 0.0)

        assertEquals(true, fix.isAccurateEnough())
    }

    @Test
    fun `isAccurateEnough is false beyond the threshold`() {
        val fix = locationOf(accuracy = 25f).toLocationData(geoidSeparationMeters = 0.0)

        assertEquals(false, fix.isAccurateEnough())
    }
}
