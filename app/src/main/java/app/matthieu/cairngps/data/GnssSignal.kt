package app.matthieu.cairngps.data

import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.os.Build
import kotlin.math.roundToInt

/**
 * Carrier frequency bands, bucketed from a raw carrier frequency. Several constellations share a
 * band (GPS L1, Galileo E1 and BeiDou B1C all sit on 1575.42 MHz); [GnssBand] pairs a band with its
 * constellation for display.
 */
enum class CarrierBand {
    L1,
    L2,
    L5,
    E5B,
    E6,
    B1I,
    G1,
    G2,
    OTHER;

    companion object {
        /**
         * Buckets [frequencyHz] into its band. GLONASS uses FDMA, so each of its satellites
         * transmits on a slightly different frequency: the G1/G2 ranges group them back together.
         * A missing frequency means L1, as documented for [GnssMeasurement.getCarrierFrequencyHz].
         */
        fun fromFrequencyHz(frequencyHz: Float?): CarrierBand {
            val mhz = frequencyHz?.let { (it / 1_000_000f).roundToInt() } ?: return L1
            return when (mhz) {
                in 1574..1577 -> L1
                in 1559..1563 -> B1I
                in 1592..1610 -> G1
                in 1240..1260 -> G2
                in 1226..1229 -> L2
                in 1175..1178 -> L5
                in 1206..1209 -> E5B
                in 1268..1280 -> E6
                else -> OTHER
            }
        }
    }
}

/** One constellation's signal on one carrier band, e.g. Galileo on L1 (E1). */
data class GnssBand(val constellation: Constellation, val band: CarrierBand) {

    /** Label in the constellation's own naming, e.g. "GPS L1", "Galileo E5a", "BeiDou B1C". */
    val label: String get() = "${constellation.displayName} ${bandName()}"

    private fun bandName(): String = when (constellation) {
        Constellation.GALILEO -> when (band) {
            CarrierBand.L1 -> "E1"
            CarrierBand.L5 -> "E5a"
            CarrierBand.E5B -> "E5b"
            else -> band.defaultName
        }
        Constellation.BEIDOU -> when (band) {
            CarrierBand.L1 -> "B1C"
            CarrierBand.L5 -> "B2a"
            CarrierBand.E5B -> "B2b"
            CarrierBand.E6 -> "B3"
            else -> band.defaultName
        }
        else -> band.defaultName
    }

    private val CarrierBand.defaultName: String
        get() = when (this) {
            CarrierBand.E5B -> "E5b"
            CarrierBand.B1I -> "B1I"
            CarrierBand.OTHER -> "?"
            else -> name
        }
}

/**
 * One epoch of raw measurements for a [GnssBand].
 *
 * @property cn0DbHz Mean carrier-to-noise density of the satellites tracked on this band, or
 *                   `null` when none is tracked (e.g. every signal was lost to jamming).
 * @property agcDb   Automatic gain control level in dB, or `null` when the device doesn't report
 *                   it. Strong in-band interference drives it more negative.
 */
data class GnssBandSample(val cn0DbHz: Double?, val agcDb: Double?)

/** Events backing the GNSS interference detection, see [LocationRepository.gnssSignalUpdates]. */
sealed interface GnssSignalEvent {

    /** One raw measurement epoch (~1 Hz), per band. */
    data class Measurements(val bands: Map<GnssBand, GnssBandSample>) : GnssSignalEvent

    /**
     * Network (NTP) time minus the GNSS time of the latest fix, in milliseconds, or `null` when
     * network time is unavailable (no connection, or Android < 13).
     */
    data class TimeOffset(val networkMinusGnssMs: Long?) : GnssSignalEvent

    /** The device doesn't provide raw GNSS measurements at all. */
    data object MeasurementsUnsupported : GnssSignalEvent
}

/** Groups this epoch's measurements and AGC levels by [GnssBand]. */
fun GnssMeasurementsEvent.toBandSamples(): Map<GnssBand, GnssBandSample> {
    val cn0ByBand = measurements
        .groupBy { bandOf(it.constellationType, it.carrierFrequencyOrNull()) }
        .mapValues { (_, signals) -> signals.map { it.cn0DbHz }.average() }

    val agcByBand: Map<GnssBand, Double> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && gnssAutomaticGainControls.isNotEmpty()) {
            gnssAutomaticGainControls
                .groupBy { bandOf(it.constellationType, it.carrierFrequencyHz.toFloat()) }
                .mapValues { (_, levels) -> levels.map { it.levelDb }.average() }
        } else {
            // Before API 33 AGC is only reported per measurement; on newer devices this is the
            // fallback for HALs that still fill it there instead of in the event-level list.
            @Suppress("DEPRECATION")
            measurements
                .filter { it.hasAutomaticGainControlLevelDb() }
                .groupBy { bandOf(it.constellationType, it.carrierFrequencyOrNull()) }
                .mapValues { (_, signals) -> signals.map { it.automaticGainControlLevelDb }.average() }
        }

    return (cn0ByBand.keys + agcByBand.keys).associateWith { band ->
        GnssBandSample(cn0DbHz = cn0ByBand[band], agcDb = agcByBand[band])
    }
}

private fun GnssMeasurement.carrierFrequencyOrNull(): Float? =
    if (hasCarrierFrequencyHz()) carrierFrequencyHz else null

private fun bandOf(constellationType: Int, frequencyHz: Float?) = GnssBand(
    constellation = Constellation.fromGnssConstellationType(constellationType),
    band = CarrierBand.fromFrequencyHz(frequencyHz),
)
