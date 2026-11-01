package app.matthieu.cairngps.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InterferenceSupportTest {

    @Test
    fun `raw measurements make both checks available`() {
        assertEquals(InterferenceSupport.AVAILABLE, InterferenceSupport.of(measurementsSupported = true, sdkInt = 31))
        assertEquals(InterferenceSupport.AVAILABLE, InterferenceSupport.of(measurementsSupported = true, sdkInt = 36))
    }

    @Test
    fun `unknown measurement support before Android 12 stays available`() {
        assertEquals(InterferenceSupport.AVAILABLE, InterferenceSupport.of(measurementsSupported = null, sdkInt = 26))
        assertEquals(InterferenceSupport.AVAILABLE, InterferenceSupport.of(measurementsSupported = null, sdkInt = 30))
    }

    @Test
    fun `no raw measurements on Android 13 or later leaves only the time check`() {
        assertEquals(InterferenceSupport.TIME_ONLY, InterferenceSupport.of(measurementsSupported = false, sdkInt = 33))
        assertEquals(InterferenceSupport.TIME_ONLY, InterferenceSupport.of(measurementsSupported = false, sdkInt = 37))
    }

    @Test
    fun `no raw measurements before Android 13 is unsupported`() {
        assertEquals(InterferenceSupport.UNSUPPORTED, InterferenceSupport.of(measurementsSupported = false, sdkInt = 31))
        assertEquals(InterferenceSupport.UNSUPPORTED, InterferenceSupport.of(measurementsSupported = false, sdkInt = 32))
    }
}
