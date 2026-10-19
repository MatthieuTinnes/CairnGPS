package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Naviguer (compass) screen points at: a saved waypoint or free-form coordinates. */
sealed interface NavigationTarget {
    data class SavedWaypoint(val id: Long) : NavigationTarget
    data class Coordinates(val latitude: Double, val longitude: Double) : NavigationTarget
}

/**
 * Holds the [NavigationTarget] currently selected on the Naviguer (compass) screen.
 *
 * App-scoped and in-memory only, unlike the Room-backed repositories: the selected target is
 * transient navigation state, not persisted business data or a user preference, so it doesn't
 * belong in Room or DataStore (see CLAUDE.md). It does not need to survive process death — losing
 * it just means the user picks a target again.
 */
class NavigationTargetRepository {

    private val _target = MutableStateFlow<NavigationTarget?>(null)
    val target: StateFlow<NavigationTarget?> = _target.asStateFlow()

    /** Sets the waypoint to navigate toward. */
    fun setTarget(waypointId: Long) {
        _target.value = NavigationTarget.SavedWaypoint(waypointId)
    }

    /** Sets arbitrary coordinates (decimal degrees) to navigate toward. */
    fun setTarget(latitude: Double, longitude: Double) {
        _target.value = NavigationTarget.Coordinates(latitude, longitude)
    }

    /** Clears the current target, if any. */
    fun clear() {
        _target.value = null
    }
}
