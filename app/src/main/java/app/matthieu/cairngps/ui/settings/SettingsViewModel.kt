package app.matthieu.cairngps.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.GamificationFlagsRepository
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
 * [gamificationFlagsRepository] is `null` everywhere except the Settings screen for the same
 * reason: only that screen ever calls [setThemeMode]/[setCoordinateFormat], so it's the only one
 * that needs to flag `APP_THEMES`/`APP_FORMATS` (see `succes.md` §4.10) when they're used.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val gamificationFlagsRepository: GamificationFlagsRepository? = null,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun setCoordinateFormat(format: CoordinateFormat) {
        viewModelScope.launch {
            repository.setCoordinateFormat(format)
            val flag = when (format) {
                CoordinateFormat.DECIMAL -> "format_decimal"
                CoordinateFormat.DMS -> "format_dms"
            }
            gamificationFlagsRepository?.set(flag)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
            // SYSTEM doesn't tell us which theme is actually rendered, so it sets neither flag —
            // only an explicit LIGHT/DARK choice counts towards APP_THEMES.
            val flag = when (mode) {
                ThemeMode.LIGHT -> "theme_light"
                ThemeMode.DARK -> "theme_dark"
                ThemeMode.SYSTEM -> null
            }
            flag?.let { gamificationFlagsRepository?.set(it) }
        }
    }

    fun setNorthReference(reference: NorthReference) {
        viewModelScope.launch { repository.setNorthReference(reference) }
    }

    fun setUnitSystem(unitSystem: UnitSystem) {
        viewModelScope.launch { repository.setUnitSystem(unitSystem) }
    }

    companion object {
        fun factory(
            repository: SettingsRepository,
            gamificationFlagsRepository: GamificationFlagsRepository? = null,
        ): ViewModelProvider.Factory =
            factoryOf { SettingsViewModel(repository, gamificationFlagsRepository) }
    }
}
