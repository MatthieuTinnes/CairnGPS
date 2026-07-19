package app.matthieu.cairngps.domain.format

import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.UnitSystem
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GpsFormattingTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    // --- accuracyQuality --------------------------------------------------------------------

    @Test
    fun `accuracyQuality is UNKNOWN when null`() {
        assertEquals(AccuracyQuality.UNKNOWN, accuracyQuality(null))
    }

    @Test
    fun `accuracyQuality is GOOD just under 5m`() {
        assertEquals(AccuracyQuality.GOOD, accuracyQuality(4.99f))
    }

    @Test
    fun `accuracyQuality is MEDIUM at exactly 5m`() {
        assertEquals(AccuracyQuality.MEDIUM, accuracyQuality(5f))
    }

    @Test
    fun `accuracyQuality is MEDIUM at exactly 15m`() {
        assertEquals(AccuracyQuality.MEDIUM, accuracyQuality(15f))
    }

    @Test
    fun `accuracyQuality is POOR just over 15m`() {
        assertEquals(AccuracyQuality.POOR, accuracyQuality(15.01f))
    }

    // --- formatCoordinate / DMS --------------------------------------------------------------

    @Test
    fun `formatCoordinate is DASH when null`() {
        assertEquals(DASH, formatCoordinate(null, isLatitude = true, format = CoordinateFormat.DECIMAL))
    }

    @Test
    fun `formatCoordinate DECIMAL uses six decimals`() {
        assertEquals(
            "47.123456°",
            formatCoordinate(47.123456, isLatitude = true, format = CoordinateFormat.DECIMAL),
        )
    }

    @Test
    fun `formatCoordinate DMS uses N for positive latitude`() {
        assertEquals(
            "45°30'00.0\"N",
            formatCoordinate(45.5, isLatitude = true, format = CoordinateFormat.DMS),
        )
    }

    @Test
    fun `formatCoordinate DMS uses S for negative latitude`() {
        assertEquals(
            "45°30'00.0\"S",
            formatCoordinate(-45.5, isLatitude = true, format = CoordinateFormat.DMS),
        )
    }

    @Test
    fun `formatCoordinate DMS uses E for positive longitude`() {
        assertEquals(
            "6°30'00.0\"E",
            formatCoordinate(6.5, isLatitude = false, format = CoordinateFormat.DMS),
        )
    }

    @Test
    fun `formatCoordinate DMS uses the given westLabel for negative longitude`() {
        assertEquals(
            "6°30'00.0\"O",
            formatCoordinate(-6.5, isLatitude = false, format = CoordinateFormat.DMS, westLabel = "O"),
        )
    }

    @Test
    fun `formatCoordinate DMS defaults to W for negative longitude`() {
        assertEquals(
            "6°30'00.0\"W",
            formatCoordinate(-6.5, isLatitude = false, format = CoordinateFormat.DMS),
        )
    }

    @Test
    fun `formatCoordinate DMS carries seconds into the next minute instead of showing 60`() {
        // 45 deg + 59 min + 59.96 sec -> rounds to 46 deg 00 min 00.0 sec, never "59'60.0".
        val value = 45.0 + 59.0 / 60.0 + 59.96 / 3600.0
        assertEquals(
            "46°00'00.0\"N",
            formatCoordinate(value, isLatitude = true, format = CoordinateFormat.DMS),
        )
    }

    @Test
    fun `formatCoordinate DMS rounds tenths of a second down when below the carry boundary`() {
        val value = 59.94 / 3600.0
        assertEquals(
            "0°00'59.9\"N",
            formatCoordinate(value, isLatitude = true, format = CoordinateFormat.DMS),
        )
    }

    // --- formatAltitude ------------------------------------------------------------------------

    @Test
    fun `formatAltitude is DASH when null`() {
        assertEquals(DASH, formatAltitude(null, UnitSystem.METRIC))
    }

    @Test
    fun `formatAltitude rounds metric meters`() {
        assertEquals("124", formatAltitude(123.6, UnitSystem.METRIC))
    }

    @Test
    fun `formatAltitude converts to feet for imperial`() {
        assertEquals("328", formatAltitude(100.0, UnitSystem.IMPERIAL))
    }

    // --- formatSpeed / formatSpeedSecondary -----------------------------------------------------

    @Test
    fun `formatSpeed is DASH when null`() {
        assertEquals(DASH, formatSpeed(null, UnitSystem.METRIC))
    }

    @Test
    fun `formatSpeed converts mps to kmh for metric`() {
        assertEquals("36.0", formatSpeed(10f, UnitSystem.METRIC))
    }

    @Test
    fun `formatSpeed converts mps to mph for imperial`() {
        assertEquals("22.4", formatSpeed(10f, UnitSystem.IMPERIAL))
    }

    @Test
    fun `formatSpeedSecondary passes through mps for metric`() {
        assertEquals("10.0", formatSpeedSecondary(10f, UnitSystem.METRIC))
    }

    @Test
    fun `formatSpeedSecondary converts to ftps for imperial`() {
        assertEquals("32.8", formatSpeedSecondary(10f, UnitSystem.IMPERIAL))
    }

    // --- formatAccuracy --------------------------------------------------------------------------

    @Test
    fun `formatAccuracy is DASH when null`() {
        assertEquals(DASH, formatAccuracy(null, UnitSystem.METRIC))
    }

    @Test
    fun `formatAccuracy passes through meters for metric`() {
        assertEquals("12.3", formatAccuracy(12.34f, UnitSystem.METRIC))
    }

    @Test
    fun `formatAccuracy converts to feet for imperial`() {
        assertEquals("40.5", formatAccuracy(12.34f, UnitSystem.IMPERIAL))
    }

    // --- formatCoordinatesForClipboard: always Locale.US -------------------------------------

    @Test
    fun `formatCoordinatesForClipboard always uses a dot regardless of default locale`() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("47.123457, 6.123456", formatCoordinatesForClipboard(47.1234567, 6.123456))
    }

    @Test
    fun `formatSpeed follows the default locale unlike the clipboard format`() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("36,0", formatSpeed(10f, UnitSystem.METRIC))
    }

    // --- formatDuration ----------------------------------------------------------------------

    @Test
    fun `formatDuration zero is 00-00`() {
        assertEquals("00:00", formatDuration(0))
    }

    @Test
    fun `formatDuration under an hour omits the hours component`() {
        assertEquals("59:59", formatDuration(3_599_000))
    }

    @Test
    fun `formatDuration at one hour includes hours and seconds by default`() {
        assertEquals("1:00:00", formatDuration(3_600_000))
    }

    @Test
    fun `formatDuration can drop seconds past one hour`() {
        assertEquals("1:00", formatDuration(3_600_000, showSecondsPastOneHour = false))
    }

    @Test
    fun `formatDuration coerces negative durations to zero`() {
        assertEquals("00:00", formatDuration(-5_000))
    }

    // --- formatDistance ------------------------------------------------------------------------

    @Test
    fun `formatDistance converts meters to km for metric`() {
        assertEquals("1.50", formatDistance(1500.0, UnitSystem.METRIC))
    }

    @Test
    fun `formatDistance converts meters to miles for imperial`() {
        assertEquals("0.93", formatDistance(1500.0, UnitSystem.IMPERIAL))
    }

    // --- shortDistanceValueAndUnit / formatShortDistance ----------------------------------------

    @Test
    fun `shortDistanceValueAndUnit stays in meters below the metric threshold`() {
        assertEquals("999" to "m", shortDistanceValueAndUnit(999.4, UnitSystem.METRIC))
    }

    @Test
    fun `shortDistanceValueAndUnit switches to km at 1000 meters`() {
        assertEquals("1.00" to "km", shortDistanceValueAndUnit(1000.0, UnitSystem.METRIC))
    }

    @Test
    fun `shortDistanceValueAndUnit stays in feet below the imperial threshold`() {
        assertEquals("525" to "ft", shortDistanceValueAndUnit(160.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `shortDistanceValueAndUnit switches to miles at 528 feet`() {
        assertEquals("0.10" to "mi", shortDistanceValueAndUnit(161.0, UnitSystem.IMPERIAL))
    }

    @Test
    fun `formatShortDistance combines value and unit into one string`() {
        assertEquals("999 m", formatShortDistance(999.4, UnitSystem.METRIC))
    }

    // --- formatElevation -----------------------------------------------------------------------

    @Test
    fun `formatElevation rounds metric meters`() {
        assertEquals("124", formatElevation(123.6, UnitSystem.METRIC))
    }

    @Test
    fun `formatElevation converts to feet for imperial`() {
        assertEquals("328", formatElevation(100.0, UnitSystem.IMPERIAL))
    }

    // --- unit labels -----------------------------------------------------------------------------

    @Test
    fun `shortUnitLabel is meters for metric and feet for imperial`() {
        assertEquals("m", shortUnitLabel(UnitSystem.METRIC))
        assertEquals("ft", shortUnitLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `speedUnitLabel is kmh for metric and mph for imperial`() {
        assertEquals("km/h", speedUnitLabel(UnitSystem.METRIC))
        assertEquals("mph", speedUnitLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `speedSecondaryUnitLabel is mps for metric and ftps for imperial`() {
        assertEquals("m/s", speedSecondaryUnitLabel(UnitSystem.METRIC))
        assertEquals("ft/s", speedSecondaryUnitLabel(UnitSystem.IMPERIAL))
    }

    @Test
    fun `distanceUnitLabel is km for metric and mi for imperial`() {
        assertEquals("km", distanceUnitLabel(UnitSystem.METRIC))
        assertEquals("mi", distanceUnitLabel(UnitSystem.IMPERIAL))
    }
}
