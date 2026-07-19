package app.matthieu.cairngps.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses the real [android.location.Location.distanceBetween] under the hood, which is why this
 * class needs [RobolectricTestRunner] (same reason as `RecordingRepositoryTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeodesyTest {

    @Test
    fun `one millidegree of latitude is about 111 meters due north`() {
        val result = distanceAndBearing(47.0, 6.0, 47.001, 6.0)
        assertEquals(111.0, result.distanceMeters, 1.0)
        assertEquals(0f, result.bearingTrueDegrees, 0.01f)
    }

    @Test
    fun `heading east from the equator bears 90 degrees`() {
        val result = distanceAndBearing(0.0, 6.0, 0.0, 6.001)
        assertEquals(111.0, result.distanceMeters, 1.0)
        assertEquals(90f, result.bearingTrueDegrees, 0.01f)
    }

    @Test
    fun `identical points are at distance zero`() {
        val result = distanceAndBearing(47.0, 6.0, 47.0, 6.0)
        assertEquals(0.0, result.distanceMeters, 0.0)
    }
}
