package app.matthieu.cairngps.data

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresPermission
import app.matthieu.cairngps.domain.GnssInterferenceDetector
import app.matthieu.cairngps.domain.InterferenceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/** Which interference checks this device can run, as far as can be known before using the GPS. */
enum class InterferenceSupport {
    /** Raw measurements are available, or can't be ruled out (before Android 12). */
    AVAILABLE,

    /** No raw measurements: only the GNSS vs network time check can run. */
    TIME_ONLY,

    /** Neither check can run. */
    UNSUPPORTED;

    companion object {
        /**
         * The time check needs [android.os.SystemClock.currentNetworkTimeClock] (Android 13), the
         * signal check needs raw measurements. Whether those include AGC is only known once they
         * arrive, so a device without AGC still reports [AVAILABLE] here.
         */
        fun of(measurementsSupported: Boolean?, sdkInt: Int): InterferenceSupport = when {
            measurementsSupported != false -> AVAILABLE
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> TIME_ONLY
            else -> UNSUPPORTED
        }
    }
}

/**
 * Runs the GNSS jamming/spoofing detection while the user has enabled it in the settings, and
 * shares its verdict with every screen that shows it.
 */
class GnssInterferenceRepository(
    private val locationRepository: LocationRepository,
    settingsRepository: SettingsRepository,
) {
    // App-scoped like LocationRepository's: this repository is an application singleton.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val support: InterferenceSupport =
        InterferenceSupport.of(locationRepository.gnssMeasurementsSupported(), Build.VERSION.SDK_INT)

    /**
     * The current [InterferenceStatus], or `null` while the detection is disabled, including when
     * the setting is on but the device is [InterferenceSupport.UNSUPPORTED] (e.g. a backup
     * restored from another phone). One detector
     * backs every collector, so switching between screens doesn't reset its sliding windows; it
     * starts over once nobody has been collecting for a few seconds, since the windows would no
     * longer be contiguous. Raw measurements are only requested while enabled.
     *
     * The caller MUST hold [Manifest.permission.ACCESS_FINE_LOCATION] before collecting.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun status(): Flow<InterferenceStatus?> = sharedStatus

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sharedStatus: Flow<InterferenceStatus?> = settingsRepository.settings
        .map { it.gnssInterferenceDetection && support != InterferenceSupport.UNSUPPORTED }
        .distinctUntilChanged()
        .flatMapLatest { enabled -> if (enabled) detectionUpdates() else flowOf(null) }
        .distinctUntilChanged()
        .shareIn(
            scope,
            SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L, replayExpirationMillis = 0L),
            replay = 1,
        )

    // status() carries @RequiresPermission, but lint can't trace it through the property
    // initializer down to this helper.
    @SuppressLint("MissingPermission")
    private fun detectionUpdates(): Flow<InterferenceStatus> = flow {
        val detector = GnssInterferenceDetector()
        emit(detector.status)
        emitAll(locationRepository.gnssSignalUpdates().map(detector::onEvent))
    }
}
