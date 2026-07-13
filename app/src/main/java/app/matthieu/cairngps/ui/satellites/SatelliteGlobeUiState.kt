package app.matthieu.cairngps.ui.satellites

import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.SatelliteInfo
import app.matthieu.cairngps.domain.EcefPosition

/** A satellite in view together with its reconstructed approximate ECEF position (km). */
data class GlobeSatellite(
    val info: SatelliteInfo,
    val position: EcefPosition,
)

/**
 * State of the 3D satellite globe screen.
 *
 * @property observer    Latest GPS fix, or `null` while no fix is available yet.
 * @property hasGnssData True once at least one GNSS status snapshot has been received.
 * @property satellites  Satellites in view with their reconstructed 3D positions; empty until
 *                       both a fix and GNSS data are available.
 */
data class SatelliteGlobeUiState(
    val observer: LocationData? = null,
    val hasGnssData: Boolean = false,
    val satellites: List<GlobeSatellite> = emptyList(),
)
