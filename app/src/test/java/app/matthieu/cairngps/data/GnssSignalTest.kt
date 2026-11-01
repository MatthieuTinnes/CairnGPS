package app.matthieu.cairngps.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssSignalTest {

    @Test
    fun `carrier frequencies are bucketed into bands`() {
        assertEquals(CarrierBand.L1, CarrierBand.fromFrequencyHz(1_575.42e6f))
        assertEquals(CarrierBand.L5, CarrierBand.fromFrequencyHz(1_176.45e6f))
        assertEquals(CarrierBand.L2, CarrierBand.fromFrequencyHz(1_227.60e6f))
        assertEquals(CarrierBand.B1I, CarrierBand.fromFrequencyHz(1_561.098e6f))
        assertEquals(CarrierBand.E6, CarrierBand.fromFrequencyHz(1_278.75e6f))
        assertEquals(CarrierBand.OTHER, CarrierBand.fromFrequencyHz(2_492.028e6f))
    }

    @Test
    fun `GLONASS FDMA channels share one band`() {
        assertEquals(CarrierBand.G1, CarrierBand.fromFrequencyHz(1_598.0625e6f))
        assertEquals(CarrierBand.G1, CarrierBand.fromFrequencyHz(1_605.375e6f))
    }

    @Test
    fun `missing carrier frequency means L1`() {
        assertEquals(CarrierBand.L1, CarrierBand.fromFrequencyHz(null))
    }

    @Test
    fun `band labels follow each constellation's naming`() {
        assertEquals("GPS L1", GnssBand(Constellation.GPS, CarrierBand.L1).label)
        assertEquals("GPS L5", GnssBand(Constellation.GPS, CarrierBand.L5).label)
        assertEquals("Galileo E1", GnssBand(Constellation.GALILEO, CarrierBand.L1).label)
        assertEquals("Galileo E5a", GnssBand(Constellation.GALILEO, CarrierBand.L5).label)
        assertEquals("BeiDou B1C", GnssBand(Constellation.BEIDOU, CarrierBand.L1).label)
        assertEquals("GLONASS G1", GnssBand(Constellation.GLONASS, CarrierBand.G1).label)
    }
}
