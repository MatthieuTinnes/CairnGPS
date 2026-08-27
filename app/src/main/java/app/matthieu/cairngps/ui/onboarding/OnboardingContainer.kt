package app.matthieu.cairngps.ui.onboarding

import android.content.Context
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.AppContainer
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

/**
 * The onboarding tour's own [AppContainer], so [app.matthieu.cairngps.ui.navigation.MainScaffold]
 * can render the app's real screens fed entirely by synthetic data:
 *
 * - [locationRepository]/[compassRepository] are backed by [app.matthieu.cairngps.demo.DemoGpsSource]
 *   directly rather than by [app.matthieu.cairngps.demo.DemoMode] — the tour must work in release
 *   builds too, where `DemoMode.isAvailable` is `false`, and must never touch the real GPS chip or
 *   require [android.Manifest.permission.ACCESS_FINE_LOCATION].
 * - Every Room-backed repository sits on its own in-memory [AppDatabase] ([seed] fills it with the
 *   same fictional history [DemoDataSeeder] builds for the debug-only demo mode), so the tour never
 *   reads or writes the user's real `cairn.db`.
 * - [settingsRepository] is the one exception: it's the app's real instance, so the tour honours
 *   whatever theme/coordinate-format the user already picked instead of overriding it.
 *
 * [close] must be called once the tour is dismissed, to cancel [gamificationManager]'s collectors
 * and release the in-memory database.
 */
class OnboardingContainer(context: Context) : AppContainer, AutoCloseable {

    private val appContext = context.applicationContext

    override val locationRepository: LocationRepository = LocationRepository.simulated(appContext)

    override val compassRepository: CompassRepository = CompassRepository.simulated(appContext)

    override val settingsRepository: SettingsRepository = SettingsRepository(appContext)

    private val database: AppDatabase = AppDatabase.inMemory(appContext)

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

    // Never starts live tracking: its init block alone derives records/achievements from the
    // seeded sessions/waypoints below, which is exactly what makes the Succès/Records/Niveaux
    // screens fill in on their own without ever contradicting the seeded Carnet.
    override val gamificationManager: GamificationManager = GamificationManager(
        appContext,
        locationRepository,
        sessionRepository,
        waypointRepository,
        recordsRepository,
        achievementsRepository,
        gamificationFlagsRepository,
    )

    /** Fills the in-memory database with the same fictional hiking history demo mode uses. */
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
