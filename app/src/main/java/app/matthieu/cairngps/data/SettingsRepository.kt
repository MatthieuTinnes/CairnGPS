package app.matthieu.cairngps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
    }

    /** Cold flow that emits the current settings and every subsequent change. */
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            coordinateFormat = prefs[Keys.COORDINATE_FORMAT]
                ?.let { runCatching { CoordinateFormat.valueOf(it) }.getOrNull() }
                ?: CoordinateFormat.DECIMAL,
        )
    }

    suspend fun setCoordinateFormat(format: CoordinateFormat) {
        dataStore.edit { prefs -> prefs[Keys.COORDINATE_FORMAT] = format.name }
    }
}
