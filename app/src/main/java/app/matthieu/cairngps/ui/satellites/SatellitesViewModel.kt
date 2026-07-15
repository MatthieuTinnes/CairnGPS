package app.matthieu.cairngps.ui.satellites

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Owns the GNSS status subscription and exposes it to the UI as a [StateFlow] of
 * [SatellitesUiState].
 */
class SatellitesViewModel(
    private val repository: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SatellitesUiState())
    val uiState: StateFlow<SatellitesUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null

    /**
     * Starts collecting GNSS satellite snapshots. Idempotent: calling it again while already
     * tracking is a no-op. Must only be called once
     * [Manifest.permission.ACCESS_FINE_LOCATION] has been granted.
     *
     * Tied to the screen lifecycle (started in ON_START, stopped in ON_STOP) so the GNSS callback
     * is only registered while the screen is visible.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = repository.satelliteUpdates()
            .onEach { satellites ->
                // Stable sort so rows don't jump around between GNSS snapshots.
                val sorted = satellites.sortedWith(
                    compareBy({ it.constellation.ordinal }, { it.svid }),
                )
                _uiState.value = SatellitesUiState(sorted)
            }
            .launchIn(viewModelScope)
    }

    /** Stops collecting and unregisters the GNSS status callback. */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    companion object {
        /** Factory that injects the [LocationRepository] without needing a DI framework. */
        fun factory(repository: LocationRepository): ViewModelProvider.Factory =
            factoryOf { SatellitesViewModel(repository) }
    }
}
