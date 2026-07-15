package app.matthieu.cairngps.ui.location

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the GPS subscription and exposes it to the UI as a [StateFlow] of [LocationUiState].
 * Also captures the current fix as a persisted [Waypoint] on demand.
 */
class LocationViewModel(
    private val locationRepository: LocationRepository,
    private val waypointRepository: WaypointRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null

    /**
     * Starts collecting GPS fixes and GNSS status. Idempotent: calling it again while already
     * tracking is a no-op. Must only be called once [Manifest.permission.ACCESS_FINE_LOCATION]
     * has been granted.
     *
     * Tied to the screen lifecycle (started in ON_START, stopped in ON_STOP) so the GPS chip is
     * only powered while the screen is visible. The satellite stream is collected alongside the
     * fixes so a saved waypoint can record how many satellites were used at capture time.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = viewModelScope.launch {
            launch {
                locationRepository.locationUpdates().collect { fix ->
                    _uiState.update { it.copy(fix = fix) }
                }
            }
            launch {
                locationRepository.satelliteUpdates().collect { satellites ->
                    _uiState.update {
                        it.copy(
                            satellitesUsedInFix = satellites.count { s -> s.usedInFix },
                            satellitesVisible = satellites.size,
                        )
                    }
                }
            }
        }
    }

    /** Stops collecting GPS fixes and satellite status and releases the provider registrations. */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    /**
     * Captures the current GPS state as a named [Waypoint]. A no-op when no fix is available yet,
     * so callers should keep the save action disabled until [LocationUiState.hasFix] is true.
     *
     * If a recording is currently active, the new waypoint is automatically attached to it;
     * otherwise it is saved standalone. The attachment slot is reserved *before* the insert (see
     * [RecordingRepository.reserveWaypointAttachment]) so a concurrent [RecordingRepository.stop]
     * can never race ahead of a save that started while still recording.
     */
    fun saveWaypoint(name: String) {
        val state = _uiState.value
        val fix = state.fix ?: return
        viewModelScope.launch {
            val reserved = recordingRepository.reserveWaypointAttachment()
            val id = waypointRepository.save(
                Waypoint(
                    name = name,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    altitude = fix.altitude,
                    speed = fix.speed,
                    horizontalAccuracy = fix.horizontalAccuracy,
                    satellitesUsedInFix = state.satellitesUsedInFix,
                    timestamp = System.currentTimeMillis(),
                )
            )
            recordingRepository.completeWaypointAttachment(reserved, id)
        }
    }

    companion object {
        /** Factory that injects the repositories without needing a DI framework. */
        fun factory(
            locationRepository: LocationRepository,
            waypointRepository: WaypointRepository,
            recordingRepository: RecordingRepository,
        ): ViewModelProvider.Factory = factoryOf {
            LocationViewModel(locationRepository, waypointRepository, recordingRepository)
        }
    }
}
