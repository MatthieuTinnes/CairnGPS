package app.matthieu.cairngps.ui.waypoints

import app.matthieu.cairngps.data.Waypoint

/**
 * State of the waypoints list screen.
 *
 * @property waypoints Saved waypoints, most recent first, or `null` while the first database load
 *                     is still in flight (waiting state, consistent with the other screens).
 */
data class WaypointsUiState(
    val waypoints: List<Waypoint>? = null,
) {
    /** True once the list has loaded and contains no waypoints. */
    val isEmpty: Boolean get() = waypoints != null && waypoints.isEmpty()
}
