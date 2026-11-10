package app.matthieu.cairngps.storescreenshots

import android.content.Context
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.AppContainer
import app.matthieu.cairngps.data.AppDatabase
import app.matthieu.cairngps.data.BackupRepository
import app.matthieu.cairngps.data.CompassRepository
import app.matthieu.cairngps.data.GamificationFlagsRepository
import app.matthieu.cairngps.data.GamificationManager
import app.matthieu.cairngps.data.GnssInterferenceRepository
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.NavigationTargetRepository
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.demo.DemoDataSeeder
import app.matthieu.cairngps.demo.DemoGpsSource

/**
 * Same wiring as the onboarding tour's container, with the simulated walk pinned to [clockMs] so
 * every capture shows the same position, sky and heading. While [spoofed] is set, the simulated
 * receiver reports a GNSS clock an hour off the network time, which the detector flags as spoofing.
 */
class StoreContainer(
    context: Context,
    clockMs: Long,
) : AppContainer, AutoCloseable {

    private val appContext = context.applicationContext

    @Volatile
    var spoofed: Boolean = false

    private val gps = DemoGpsSource(
        clock = { clockMs },
        timeOffsetMs = { if (spoofed) 3_600_000L else 40L },
    )

    override val locationRepository: LocationRepository = LocationRepository(appContext, gps)

    override val compassRepository: CompassRepository = CompassRepository(appContext, gps)

    override val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    override val gnssInterferenceRepository: GnssInterferenceRepository =
        GnssInterferenceRepository(locationRepository, settingsRepository)

    val database: AppDatabase = AppDatabase.inMemory(appContext)

    override val waypointRepository: WaypointRepository = WaypointRepository(database.waypointDao())

    override val sessionRepository: SessionRepository =
        SessionRepository(database.sessionDao(), database.trackPointDao(), database.recordingCheckpointDao())

    override val navigationTargetRepository: NavigationTargetRepository = NavigationTargetRepository()

    override val recordingRepository: RecordingRepository =
        RecordingRepository(locationRepository, sessionRepository, waypointRepository)

    override val recordsRepository: RecordsRepository = RecordsRepository(database.recordDao())

    override val achievementsRepository: AchievementsRepository =
        AchievementsRepository(database.achievementDao())

    override val gamificationFlagsRepository: GamificationFlagsRepository =
        GamificationFlagsRepository(database.gamificationFlagDao())

    override val backupRepository: BackupRepository = BackupRepository(
        database = database,
        waypointDao = database.waypointDao(),
        sessionDao = database.sessionDao(),
        trackPointDao = database.trackPointDao(),
        recordDao = database.recordDao(),
        achievementDao = database.achievementDao(),
        gamificationFlagDao = database.gamificationFlagDao(),
        settingsRepository = settingsRepository,
    )

    override val gamificationManager: GamificationManager = GamificationManager(
        appContext,
        locationRepository,
        sessionRepository,
        waypointRepository,
        recordsRepository,
        achievementsRepository,
        gamificationFlagsRepository,
    )

    suspend fun seed() {
        DemoDataSeeder(
            sessionDao = database.sessionDao(),
            trackPointDao = database.trackPointDao(),
            waypointDao = database.waypointDao(),
            gamificationFlagDao = database.gamificationFlagDao(),
        ).seedIfEmpty()
    }

    override fun close() {
        gamificationManager.close()
        database.close()
    }
}
