package app.matthieu.cairngps

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.AppDatabase
import app.matthieu.cairngps.data.BackupRepository
import app.matthieu.cairngps.data.CompassRepository
import app.matthieu.cairngps.data.GamificationFlagsRepository
import app.matthieu.cairngps.data.GamificationManager
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.NavigationTargetRepository
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.demo.DemoDataSeeder
import app.matthieu.cairngps.demo.DemoMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application-scoped container. Holds the singleton repositories so they survive configuration
 * changes and can be shared across ViewModels — a lightweight manual alternative to a full DI
 * framework.
 */
class CairnApplication : Application() {

    val locationRepository: LocationRepository by lazy { LocationRepository(this) }

    val compassRepository: CompassRepository by lazy { CompassRepository(this) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val waypointRepository: WaypointRepository by lazy { WaypointRepository(database.waypointDao()) }

    val sessionRepository: SessionRepository by lazy {
        SessionRepository(database.sessionDao(), database.trackPointDao(), database.recordingCheckpointDao())
    }

    val navigationTargetRepository: NavigationTargetRepository by lazy { NavigationTargetRepository() }

    val recordingRepository: RecordingRepository by lazy {
        RecordingRepository(locationRepository, sessionRepository, waypointRepository)
    }

    val recordsRepository: RecordsRepository by lazy { RecordsRepository(database.recordDao()) }

    val achievementsRepository: AchievementsRepository by lazy {
        AchievementsRepository(database.achievementDao())
    }

    val gamificationFlagsRepository: GamificationFlagsRepository by lazy {
        GamificationFlagsRepository(database.gamificationFlagDao())
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            database = database,
            waypointDao = database.waypointDao(),
            sessionDao = database.sessionDao(),
            trackPointDao = database.trackPointDao(),
            recordDao = database.recordDao(),
            achievementDao = database.achievementDao(),
            gamificationFlagDao = database.gamificationFlagDao(),
            settingsRepository = settingsRepository,
        )
    }

    val gamificationManager: GamificationManager by lazy {
        GamificationManager(
            this,
            locationRepository,
            sessionRepository,
            waypointRepository,
            recordsRepository,
            achievementsRepository,
            gamificationFlagsRepository,
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Must run before anything touches a repository: it decides which database file to open
        // and whether the location/compass sources are real or simulated. No-op in release.
        DemoMode.init(this)
        if (DemoMode.isEnabled) seedDemoData()

        // Gates GamificationManager's live GPS+GNSS subscription to whenever the app itself is in
        // the foreground (any screen), independently of which screen's own onStart/onStop fires —
        // achievements/records should progress no matter which tab is open.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    gamificationManager.startLiveTracking()
                }

                override fun onStop(owner: LifecycleOwner) {
                    gamificationManager.stopLiveTracking()
                }
            },
        )
    }

    /**
     * Fills the demo database on its first run. Fire-and-forget rather than blocking `onCreate`:
     * every screen reads through a Room [kotlinx.coroutines.flow.Flow], so they simply re-emit
     * once the rows land instead of needing the data to be there before the first frame.
     */
    private fun seedDemoData() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            DemoDataSeeder(
                sessionDao = database.sessionDao(),
                trackPointDao = database.trackPointDao(),
                waypointDao = database.waypointDao(),
                gamificationFlagDao = database.gamificationFlagDao(),
            ).seedIfEmpty()
        }
    }
}
