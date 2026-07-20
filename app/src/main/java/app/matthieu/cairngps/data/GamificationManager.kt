package app.matthieu.cairngps.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.matthieu.cairngps.domain.gamification.Achievements
import app.matthieu.cairngps.domain.gamification.AchievementDef
import app.matthieu.cairngps.domain.gamification.GamificationMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Drives the gamification layer (records + achievements) from live GPS data and finished
 * sessions, kept entirely separate from the raw-data screens (Position/Satellites/Boussole) and
 * their ViewModels — those never know this class exists.
 *
 * App-scoped (constructed once in `CairnApplication`), with two independent inputs:
 * - Session-derived records/achievements run for the app's whole lifetime: reading already-
 *   persisted [Session] rows has no GPS/battery cost, so there is no reason to gate it.
 * - Live records ([startLiveTracking]/[stopLiveTracking]) open their own GPS+GNSS subscription,
 *   gated by `ProcessLifecycleOwner` in `CairnApplication` (foreground only) rather than by any
 *   single screen's lifecycle, since achievements should progress no matter which tab is open.
 */
class GamificationManager(
    context: Context,
    private val locationRepository: LocationRepository,
    sessionRepository: SessionRepository,
    waypointRepository: WaypointRepository,
    private val recordsRepository: RecordsRepository,
    private val achievementsRepository: AchievementsRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _unlockedEvents = MutableSharedFlow<AchievementDef>(extraBufferCapacity = 8)

    /** Emits each achievement the moment it is newly unlocked; used to trigger the unlock banner. */
    val unlockedEvents: SharedFlow<AchievementDef> = _unlockedEvents.asSharedFlow()

    private var liveJob: Job? = null

    init {
        scope.launch { sessionRepository.sessions().collect { sessions -> submitSessionRecords(sessions) } }
        scope.launch {
            combine(
                recordsRepository.records(),
                sessionRepository.sessions(),
                waypointRepository.waypoints(),
            ) { records, sessions, waypoints ->
                Achievements.metricsFrom(records, sessions, waypoints)
            }.collect { metrics -> evaluateAndUnlock(metrics) }
        }
    }

    /**
     * Folds every finished session's aggregates into [RecordsRepository]. Re-submitting the whole
     * history on every emission (rather than just the newest session) is deliberate: [Session]
     * rows can be renamed/deleted independently of this collector, and [RecordsRepository.submit]
     * only ever keeps the best value anyway, so re-checking already-known bests is wasted work but
     * never wrong — simpler than tracking which sessions were already submitted.
     *
     * All candidates across all sessions go through a single [RecordsRepository.submitAll] call,
     * rather than 9 individual [RecordsRepository.submit] calls per session — avoids
     * an O(N×9) DB read/write pattern under the mutex on every `sessions()` emission.
     */
    private suspend fun submitSessionRecords(sessions: List<Session>) {
        val candidates = sessions.flatMap { session ->
            listOf(
                RecordCandidate(
                    RecordType.MAX_SPEED, session.maxSpeed.toDouble(),
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                RecordCandidate(
                    RecordType.MAX_ALTITUDE, session.maxAltitude,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                RecordCandidate(
                    RecordType.MIN_ALTITUDE, session.minAltitude,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                RecordCandidate(
                    RecordType.MAX_ELEVATION_GAIN, session.elevationGain,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                RecordCandidate(
                    RecordType.MAX_DISTANCE, session.distanceMeters,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                // Session only stores independent bounding-box extremes (e.g. latitudeMax isn't
                // paired with the longitude reached at that same instant) unlike a single live
                // fix, which has both coordinates together — see submitLiveFix below. So only the
                // axis that is actually the record's value is set here; the other is left null.
                RecordCandidate(
                    RecordType.NORTHERNMOST, session.latitudeMax, latitude = session.latitudeMax,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                RecordCandidate(
                    RecordType.SOUTHERNMOST, session.latitudeMin, latitude = session.latitudeMin,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                RecordCandidate(
                    RecordType.EASTERNMOST, session.longitudeMax, longitude = session.longitudeMax,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
                RecordCandidate(
                    RecordType.WESTERNMOST, session.longitudeMin, longitude = session.longitudeMin,
                    achievedAt = session.endTimestamp, sessionId = session.id,
                ),
            )
        }
        recordsRepository.submitAll(candidates)
    }

    /**
     * Unlocks every achievement satisfied by [metrics] that isn't already unlocked.
     * [AchievementsRepository.markUnlocked] is itself idempotent (a Room `INSERT ... IGNORE`), so
     * an achievement already unlocked in an earlier app run never re-fires [unlockedEvents] here —
     * only a genuinely new unlock does.
     */
    private suspend fun evaluateAndUnlock(metrics: GamificationMetrics) {
        for (def in Achievements.ALL) {
            if (!Achievements.isUnlocked(def, metrics)) continue
            if (achievementsRepository.markUnlocked(def.id)) {
                _unlockedEvents.emit(def)
            }
        }
    }

    /**
     * Starts a dedicated GPS+GNSS subscription that feeds live extremes into [RecordsRepository]
     * while the app is in the foreground. Idempotent: a no-op if already tracking. Silently does
     * nothing if [Manifest.permission.ACCESS_FINE_LOCATION] isn't granted yet — the caller
     * (`CairnApplication`'s `ProcessLifecycleOwner` observer) doesn't gate on permission itself, so
     * live records simply start on the next foreground transition after the permission is granted.
     */
    fun startLiveTracking() {
        if (liveJob?.isActive == true) return
        if (!hasLocationPermission()) return

        liveJob = scope.launch {
            launch {
                locationRepository.locationUpdates().collect { fix -> submitLiveFix(fix) }
            }
            launch {
                locationRepository.satelliteUpdates().collect { satellites ->
                    val usedInFix = satellites.count { it.usedInFix }
                    recordsRepository.submit(RecordType.MAX_SATELLITES, usedInFix.toDouble())
                }
            }
        }
    }

    /** Stops the live GPS+GNSS subscription started by [startLiveTracking]. A no-op if not running. */
    fun stopLiveTracking() {
        liveJob?.cancel()
        liveJob = null
    }

    private suspend fun submitLiveFix(fix: LocationData) {
        recordsRepository.submit(RecordType.MAX_SPEED, fix.speed.toDouble())
        recordsRepository.submit(RecordType.MAX_ALTITUDE, fix.altitude)
        recordsRepository.submit(RecordType.MIN_ALTITUDE, fix.altitude)
        // A live fix has both coordinates at once, so each geographic record can be paired with
        // the exact point it was set at (unlike the session-derived bounding box above).
        recordsRepository.submit(RecordType.NORTHERNMOST, fix.latitude, latitude = fix.latitude, longitude = fix.longitude)
        recordsRepository.submit(RecordType.SOUTHERNMOST, fix.latitude, latitude = fix.latitude, longitude = fix.longitude)
        recordsRepository.submit(RecordType.EASTERNMOST, fix.longitude, latitude = fix.latitude, longitude = fix.longitude)
        recordsRepository.submit(RecordType.WESTERNMOST, fix.longitude, latitude = fix.latitude, longitude = fix.longitude)
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
