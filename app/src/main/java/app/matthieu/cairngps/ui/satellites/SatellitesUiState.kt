package app.matthieu.cairngps.ui.satellites

import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.SatelliteInfo

/** Per-constellation counts shown in the summary section. */
data class ConstellationSummary(
    val constellation: Constellation,
    val inView: Int,
    val usedInFix: Int,
)

/**
 * State of the satellites screen.
 *
 * @property satellites Latest GNSS snapshot, sorted by constellation then satellite id so rows
 *                      keep a stable order across updates, or `null` while no [android.location.GnssStatus]
 *                      has been received yet (waiting state).
 */
data class SatellitesUiState(
    val satellites: List<SatelliteInfo>? = null,
) {
    /** True once at least one GNSS status snapshot has been received. */
    val hasData: Boolean get() = satellites != null

    val inViewCount: Int get() = satellites.orEmpty().size

    val usedInFixCount: Int get() = satellites.orEmpty().count { it.usedInFix }

    val constellationSummaries: List<ConstellationSummary>
        get() = satellites.orEmpty()
            .groupBy { it.constellation }
            .map { (constellation, sats) ->
                ConstellationSummary(
                    constellation = constellation,
                    inView = sats.size,
                    usedInFix = sats.count { it.usedInFix },
                )
            }
            .sortedBy { it.constellation.ordinal }

    /**
     * Satellites grouped by constellation, each group internally sorted by svid, and the groups
     * themselves ordered like [constellationSummaries] — backs the per-constellation sections in
     * the design's satellite list (screen 1d), replacing one flat list with a header per group.
     */
    val satellitesByConstellation: List<Pair<Constellation, List<SatelliteInfo>>>
        get() = satellites.orEmpty()
            .groupBy { it.constellation }
            .toList()
            .sortedBy { (constellation, _) -> constellation.ordinal }
            .map { (constellation, sats) -> constellation to sats.sortedBy { it.svid } }
}
