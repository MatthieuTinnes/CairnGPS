package app.matthieu.cairngps.ui.location

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.LocationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Owns the GPS subscription and exposes it to the UI as a [StateFlow] of [LocationUiState].
 */
class LocationViewModel(
    private val repository: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null

    /**
     * Starts collecting GPS fixes. Idempotent: calling it again while already tracking is a no-op.
     * Must only be called once [Manifest.permission.ACCESS_FINE_LOCATION] has been granted.
     *
     * Tied to the screen lifecycle (started in ON_START, stopped in ON_STOP) so the GPS chip is
     * only powered while the screen is visible.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = repository.locationUpdates()
            .onEach { fix -> _uiState.value = LocationUiState(fix) }
            .launchIn(viewModelScope)
    }

    /** Stops collecting GPS fixes and releases the provider registration. */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    companion object {
        /** Factory that injects the [LocationRepository] without needing a DI framework. */
        fun factory(repository: LocationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return LocationViewModel(repository) as T
                }
            }
    }
}
