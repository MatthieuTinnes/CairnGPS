package app.matthieu.cairngps.domain

import app.matthieu.cairngps.data.CarrierBand
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.GnssBand
import app.matthieu.cairngps.data.GnssBandSample
import app.matthieu.cairngps.data.GnssSignalEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssInterferenceDetectorTest {

    private val gpsL1 = GnssBand(Constellation.GPS, CarrierBand.L1)
    private val galileoE5a = GnssBand(Constellation.GALILEO, CarrierBand.L5)

    private val clean = GnssBandSample(cn0DbHz = 40.0, agcDb = 3.0)

    private fun GnssInterferenceDetector.feed(
        epochs: Int,
        vararg bands: Pair<GnssBand, GnssBandSample>,
    ): InterferenceStatus {
        repeat(epochs) { onEvent(GnssSignalEvent.Measurements(bands.toMap())) }
        return status
    }

    /** Fills the baseline with [clean] epochs, then feeds [recent] for the 10-epoch recent window. */
    private fun detectorAfter(recent: GnssBandSample): InterferenceStatus =
        GnssInterferenceDetector().run {
            feed(50, gpsL1 to clean)
            feed(10, gpsL1 to recent)
        }

    @Test
    fun `starts waiting on both checks`() {
        val status = GnssInterferenceDetector().status
        assertEquals(SignalCheck.WAITING, status.signalCheck)
        assertEquals(TimeCheck.WAITING, status.timeCheck)
        assertFalse(status.isAlert)
    }

    @Test
    fun `warms up until both windows are full`() {
        val detector = GnssInterferenceDetector()
        assertEquals(SignalCheck.WARMING_UP, detector.feed(59, gpsL1 to clean).signalCheck)
        assertEquals(SignalCheck.OK, detector.feed(1, gpsL1 to clean).signalCheck)
    }

    @Test
    fun `stable signal stays ok`() {
        val status = GnssInterferenceDetector().feed(200, gpsL1 to clean)
        assertEquals(SignalCheck.OK, status.signalCheck)
        assertFalse(status.isAlert)
    }

    @Test
    fun `C N0 and AGC dropping together is jamming`() {
        val status = detectorAfter(GnssBandSample(cn0DbHz = 32.0, agcDb = -5.0))
        assertEquals(SignalCheck.JAMMING, status.signalCheck)
        assertEquals(listOf(gpsL1), status.affectedBands)
        assertTrue(status.isJammingSuspected)
        assertFalse(status.isSpoofingSuspected)
    }

    @Test
    fun `signals lost while AGC drops is jamming`() {
        val status = detectorAfter(GnssBandSample(cn0DbHz = null, agcDb = -5.0))
        assertEquals(SignalCheck.JAMMING, status.signalCheck)
    }

    @Test
    fun `C N0 rising while AGC drops is spoofing`() {
        val status = detectorAfter(GnssBandSample(cn0DbHz = 48.0, agcDb = -2.0))
        assertEquals(SignalCheck.SPOOFING, status.signalCheck)
        assertTrue(status.isSpoofingSuspected)
    }

    @Test
    fun `C N0 dropping with steady AGC is only obstruction`() {
        val status = detectorAfter(GnssBandSample(cn0DbHz = 28.0, agcDb = 3.0))
        assertEquals(SignalCheck.OK, status.signalCheck)
    }

    @Test
    fun `small AGC drop is ignored`() {
        val status = detectorAfter(GnssBandSample(cn0DbHz = 32.0, agcDb = 1.0))
        assertEquals(SignalCheck.OK, status.signalCheck)
    }

    @Test
    fun `only the jammed band is reported`() {
        val detector = GnssInterferenceDetector()
        detector.feed(50, gpsL1 to clean, galileoE5a to clean)
        val status = detector.feed(10, gpsL1 to GnssBandSample(30.0, -6.0), galileoE5a to clean)
        assertEquals(SignalCheck.JAMMING, status.signalCheck)
        assertEquals(listOf(gpsL1), status.affectedBands)
    }

    @Test
    fun `alert is held after the event then clears`() {
        val detector = GnssInterferenceDetector()
        detector.feed(50, gpsL1 to clean)
        detector.feed(10, gpsL1 to GnssBandSample(30.0, -6.0))

        assertEquals(SignalCheck.JAMMING, detector.feed(20, gpsL1 to clean).signalCheck)
        val cleared = detector.feed(100, gpsL1 to clean)
        assertEquals(SignalCheck.OK, cleared.signalCheck)
        assertTrue(cleared.affectedBands.isEmpty())
    }

    @Test
    fun `missing AGC makes the signal check unsupported`() {
        val detector = GnssInterferenceDetector()
        val noAgc = GnssBandSample(cn0DbHz = 40.0, agcDb = null)
        assertEquals(SignalCheck.WARMING_UP, detector.feed(9, gpsL1 to noAgc).signalCheck)
        assertEquals(SignalCheck.UNSUPPORTED, detector.feed(1, gpsL1 to noAgc).signalCheck)
    }

    @Test
    fun `no raw measurements makes the signal check unsupported`() {
        val status = GnssInterferenceDetector().onEvent(GnssSignalEvent.MeasurementsUnsupported)
        assertEquals(SignalCheck.UNSUPPORTED, status.signalCheck)
    }

    @Test
    fun `missing network time makes the time check unavailable`() {
        val status = GnssInterferenceDetector().onEvent(GnssSignalEvent.TimeOffset(null))
        assertEquals(TimeCheck.UNAVAILABLE, status.timeCheck)
        assertNull(status.timeOffsetMs)
    }

    @Test
    fun `small time offset is ok`() {
        val status = GnssInterferenceDetector().onEvent(GnssSignalEvent.TimeOffset(400L))
        assertEquals(TimeCheck.OK, status.timeCheck)
        assertEquals(400L, status.timeOffsetMs)
    }

    @Test
    fun `time offset over a second is spoofing once confirmed`() {
        val detector = GnssInterferenceDetector()
        detector.onEvent(GnssSignalEvent.TimeOffset(-3_000L))
        assertEquals(TimeCheck.OK, detector.onEvent(GnssSignalEvent.TimeOffset(-3_000L)).timeCheck)

        val spoofed = detector.onEvent(GnssSignalEvent.TimeOffset(-3_000L))
        assertEquals(TimeCheck.SPOOFING, spoofed.timeCheck)
        assertTrue(spoofed.isSpoofingSuspected)

        assertEquals(TimeCheck.OK, detector.onEvent(GnssSignalEvent.TimeOffset(100L)).timeCheck)
    }

    @Test
    fun `time and signal checks are independent`() {
        val detector = GnssInterferenceDetector()
        detector.feed(60, gpsL1 to clean)
        val status = detector.onEvent(GnssSignalEvent.TimeOffset(null))
        assertEquals(SignalCheck.OK, status.signalCheck)
        assertEquals(TimeCheck.UNAVAILABLE, status.timeCheck)
    }
}
