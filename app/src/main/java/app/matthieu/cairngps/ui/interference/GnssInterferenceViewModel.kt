package app.matthieu.cairngps.ui.interference

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.GnssInterferenceRepository
import app.matthieu.cairngps.domain.InterferenceStatus
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Exposes the GNSS interference verdict to the screens that display it. [status] is `null` while
 * the detection is disabled in the settings.
 */
class GnssInterferenceViewModel(
    private val repository: GnssInterferenceRepository,
) : ViewModel() {

    private val _status = MutableStateFlow<InterferenceStatus?>(null)
    val status: StateFlow<InterferenceStatus?> = _status.asStateFlow()

    private var trackingJob: Job? = null

    /**
     * Starts following the detection. Idempotent. Tied to the screen lifecycle like the other GNSS
     * subscriptions, so raw measurements are only requested while the screen is visible. Must only
     * be called once [Manifest.permission.ACCESS_FINE_LOCATION] has been granted.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = repository.status()
            .onEach { _status.value = it }
            .launchIn(viewModelScope)
    }

    fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }

    companion object {
        fun factory(repository: GnssInterferenceRepository): ViewModelProvider.Factory =
            factoryOf { GnssInterferenceViewModel(repository) }
    }
}
