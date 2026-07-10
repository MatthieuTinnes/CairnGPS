package app.matthieu.cairngps.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes [AppSettings] as a [StateFlow] and writes changes back through the [SettingsRepository].
 * Shared by the home and settings screens; DataStore keeps every observer in sync.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setCoordinateFormat(format: CoordinateFormat) {
        viewModelScope.launch { repository.setCoordinateFormat(format) }
    }

    companion object {
        fun factory(repository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return SettingsViewModel(repository) as T
                }
            }
    }
}
