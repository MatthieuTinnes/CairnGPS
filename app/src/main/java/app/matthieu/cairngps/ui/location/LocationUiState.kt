package app.matthieu.cairngps.ui.location

import app.matthieu.cairngps.data.LocationData

/**
 * State of the GPS screen.
 *
 * A single immutable holder rather than a `Waiting | Fixed` hierarchy: the screen always shows the
 * same set of data cards and simply renders dashes until [fix] becomes non-null. Keeping the layout
 * stable across the first fix avoids the UI flickering / re-laying-out when data starts arriving.
 *
 * @property fix The most recent GPS fix, or `null` while none has been received yet.
 * @property isFixStale True when [fix] is no longer trustworthy: no update has arrived within the
 *                      staleness window, or the GPS provider was disabled. Cleared by the next fix.
 * @property satellitesUsedInFix Number of satellites currently used in the fix, or `null` while no
 *                              GNSS status has been received yet. Captured alongside a waypoint.
 * @property satellitesVisible Number of satellites currently visible (used or not), or `null`
 *                             while no GNSS status has been received yet.
 */
data class LocationUiState(
    val fix: LocationData? = null,
    val isFixStale: Boolean = false,
    val satellitesUsedInFix: Int? = null,
    val satellitesVisible: Int? = null,
) {
    /** True while a fresh fix is available (received within the staleness window). */
    val hasFix: Boolean get() = fix != null && !isFixStale

    /** True when a fix was acquired then lost — drives the "signal lost" status. */
    val isFixLost: Boolean get() = fix != null && isFixStale
}
