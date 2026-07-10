package app.matthieu.cairngps.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * State of the "Traces" tab of the Historique screen.
 *
 * @property sessions Saved sessions, most recently started first, or `null` while the first
 *                    database load is still in flight (waiting state, consistent with waypoints).
 */
data class SessionsUiState(
    val sessions: List<Session>? = null,
) {
    /** True once the list has loaded and contains no sessions. */
    val isEmpty: Boolean get() = sessions != null && sessions.isEmpty()
}

/** Exposes the saved sessions as a [StateFlow] of [SessionsUiState] for the Traces tab. */
class SessionsViewModel(
    repository: SessionRepository,
) : ViewModel() {

    val uiState: StateFlow<SessionsUiState> = repository.sessions()
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
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return SessionsViewModel(repository) as T
                }
            }
    }
}
