package app.matthieu.cairngps.ui.recording

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.RecordingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * State of the live recording card on the Position screen.
 *
 * @property elapsedMs Live elapsed duration; recomputed from the recording's start time on every
 *                     tick so it keeps advancing even during a gap between GPS fixes.
 */
data class RecordingUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val distanceMeters: Double = 0.0,
    val averageSpeed: Float = 0f,
    val maxSpeed: Float = 0f,
    val elevationGain: Double = 0.0,
    val elevationLoss: Double = 0.0,
)

private fun RecordingState.toUiState(elapsedMs: Long): RecordingUiState = RecordingUiState(
    isRecording = isRecording,
    elapsedMs = elapsedMs,
    distanceMeters = distanceMeters,
    averageSpeed = averageSpeed,
    maxSpeed = maxSpeed,
    elevationGain = elevationGain,
    elevationLoss = elevationLoss,
)

/** 1-second ticker used only to keep [RecordingUiState.elapsedMs] advancing while recording. */
private val ticker: Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(1_000.milliseconds)
    }
}

/** Wraps [RecordingRepository] for the Position screen's recording card. */
class RecordingViewModel(
    private val recordingRepository: RecordingRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RecordingUiState> = recordingRepository.state
        .flatMapLatest { state ->
            if (!state.isRecording) {
                flowOf(state.toUiState(elapsedMs = 0L))
            } else {
                ticker.map { state.toUiState(elapsedMs = System.currentTimeMillis() - state.startTimestamp) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordingUiState(),
        )

    /** Starts a recording. Must only be called once location permission has been granted. */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun start() = recordingRepository.start()

    /** Stops the current recording, persisting it as a [app.matthieu.cairngps.data.Session]. */
    fun stop() {
        viewModelScope.launch { recordingRepository.stop() }
    }

    companion object {
        /** Factory that injects the [RecordingRepository] without needing a DI framework. */
        fun factory(recordingRepository: RecordingRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return RecordingViewModel(recordingRepository) as T
                }
            }
    }
}
