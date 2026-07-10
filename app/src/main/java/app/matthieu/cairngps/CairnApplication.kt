package app.matthieu.cairngps

import android.app.Application
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.SettingsRepository

/**
 * Application-scoped container. Holds the singleton repositories so they survive configuration
 * changes and can be shared across ViewModels — a lightweight manual alternative to a full DI
 * framework.
 */
class CairnApplication : Application() {

    val locationRepository: LocationRepository by lazy { LocationRepository(this) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
}
