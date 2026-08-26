package app.matthieu.cairngps.data

/**
 * Everything [app.matthieu.cairngps.ui.navigation.MainScaffold] needs to build its nav graph.
 *
 * [app.matthieu.cairngps.CairnApplication] is the only implementation used by the real app; the
 * onboarding tour ([app.matthieu.cairngps.ui.onboarding.OnboardingContainer]) provides a second
 * one wired to synthetic data and an in-memory database, so [MainScaffold] can render the app's
 * real screens for either without knowing which one it was handed.
 */
interface AppContainer {
    val locationRepository: LocationRepository
    val compassRepository: CompassRepository
    val settingsRepository: SettingsRepository
    val waypointRepository: WaypointRepository
    val sessionRepository: SessionRepository
    val navigationTargetRepository: NavigationTargetRepository
    val recordingRepository: RecordingRepository
    val recordsRepository: RecordsRepository
    val achievementsRepository: AchievementsRepository
    val gamificationFlagsRepository: GamificationFlagsRepository
    val backupRepository: BackupRepository
    val gamificationManager: GamificationManager
}
