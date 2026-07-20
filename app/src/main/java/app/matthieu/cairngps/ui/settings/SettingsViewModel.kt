package app.matthieu.cairngps.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.ThemeMode
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.ui.common.factoryOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes [AppSettings] as a [StateFlow] and writes changes back through the [SettingsRepository].
 * Shared by the home, records and settings screens; DataStore keeps every observer in sync.
 *
 * Backup export/import is handled by the separate [BackupViewModel] (only the Settings screen
 * needs it), so this class stays a lightweight settings reader for every other screen.
 */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setCoordinateFormat(format: CoordinateFormat) {
        viewModelScope.launch { repository.setCoordinateFormat(format) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setNorthReference(reference: NorthReference) {
        viewModelScope.launch { repository.setNorthReference(reference) }
    }

    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch { repository.setUnitSystem(unitSystem) }
    }

    companion object {
        fun factory(repository: SettingsRepository): ViewModelProvider.Factory =
            factoryOf { SettingsViewModel(repository) }
    }
}
