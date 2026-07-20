package app.matthieu.cairngps.ui.compass

import android.Manifest
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.CompassReading
import app.matthieu.cairngps.data.CompassRepository
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.NavigationTargetRepository
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.distanceAndBearing
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Owns the compass sensor subscription and turns raw magnetic headings into [CompassUiState].
 *
 * Two bits of processing happen here rather than in the repository, keeping the repository a thin
 * sensor wrapper:
 *  - a low-pass filter that stops the needle from trembling, and
 *  - the magnetic → true north conversion using [GeomagneticField] seeded from the last known GPS
 *    position.
 */
class CompassViewModel(
    private val compassRepository: CompassRepository,
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val waypointRepository: WaypointRepository,
    private val navigationTargetRepository: NavigationTargetRepository,
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CompassUiState(sensorAvailable = compassRepository.isSensorAvailable),
    )
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null
    private var locationJob: Job? = null

    // Low-pass filter state: the last smoothed *magnetic* azimuth. Null until the first reading.
    private var smoothedMagnetic: Float? = null
    private var lastAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var declination: Float? = null

    // North reference and unit system are persisted preferences (set from the Settings screen),
    // not local UI state.
    private var northReference: NorthReference = NorthReference.MAGNETIC
    private var unitSystem: UnitSystem = UnitSystem.METRIC

    // Target waypoint (selected from the "Changer de repère cible" picker) and the last known
    // position, combined to derive the bearing/distance shown on the target card. Neither is tied
    // to the sensor tracking lifecycle by itself — the target can be picked/loaded at any time —
    // but the position stream (locationJob) only runs while the screen is visible.
    private var targetWaypoint: Waypoint? = null
    private var currentLocation: LocationData? = null

    // Backs the "Changer de repère cible" picker; kept live regardless of screen visibility, like
    // the settings/target-id observers below, since it's a cheap Room-backed flow.
    private var waypoints: List<Waypoint> = emptyList()

    init {
        settingsRepository.settings
            .onEach { settings ->
                northReference = settings.northReference
                unitSystem = settings.unitSystem
                publish()
            }
            .launchIn(viewModelScope)

        navigationTargetRepository.targetWaypointId
            .onEach { id ->
                targetWaypoint = id?.let { waypointRepository.get(it) }
                publish()
            }
            .launchIn(viewModelScope)

        waypointRepository.waypoints()
            .onEach { list ->
                waypoints = list
                publish()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Starts listening to the compass sensor (and, for the target bearing/distance, GPS updates).
     * Idempotent while already tracking. Tied to the screen lifecycle (ON_START → ON_STOP) so
     * both stay active only while the screen is visible.
     *
     * Needs [Manifest.permission.ACCESS_FINE_LOCATION] to read the last known position for the
     * declination and to track position for the target bearing; the compass itself works without
     * it (declination and the target bearing just stay unavailable).
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startTracking() {
        if (trackingJob?.isActive == true) return

        // Declination is essentially constant over a session, so compute it once from the last
        // known position when the screen opens.
        declination = computeDeclination()
        publish()

        locationJob = locationRepository.locationUpdates()
            .onEach { location ->
                currentLocation = location
                publish()
            }
            .launchIn(viewModelScope)

        if (!compassRepository.isSensorAvailable) return

        trackingJob = compassRepository.headingUpdates()
            .onEach(::onReading)
            .launchIn(viewModelScope)
    }

    /** Stops listening and unregisters the sensor/GPS. Resets the filter so it re-seeds cleanly. */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        locationJob?.cancel()
        locationJob = null
        smoothedMagnetic = null
    }

    /** Sets the waypoint to navigate toward, invoked from the target picker. */
    fun setTarget(waypointId: Long) {
        navigationTargetRepository.setTarget(waypointId)
    }

    /**
     * Creates a waypoint at the current position (invoked from the target picker's "Créer un
     * nouveau repère ici" row) and immediately makes it the navigation target. A no-op when no
     * position is available yet.
     *
     * If a recording is currently active, the new waypoint is automatically attached to it, same
     * as [app.matthieu.cairngps.ui.location.LocationViewModel.saveWaypoint] — see
     * [RecordingRepository.reserveWaypointAttachment] for why the slot must be reserved before the
     * insert.
     */
    fun createWaypointHere(name: String) {
        val location = currentLocation ?: return
        viewModelScope.launch {
            val reserved = recordingRepository.reserveWaypointAttachment()
            val id = waypointRepository.save(
                Waypoint(
                    name = name,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    speed = location.speed,
                    horizontalAccuracy = location.horizontalAccuracy,
                    // The compass screen doesn't observe GnssStatus, so satellite count is unknown.
                    satellitesUsedInFix = null,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            recordingRepository.completeWaypointAttachment(reserved, id)
            navigationTargetRepository.setTarget(id)
        }
    }

    private fun onReading(reading: CompassReading) {
        smoothedMagnetic = lowPass(reading.azimuthMagneticDegrees, smoothedMagnetic)
        lastAccuracy = reading.accuracy
        publish()
    }

    private fun publish() {
        // True north requires both the user preference and a known declination; falls back to
        // magnetic when no GPS position was available to compute the declination.
        val useTrueNorth = northReference == NorthReference.TRUE && declination != null

        val target = targetWaypoint
        val location = currentLocation
        var targetDistance: Double? = null
        var targetBearing: Float? = null
        if (target != null && location != null) {
            val toTarget = distanceAndBearing(
                location.latitude, location.longitude,
                target.latitude, target.longitude,
            )
            targetDistance = toTarget.distanceMeters
            // The computed bearing is always relative to true north; convert it into the same
            // reference as headingDegrees below (magnetic unless useTrueNorth), or the needle would
            // be off by the declination whenever the compass is in magnetic mode.
            targetBearing = normalize(toTarget.bearingTrueDegrees - if (useTrueNorth) 0f else (declination ?: 0f))
        }

        // Base state carries the facts that are known regardless of whether a heading has been
        // read yet (declination, north reference, target); the magnetic-dependent fields are
        // overlaid below only once a first sensor reading has arrived.
        var state = CompassUiState(
            sensorAvailable = compassRepository.isSensorAvailable,
            useTrueNorth = useTrueNorth,
            declinationDegrees = declination,
            targetName = target?.name,
            targetIcon = target?.icon,
            targetDistanceMeters = targetDistance,
            bearingToTargetDegrees = targetBearing,
            waypoints = waypoints,
            targetWaypointId = target?.id,
            unitSystem = unitSystem,
        )

        val magnetic = smoothedMagnetic
        if (magnetic != null) {
            val heading = normalize(magnetic + if (useTrueNorth) (declination ?: 0f) else 0f)
            state = state.copy(
                hasData = true,
                headingDegrees = heading,
                cardinalIndex = cardinalIndex(heading),
                needsCalibration = needsCalibration(lastAccuracy),
            )
        }

        _uiState.value = state
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun computeDeclination(): Float? {
        val location = locationRepository.lastKnownLocation() ?: return null
        return GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            location.altitude.toFloat(),
            System.currentTimeMillis(),
        ).declination
    }

    /**
     * Exponential low-pass filter that follows the *shortest* angular path, so the value stays
     * stable across the 0°/360° wrap-around instead of spinning the whole way round.
     */
    private fun lowPass(newAngle: Float, current: Float?): Float {
        if (current == null) return newAngle
        var diff = newAngle - current
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return normalize(current + SMOOTHING_ALPHA * diff)
    }

    private fun normalize(angle: Float): Float = (angle % 360f + 360f) % 360f

    /** Maps a heading to one of the 8 cardinal sectors (each 45° wide, centered on the point). */
    private fun cardinalIndex(heading: Float): Int = (heading / 45f).roundToInt() % 8

    private fun needsCalibration(accuracy: Int): Boolean =
        accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW

    companion object {
        /**
         * Smoothing factor for the low-pass filter (0..1). Lower = smoother but laggier. At the
         * ~16 Hz UI sensor rate, 0.15 gives a ~400 ms response: steady needle, still responsive.
         */
        private const val SMOOTHING_ALPHA = 0.15f

        /** Factory injecting the repositories without a DI framework. */
        fun factory(
            compassRepository: CompassRepository,
            locationRepository: LocationRepository,
            settingsRepository: SettingsRepository,
            waypointRepository: WaypointRepository,
            navigationTargetRepository: NavigationTargetRepository,
            recordingRepository: RecordingRepository,
        ): ViewModelProvider.Factory = factoryOf {
            CompassViewModel(
                compassRepository,
                locationRepository,
                settingsRepository,
                waypointRepository,
                navigationTargetRepository,
                recordingRepository,
            )
        }
    }
}
