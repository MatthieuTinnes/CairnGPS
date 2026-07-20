package app.matthieu.cairngps.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.GpxExporter
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.common.factoryOf
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-shot outcome of a GPX export attempt, consumed by the session detail screen as a snackbar. */
sealed interface GpxExportEvent {
    data object Success : GpxExportEvent
    data object Error : GpxExportEvent
}

/**
 * State of the trace detail screen.
 *
 * @property session   The loaded session, or `null` while loading (or once deleted).
 * @property waypoints Waypoints attached to this session, most recent first.
 * @property track     Chronologically ordered track points sampled during the recording, backing
 *                      the altitude profile / route trace. Empty for sessions recorded before this
 *                      feature existed, or too short to have sampled any point.
 * @property deleted   True after the session has been removed, signalling the screen to navigate back.
 */
data class SessionDetailUiState(
    val session: Session? = null,
    val waypoints: List<Waypoint> = emptyList(),
    val track: List<TrackPoint> = emptyList(),
    val deleted: Boolean = false,
)

/** Loads a single [Session] by id together with its attached waypoints, and handles its deletion. */
class SessionDetailViewModel(
    private val sessionRepository: SessionRepository,
    waypointRepository: WaypointRepository,
    private val sessionId: Long,
) : ViewModel() {

    private val gpxExporter = GpxExporter()

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    private val _isExporting = MutableStateFlow(false)

    /** True while a GPX export is in flight — the screen disables the export action meanwhile. */
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportEvents = MutableSharedFlow<GpxExportEvent>(extraBufferCapacity = 4)
    val exportEvents: SharedFlow<GpxExportEvent> = _exportEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(session = sessionRepository.get(sessionId)) }
        }
        viewModelScope.launch {
            waypointRepository.waypointsForSession(sessionId).collect { waypoints ->
                _uiState.update { it.copy(waypoints = waypoints) }
            }
        }
        viewModelScope.launch {
            sessionRepository.trackForSession(sessionId).collect { track ->
                _uiState.update { it.copy(track = track) }
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

    /**
     * Writes the current session's track and waypoints to [output], a stream the caller opened
     * (via the system save dialog) and this will close. No-ops if the session hasn't loaded yet.
     */
    fun exportGpx(output: OutputStream) {
        val state = _uiState.value
        val session = state.session ?: return
        viewModelScope.launch {
            _isExporting.value = true
            val event = try {
                gpxExporter.export(session, state.track, state.waypoints, output)
                GpxExportEvent.Success
            } catch (e: IOException) {
                GpxExportEvent.Error
            }
            _isExporting.value = false
            _exportEvents.emit(event)
        }
    }

    companion object {
        /** Factory that injects the repositories and the session id to load. */
        fun factory(
            sessionRepository: SessionRepository,
            waypointRepository: WaypointRepository,
            sessionId: Long,
        ): ViewModelProvider.Factory = factoryOf {
            SessionDetailViewModel(sessionRepository, waypointRepository, sessionId)
        }
    }
}
