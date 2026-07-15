package app.matthieu.cairngps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Single DataStore instance for the process, scoped to the application context via this delegate.
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists and exposes user [AppSettings] using Preferences DataStore.
 * The single source of truth for preferences across every screen.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val COORDINATE_FORMAT = stringPreferencesKey("coordinate_format")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NORTH_REFERENCE = stringPreferencesKey("north_reference")
    }

    // Single source for default values, reused below instead of repeating each enum default in
    // both AppSettings's constructor and the DataStore fallback here.
    private val defaults = AppSettings()

    /** Cold flow that emits the current settings and every subsequent change. */
    val settings: Flow<AppSettings> = dataStore.data
        // A corrupted/unreadable preferences file surfaces as an IOException from the DataStore
        // itself; fall back to defaults instead of crashing every collector (ViewModels stateIn
        // this flow directly).
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            AppSettings(
                coordinateFormat = prefs[Keys.COORDINATE_FORMAT]
                    ?.let { runCatching { CoordinateFormat.valueOf(it) }.getOrNull() }
                    ?: defaults.coordinateFormat,
                themeMode = prefs[Keys.THEME_MODE]
                    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: defaults.themeMode,
                northReference = prefs[Keys.NORTH_REFERENCE]
                    ?.let { runCatching { NorthReference.valueOf(it) }.getOrNull() }
                    ?: defaults.northReference,
            )
        }

    suspend fun setCoordinateFormat(format: CoordinateFormat) {
        dataStore.edit { prefs -> prefs[Keys.COORDINATE_FORMAT] = format.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setNorthReference(reference: NorthReference) {
        dataStore.edit { prefs -> prefs[Keys.NORTH_REFERENCE] = reference.name }
    }
}
