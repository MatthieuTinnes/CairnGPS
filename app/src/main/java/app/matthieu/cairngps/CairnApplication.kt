package app.matthieu.cairngps

import android.app.Application
import app.matthieu.cairngps.data.LocationRepository

/**
 * Application-scoped container. Holds the single [LocationRepository] instance so it survives
 * configuration changes and can be shared across ViewModels — a lightweight manual alternative
 * to a full DI framework.
 */
class CairnApplication : Application() {

    val locationRepository: LocationRepository by lazy { LocationRepository(this) }
}
