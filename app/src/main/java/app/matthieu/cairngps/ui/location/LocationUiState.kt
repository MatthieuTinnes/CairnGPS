package app.matthieu.cairngps.ui.location

import app.matthieu.cairngps.data.LocationData

/** State of the GPS fix as observed by the UI. */
sealed interface LocationUiState {

    /** No fix received yet — the receiver is still acquiring satellites. */
    data object WaitingForFix : LocationUiState

    /** At least one fix has been received; [data] holds the most recent one. */
    data class Fixed(val data: LocationData) : LocationUiState
}
