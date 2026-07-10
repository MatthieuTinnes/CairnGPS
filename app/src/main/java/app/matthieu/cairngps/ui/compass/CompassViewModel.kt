package app.matthieu.cairngps.ui.compass

import android.Manifest
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.CompassReading
import app.matthieu.cairngps.data.CompassRepository
import app.matthieu.cairngps.data.LocationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CompassUiState(sensorAvailable = compassRepository.isSensorAvailable),
    )
    val uiState: StateFlow<CompassUiState> = _uiState.asStateFlow()

    private var trackingJob: Job? = null

    // Low-pass filter state: the last smoothed *magnetic* azimuth. Null until the first reading.
    private var smoothedMagnetic: Float? = null
    private var lastAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var declination: Float? = null
    private var useTrueNorth: Boolean = false

    /**
     * Starts listening to the compass sensor. Idempotent while already tracking. Tied to the screen
     * lifecycle (ON_START → ON_STOP) so the sensor is only active while the screen is visible.
     *
     * Needs [Manifest.permission.ACCESS_FINE_LOCATION] to read the last known position for the
     * declination; the compass itself works without it (declination just stays unavailable).
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startTracking() {
        if (trackingJob?.isActive == true) return

        // Declination is essentially constant over a session, so compute it once from the last
        // known position when the screen opens.
        declination = computeDeclination()
        publish()

        if (!compassRepository.isSensorAvailable) return

        trackingJob = compassRepository.headingUpdates()
            .onEach(::onReading)
            .launchIn(viewModelScope)
    }

    /** Stops listening and unregisters the sensor. Resets the filter so it re-seeds cleanly. */
    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
        smoothedMagnetic = null
    }

    /** Switches between magnetic and true (geographic) north. Ignored when declination is unknown. */
    fun setUseTrueNorth(value: Boolean) {
        val effective = value && declination != null
        if (effective == useTrueNorth) return
        useTrueNorth = effective
        publish()
    }

    private fun onReading(reading: CompassReading) {
        smoothedMagnetic = lowPass(reading.azimuthMagneticDegrees, smoothedMagnetic)
        lastAccuracy = reading.accuracy
        publish()
    }

    private fun publish() {
        val magnetic = smoothedMagnetic
        if (magnetic == null) {
            // No heading yet: keep the declination/north-reference facts visible while we wait.
            _uiState.value = _uiState.value.copy(
                hasData = false,
                useTrueNorth = useTrueNorth,
                declinationDegrees = declination,
            )
            return
        }
        val heading = normalize(magnetic + if (useTrueNorth) (declination ?: 0f) else 0f)
        _uiState.value = CompassUiState(
            sensorAvailable = true,
            hasData = true,
            headingDegrees = heading,
            cardinalIndex = cardinalIndex(heading),
            useTrueNorth = useTrueNorth,
            declinationDegrees = declination,
            needsCalibration = needsCalibration(lastAccuracy),
        )
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
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return CompassViewModel(compassRepository, locationRepository) as T
                }
            }
    }
}
