package app.matthieu.cairngps.ui.waypoints

import android.Manifest
import android.location.Location
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the waypoint detail screen.
 *
 * @property waypoint             The loaded waypoint, or `null` while loading (or once deleted).
 * @property session               The parent trace, if [waypoint] was captured during a recording;
 *                                  `null` otherwise (or while loading).
 * @property currentDistanceMeters Distance from the last known position to this waypoint, or
 *                                  `null` if no position is known yet.
 * @property deleted              True after the waypoint has been removed, signalling the screen
 *                                 to navigate back.
 */
data class WaypointDetailUiState(
    val waypoint: Waypoint? = null,
    val session: Session? = null,
    val currentDistanceMeters: Double? = null,
    val deleted: Boolean = false,
)

/** Loads a single [Waypoint] by id — and its parent [Session], if any — and handles its deletion. */
class WaypointDetailViewModel(
    private val repository: WaypointRepository,
    private val sessionRepository: SessionRepository,
    private val locationRepository: LocationRepository,
    private val waypointId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WaypointDetailUiState())
    val uiState: StateFlow<WaypointDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val waypoint = repository.get(waypointId)
            val session = waypoint?.sessionId?.let { sessionRepository.get(it) }
            _uiState.update { it.copy(waypoint = waypoint, session = session) }
        }
    }

    /**
     * Takes a single last-known-position snapshot and computes the distance to this waypoint. A
     * one-shot read rather than a live subscription: this is a static detail page, not a tracking
     * screen, so it doesn't need its own ON_START/ON_STOP GPS stream — an approximate
     * "distance actuelle" is enough here; the Naviguer tab is where a live, continuously updated
     * bearing/distance belongs. Called once from the Route, which is only ever composed under the
     * location-permission gate.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun refreshCurrentDistance() {
        val waypoint = _uiState.value.waypoint ?: return
        val location = locationRepository.lastKnownLocation() ?: return
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            waypoint.latitude, waypoint.longitude,
            results,
        )
        _uiState.update { it.copy(currentDistanceMeters = results[0].toDouble()) }
    }

    /** Deletes the current waypoint and flips [WaypointDetailUiState.deleted] once done. */
    fun delete() {
        viewModelScope.launch {
            repository.delete(waypointId)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    /** Renames the current waypoint to [name] and sets its icon to [icon] in one write. */
    fun edit(name: String, icon: String) {
        viewModelScope.launch {
            repository.edit(waypointId, name, icon)
            _uiState.update { state -> state.copy(waypoint = state.waypoint?.copy(name = name, icon = icon)) }
        }
    }

    /** Changes the current waypoint's icon to [icon], independent of a rename (screen 1i avatar). */
    fun setIcon(icon: String) {
        viewModelScope.launch {
            repository.setIcon(waypointId, icon)
            _uiState.update { state -> state.copy(waypoint = state.waypoint?.copy(icon = icon)) }
        }
    }

    companion object {
        /** Factory that injects the repositories and the waypoint id to load. */
        fun factory(
            repository: WaypointRepository,
            sessionRepository: SessionRepository,
            locationRepository: LocationRepository,
            waypointId: Long,
        ): ViewModelProvider.Factory = factoryOf {
            WaypointDetailViewModel(repository, sessionRepository, locationRepository, waypointId)
        }
    }
}
