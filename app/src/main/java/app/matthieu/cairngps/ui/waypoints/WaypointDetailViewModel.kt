package app.matthieu.cairngps.ui.waypoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the waypoint detail screen.
 *
 * @property waypoint The loaded waypoint, or `null` while loading (or once deleted).
 * @property session  The parent trace, if [waypoint] was captured during a recording; `null`
 *                    otherwise (or while loading).
 * @property deleted  True after the waypoint has been removed, signalling the screen to navigate back.
 */
data class WaypointDetailUiState(
    val waypoint: Waypoint? = null,
    val session: Session? = null,
    val deleted: Boolean = false,
)

/** Loads a single [Waypoint] by id — and its parent [Session], if any — and handles its deletion. */
class WaypointDetailViewModel(
    private val repository: WaypointRepository,
    private val sessionRepository: SessionRepository,
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

    /** Deletes the current waypoint and flips [WaypointDetailUiState.deleted] once done. */
    fun delete() {
        viewModelScope.launch {
            repository.delete(waypointId)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    companion object {
        /** Factory that injects the repositories and the waypoint id to load. */
        fun factory(
            repository: WaypointRepository,
            sessionRepository: SessionRepository,
            waypointId: Long,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return WaypointDetailViewModel(repository, sessionRepository, waypointId) as T
                }
            }
    }
}
