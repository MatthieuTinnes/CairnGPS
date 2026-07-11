package app.matthieu.cairngps.ui.satellites

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.SatelliteInfo
import app.matthieu.cairngps.domain.SatelliteGeometry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Combines the location and GNSS status flows of the shared [LocationRepository] and exposes the
 * reconstructed 3D satellite positions as a [StateFlow] of [SatelliteGlobeUiState].
 *
 * Positions are recomputed on every GNSS snapshot and every fix update; the Composable only
 * projects and draws them.
 */
class SatelliteGlobeViewModel(
    private val repository: LocationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SatelliteGlobeUiState())
    val uiState: StateFlow<SatelliteGlobeUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null

    // Latest inputs, kept separately because the two flows emit at independent rates.
    private var latestObserver: LocationData? = null
    private var latestSatellites: List<SatelliteInfo>? = null

    /**
     * Starts collecting fixes and GNSS snapshots. Idempotent while already tracking. Tied to the
     * screen lifecycle (ON_START/ON_STOP) so nothing listens while the screen is not visible.
     * Must only be called once [Manifest.permission.ACCESS_FINE_LOCATION] has been granted.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            // Seed with the OS-cached fix so the globe can be oriented before the first live fix.
            if (latestObserver == null) latestObserver = repository.lastKnownLocation()
            publish()
            launch {
                repository.locationUpdates().collect { location ->
                    latestObserver = location
                    publish()
                }
            }
            launch {
                repository.satelliteUpdates().collect { satellites ->
                    latestSatellites = satellites
                    publish()
                }
            }
        }
    }

    /** Stops collecting; unregisters both the location and GNSS status callbacks. */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun publish() {
        val observer = latestObserver
        val satellites = latestSatellites
        _uiState.value = SatelliteGlobeUiState(
            observer = observer,
            hasGnssData = satellites != null,
            satellites = if (observer == null) {
                emptyList()
            } else {
                satellites.orEmpty()
                    .mapNotNull { info ->
                        SatelliteGeometry.satelliteEcef(observer, info)
                            ?.let { position -> GlobeSatellite(info, position) }
                    }
                    // Stable order so drawing and hit-testing stay consistent across snapshots.
                    .sortedWith(compareBy({ it.info.constellation.ordinal }, { it.info.svid }))
            },
        )
    }

    companion object {
        /** Factory that injects the [LocationRepository] without needing a DI framework. */
        fun factory(repository: LocationRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return SatelliteGlobeViewModel(repository) as T
                }
            }
    }
}
