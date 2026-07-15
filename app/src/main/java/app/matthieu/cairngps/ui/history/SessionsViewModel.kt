package app.matthieu.cairngps.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A saved session paired with its track, ready for the sparkline preview on the Traces tab. */
data class SessionWithTrack(
    val session: Session,
    val track: List<TrackPoint>,
)

/**
 * State of the "Traces" tab of the Historique screen.
 *
 * @property sessions Saved sessions (each paired with its track for the row's sparkline), most
 *                    recently started first, or `null` while the first database load is still in
 *                    flight (waiting state, consistent with waypoints).
 */
data class SessionsUiState(
    val sessions: List<SessionWithTrack>? = null,
) {
    /** True once the list has loaded and contains no sessions. */
    val isEmpty: Boolean get() = sessions != null && sessions.isEmpty()
}

/**
 * Exposes the saved sessions — each paired with its track — as a [StateFlow] of [SessionsUiState]
 * for the Traces tab. Tracks are combined here rather than in the Composable, which only ever sees
 * this already-assembled state (see audit 4.1: a Composable used to collect `trackForSession`
 * directly from [SessionRepository]).
 */
class SessionsViewModel(
    repository: SessionRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionsUiState> = repository.sessions()
        .flatMapLatest { sessions ->
            if (sessions.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    sessions.map { session ->
                        repository.trackForSession(session.id).map { track -> SessionWithTrack(session, track) }
                    },
                ) { it.toList() }
            }
        }
        .map { SessionsUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // null sessions = still loading; distinct from an empty loaded list.
            initialValue = SessionsUiState(),
        )

    companion object {
        /** Factory that injects the [SessionRepository] without needing a DI framework. */
        fun factory(repository: SessionRepository): ViewModelProvider.Factory =
            factoryOf { SessionsViewModel(repository) }
    }
}
