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
 */
data class LocationUiState(
    val fix: LocationData? = null,
) {
    /** True once at least one fix has been received. */
    val hasFix: Boolean get() = fix != null
}
