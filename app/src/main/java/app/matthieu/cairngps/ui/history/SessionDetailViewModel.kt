package app.matthieu.cairngps.ui.history

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
 * State of the trace detail screen.
 *
 * @property session   The loaded session, or `null` while loading (or once deleted).
 * @property waypoints Waypoints attached to this session, most recent first.
 * @property deleted   True after the session has been removed, signalling the screen to navigate back.
 */
data class SessionDetailUiState(
    val session: Session? = null,
    val waypoints: List<Waypoint> = emptyList(),
    val deleted: Boolean = false,
)

/** Loads a single [Session] by id together with its attached waypoints, and handles its deletion. */
class SessionDetailViewModel(
    private val sessionRepository: SessionRepository,
    waypointRepository: WaypointRepository,
    private val sessionId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(session = sessionRepository.get(sessionId)) }
        }
        viewModelScope.launch {
            waypointRepository.waypointsForSession(sessionId).collect { waypoints ->
                _uiState.update { it.copy(waypoints = waypoints) }
            }
        }
    }

    /**
     * Deletes the current session and flips [SessionDetailUiState.deleted] once done. Waypoints
     * attached to it are kept (their `sessionId` is cleared by the `SET NULL` foreign key).
     */
    fun delete() {
        viewModelScope.launch {
            sessionRepository.delete(sessionId)
            _uiState.update { it.copy(deleted = true) }
        }
    }

    /** Renames the current session to [name]. */
    fun rename(name: String) {
        viewModelScope.launch {
            sessionRepository.rename(sessionId, name)
            _uiState.update { state -> state.copy(session = state.session?.copy(name = name)) }
        }
    }

    companion object {
        /** Factory that injects the repositories and the session id to load. */
        fun factory(
            sessionRepository: SessionRepository,
            waypointRepository: WaypointRepository,
            sessionId: Long,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return SessionDetailViewModel(sessionRepository, waypointRepository, sessionId) as T
                }
            }
    }
}
