package app.matthieu.cairngps.data

import android.location.GnssStatus

/**
 * GNSS constellations, mapped from the [GnssStatus.getConstellationType] constants
 * to human-readable names.
 */
enum class Constellation(val displayName: String) {
    GPS("GPS"),
    GLONASS("GLONASS"),
    GALILEO("Galileo"),
    BEIDOU("BeiDou"),
    QZSS("QZSS"),
    SBAS("SBAS"),
    IRNSS("IRNSS"),
    UNKNOWN("?");

    companion object {
        fun fromGnssConstellationType(type: Int): Constellation = when (type) {
            GnssStatus.CONSTELLATION_GPS -> GPS
            GnssStatus.CONSTELLATION_GLONASS -> GLONASS
            GnssStatus.CONSTELLATION_GALILEO -> GALILEO
            GnssStatus.CONSTELLATION_BEIDOU -> BEIDOU
            GnssStatus.CONSTELLATION_QZSS -> QZSS
            GnssStatus.CONSTELLATION_SBAS -> SBAS
            GnssStatus.CONSTELLATION_IRNSS -> IRNSS
            else -> UNKNOWN
        }
    }
}

/**
 * A single satellite as reported by a [GnssStatus] snapshot.
 *
 * @property constellation    Constellation this satellite belongs to.
 * @property svid             Satellite identifier, unique within its constellation.
 * @property cn0DbHz          Carrier-to-noise density, in dB-Hz (signal strength).
 * @property usedInFix        Whether this satellite was used to compute the current fix.
 * @property azimuthDegrees   Azimuth in degrees, clockwise from true north.
 * @property elevationDegrees Elevation above the horizon, in degrees.
 */
data class SatelliteInfo(
    val constellation: Constellation,
    val svid: Int,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
)

/**
 * Flattens a [GnssStatus] snapshot into one [SatelliteInfo] per physical satellite.
 *
 * Dual-frequency devices report one [GnssStatus] entry per tracked signal (e.g. L1 and L5) for
 * the same satellite, so (constellation, svid) can appear several times in a snapshot. Those
 * entries are collapsed here: strongest signal wins, and the satellite counts as used in the fix
 * if any of its signals does.
 */
fun GnssStatus.toSatelliteInfoList(): List<SatelliteInfo> =
    (0 until satelliteCount)
        .map { i ->
            SatelliteInfo(
                constellation = Constellation.fromGnssConstellationType(getConstellationType(i)),
                svid = getSvid(i),
                cn0DbHz = getCn0DbHz(i),
                usedInFix = usedInFix(i),
                azimuthDegrees = getAzimuthDegrees(i),
                elevationDegrees = getElevationDegrees(i),
            )
        }
        .groupBy { it.constellation to it.svid }
        .map { (_, signals) ->
            signals.maxBy { it.cn0DbHz }
                .copy(usedInFix = signals.any { it.usedInFix })
        }
