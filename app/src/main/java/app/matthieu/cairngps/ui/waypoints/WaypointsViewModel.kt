package app.matthieu.cairngps.ui.waypoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Exposes the saved waypoints as a [StateFlow] of [WaypointsUiState] for the list screen. */
class WaypointsViewModel(
    repository: WaypointRepository,
) : ViewModel() {

    val uiState: StateFlow<WaypointsUiState> = repository.waypoints()
        .map { WaypointsUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // null waypoints = still loading; distinct from an empty loaded list.
            initialValue = WaypointsUiState(),
        )

    companion object {
        /** Factory that injects the [WaypointRepository] without needing a DI framework. */
        fun factory(repository: WaypointRepository): ViewModelProvider.Factory =
            factoryOf { WaypointsViewModel(repository) }
    }
}
