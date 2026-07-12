package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the id of the [Waypoint] currently targeted on the Naviguer (compass) screen.
 *
 * App-scoped and in-memory only, unlike the Room-backed repositories: the selected target is
 * transient navigation state, not persisted business data or a user preference, so it doesn't
 * belong in Room or DataStore (see CLAUDE.md). It does not need to survive process death — losing
 * it just means the user picks a target again.
 */
class NavigationTargetRepository {

    private val _targetWaypointId = MutableStateFlow<Long?>(null)
    val targetWaypointId: StateFlow<Long?> = _targetWaypointId.asStateFlow()

    /** Sets the waypoint to navigate toward. */
    fun setTarget(waypointId: Long) {
        _targetWaypointId.value = waypointId
    }

    /** Clears the current target, if any. */
    fun clear() {
        _targetWaypointId.value = null
    }
}
