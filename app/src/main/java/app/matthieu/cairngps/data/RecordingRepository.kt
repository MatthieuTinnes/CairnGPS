package app.matthieu.cairngps.data

import android.Manifest
import android.location.Location
import androidx.annotation.RequiresPermission
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Live aggregates of an in-progress (or just-reset) recording, as shown by the Position screen.
 *
 * Duration is deliberately not included here: the UI derives it from [startTimestamp] with its
 * own 1-second ticker, so this state doesn't need to change purely because time passed without a
 * new GPS fix.
 */
data class RecordingState(
    val isRecording: Boolean = false,
    val startTimestamp: Long = 0L,
    val distanceMeters: Double = 0.0,
    val averageSpeed: Float = 0f, // m/s, while moving only
    val maxSpeed: Float = 0f, // m/s
    val elevationGain: Double = 0.0, // D+, meters
    val elevationLoss: Double = 0.0, // D-, meters
    val currentAltitude: Double? = null, // meters, from the last accepted fix; null until then
    val currentSpeed: Float? = null, // m/s, from the last accepted fix; null until then
)

/**
 * Accumulates GPS fixes from [LocationRepository] into a running [RecordingState] while a
 * recording is active, then persists the final aggregates as a [Session] on [stop].
 *
 * App-scoped (constructed once in `CairnApplication`), not screen-scoped: a recording must
 * survive navigating away from the Position screen. Its lifecycle is driven by
 * `service.RecordingService` (a `START_STICKY` foreground service with its own notification), so
 * the recording keeps running in the background rather than stopping when the app is backgrounded.
 *
 * Survives a process death, not just backgrounding: [start] persists the recording's [Session] row
 * immediately (flagged [Session.isActive]) rather than only at [stop], and periodically checkpoints
 * the live accumulator ([RecordingCheckpoint]) and sampled track points at the same cadence as
 * track sampling (see [onFix]). If Android kills the process mid-recording, `START_STICKY` restarts
 * `RecordingService`, which calls [resumeIfActive] to reload this state and keep accumulating from
 * where it left off — see that function's doc for exactly what is and isn't preserved.
 */
class RecordingRepository(
    private val locationRepository: LocationRepository,
    private val sessionRepository: SessionRepository,
    private val waypointRepository: WaypointRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var recordingJob: Job? = null

    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    val isRecording: Boolean get() = _state.value.isRecording

    // Guards every access to createdWaypointIds/pendingAttachments below, together with the
    // finalizing read of _state in stop(). This is what lets stop() and a concurrent waypoint
    // save (reserveWaypointAttachment/completeWaypointAttachment) linearize instead of racing:
    // see the comments on those functions for the two races this closes.
    private val mutex = Mutex()

    // Ids of waypoints saved while this recording was active; attached to the Session at stop().
    // All reads/writes happen under [mutex] except the hard reset in start(), which is safe
    // because by the time start() observes isRecording == false, stop() has already finished its
    // own mutex-guarded capture-and-clear of this same field (see stop()).
    private var createdWaypointIds: List<Long> = emptyList()

    // Number of waypoint saves that have reserved an attachment slot (via
    // reserveWaypointAttachment) but not yet completed it. stop() waits for this to reach zero,
    // atomically re-checked under [mutex], before finalizing the session's waypoint list.
    private val pendingAttachments = MutableStateFlow(0)

    // The fields below are mutated only from within the single coroutine collecting
    // locationUpdates() in start()/resumeIfActive(). stop() reads them only after cancelAndJoin()
    // on that coroutine's Job, which happens-before those reads.

    // The active recording's Session row, kept in sync with the DB at the same cadence as track
    // sampling (see onFix) so a process death loses at most one sampling interval of aggregates.
    private var activeSession: Session? = null
    private var lastAcceptedFix: LocationData? = null
    private var referenceAltitude: Double? = null
    private var movingDistanceMeters = 0.0
    private var movingTimeMs = 0L
    private var minAltitude = Double.POSITIVE_INFINITY
    private var maxAltitude = Double.NEGATIVE_INFINITY
    private var latitudeMax = -90.0
    private var latitudeMin = 90.0
    private var longitudeMax = -180.0
    private var longitudeMin = 180.0

    // Raw track buffer for the in-progress recording, sampled at most once every
    // TRACK_SAMPLE_INTERVAL_MS and capped/decimated to TRACK_MAX_POINTS on stop() — this backs the
    // altitude profile / route trace on the session detail screen without growing unbounded on a
    // multi-hour hike.
    private val trackBuffer = mutableListOf<TrackPoint>()
    private var lastSampledAtMs: Long = 0L

    /**
     * Starts accumulating positions. Idempotent: a no-op while already recording.
     *
     * @param namePrefix Localized prefix for the auto-generated session name (e.g. `"Trace"`),
     *                    resolved by the caller — the data layer doesn't hold string resources.
     *                    Consumed immediately (the [Session] row is created right away, see the
     *                    class doc), unlike [stop] which no longer needs it.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun start(namePrefix: String) {
        if (isRecording) return

        resetAccumulator()

        val startTimestamp = System.currentTimeMillis()
        _state.value = RecordingState(isRecording = true, startTimestamp = startTimestamp)

        recordingJob = scope.launch {
            val initial = Session(
                name = defaultSessionName(namePrefix, startTimestamp),
                startTimestamp = startTimestamp,
                endTimestamp = startTimestamp,
                distanceMeters = 0.0,
                averageSpeed = 0f,
                maxSpeed = 0f,
                elevationGain = 0.0,
                elevationLoss = 0.0,
                minAltitude = 0.0,
                maxAltitude = 0.0,
                latitudeMax = -90.0,
                latitudeMin = 90.0,
                longitudeMax = -180.0,
                longitudeMin = 180.0,
            )
            // NonCancellable: if stop() races start() closely enough to cancel this job before the
            // insert completes, the row must still be written so stop() can find and discard it —
            // otherwise it would be orphaned in the DB forever as a phantom isActive=true session.
            val id = withContext(NonCancellable) { sessionRepository.insertActive(initial) }
            // insertActive() persists isActive=true; keep the in-memory copy in sync, since every
            // persistCheckpoint() below re-derives the row it writes from this activeSession and
            // would otherwise flip isActive back to false on the very first checkpoint.
            activeSession = initial.copy(id = id, isActive = true)
            locationRepository.locationUpdates().collect { fix -> onFix(fix) }
        }
    }

    /** Resets every accumulator field to its "nothing recorded yet" sentinel value. */
    private fun resetAccumulator() {
        activeSession = null
        lastAcceptedFix = null
        referenceAltitude = null
        movingDistanceMeters = 0.0
        movingTimeMs = 0L
        minAltitude = Double.POSITIVE_INFINITY
        maxAltitude = Double.NEGATIVE_INFINITY
        latitudeMax = -90.0
        latitudeMin = 90.0
        longitudeMax = -180.0
        longitudeMin = 180.0
        createdWaypointIds = emptyList()
        trackBuffer.clear()
        lastSampledAtMs = 0L
    }

    /**
     * Reloads an in-progress recording after `RecordingService` was restarted by `START_STICKY`
     * following a process death, and relaunches the fix collector exactly as [start] does.
     *
     * What survives depends on how far the recording got before the process died: [activeSession]
     * (name, start time, and — once at least one checkpoint was written — the live aggregates) is
     * always restored from the [Session] row itself, since [onFix] keeps it durably up to date. The
     * finer accumulator ([RecordingCheckpoint] — moving distance/time, altitude reference, last
     * fix) is only restored if at least one checkpoint was written; a recording killed before its
     * first sampling interval elapsed (`< TRACK_SAMPLE_INTERVAL_MS` after [start]) resumes as if
     * restarting fresh, since nothing was durable yet.
     *
     * Idempotent: a no-op returning `true` if a recording is already running in this process.
     * Returns `false` if no recording was active when the process died — nothing to resume.
     */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    suspend fun resumeIfActive(): Boolean {
        if (isRecording) return true
        val session = sessionRepository.getActive() ?: return false
        val checkpoint = sessionRepository.getCheckpoint()

        resetAccumulator()
        activeSession = session
        trackBuffer += sessionRepository.trackPointsFor(session.id)

        if (checkpoint != null) {
            lastAcceptedFix = LocationData(
                latitude = checkpoint.lastLatitude,
                longitude = checkpoint.lastLongitude,
                altitude = checkpoint.lastAltitude,
                speed = checkpoint.lastSpeed,
                horizontalAccuracy = checkpoint.lastHorizontalAccuracy,
                verticalAccuracy = null,
                timestamp = checkpoint.lastTimestamp,
            )
            referenceAltitude = checkpoint.referenceAltitude
            movingDistanceMeters = checkpoint.movingDistanceMeters
            movingTimeMs = checkpoint.movingTimeMs
            lastSampledAtMs = checkpoint.lastSampledAtMs
            minAltitude = session.minAltitude
            maxAltitude = session.maxAltitude
            latitudeMax = session.latitudeMax
            latitudeMin = session.latitudeMin
            longitudeMax = session.longitudeMax
            longitudeMin = session.longitudeMin
        }

        _state.value = RecordingState(
            isRecording = true,
            startTimestamp = session.startTimestamp,
            distanceMeters = session.distanceMeters,
            averageSpeed = session.averageSpeed,
            maxSpeed = session.maxSpeed,
            elevationGain = session.elevationGain,
            elevationLoss = session.elevationLoss,
            currentAltitude = lastAcceptedFix?.altitude,
            currentSpeed = lastAcceptedFix?.speed,
        )

        recordingJob = scope.launch {
            locationRepository.locationUpdates().collect { fix -> onFix(fix) }
        }
        return true
    }

    private suspend fun onFix(fix: LocationData) {
        // A noisy horizontal fix makes the point itself unreliable for distance/elevation, so it
        // is dropped entirely rather than folded into the track.
        if (fix.horizontalAccuracy > MAX_ACCURACY_METERS) return

        val previous = lastAcceptedFix
        val current = _state.value

        var distanceMeters = current.distanceMeters
        if (previous != null) {
            val results = FloatArray(1)
            Location.distanceBetween(
                previous.latitude, previous.longitude,
                fix.latitude, fix.longitude,
                results,
            )
            val segmentMeters = results[0].toDouble()
            distanceMeters += segmentMeters

            if (fix.speed > MOVING_SPEED_THRESHOLD_MS) {
                movingDistanceMeters += segmentMeters
                movingTimeMs += (fix.timestamp - previous.timestamp).coerceAtLeast(0)
            }
        }

        var elevationGain = current.elevationGain
        var elevationLoss = current.elevationLoss
        val reference = referenceAltitude
        if (reference == null) {
            // Nothing to compare the first accepted fix against yet.
            referenceAltitude = fix.altitude
        } else {
            val delta = fix.altitude - reference
            // GPS altitude is noisy at rest; only count a change once it clears the threshold,
            // using the last *counted* altitude as the new reference so noise cannot accumulate.
            if (abs(delta) >= ALTITUDE_THRESHOLD_METERS) {
                if (delta > 0) elevationGain += delta else elevationLoss += -delta
                referenceAltitude = fix.altitude
            }
        }

        minAltitude = minOf(minAltitude, fix.altitude)
        maxAltitude = maxOf(maxAltitude, fix.altitude)
        latitudeMax = maxOf(latitudeMax, fix.latitude)
        latitudeMin = minOf(latitudeMin, fix.latitude)
        longitudeMax = maxOf(longitudeMax, fix.longitude)
        longitudeMin = minOf(longitudeMin, fix.longitude)

        val averageSpeed = if (movingTimeMs > 0) {
            (movingDistanceMeters / (movingTimeMs / 1000.0)).toFloat()
        } else {
            0f
        }

        lastAcceptedFix = fix
        val newState = current.copy(
            distanceMeters = distanceMeters,
            averageSpeed = averageSpeed,
            maxSpeed = maxOf(current.maxSpeed, fix.speed),
            elevationGain = elevationGain,
            elevationLoss = elevationLoss,
            currentAltitude = fix.altitude,
            currentSpeed = fix.speed,
        )
        _state.value = newState

        // Sample into the track buffer, and durably persist the sample plus the running
        // aggregates, at most once every TRACK_SAMPLE_INTERVAL_MS. This same gate is what keeps
        // the extra writes added to survive a process death mid-recording battery-friendly, rather
        // than firing on every GPS fix.
        if (fix.timestamp - lastSampledAtMs >= TRACK_SAMPLE_INTERVAL_MS) {
            lastSampledAtMs = fix.timestamp
            persistCheckpoint(fix, newState)
        }
    }

    /** Durably persists the running aggregates so [resumeIfActive] can rebuild them after a process death. */
    private suspend fun persistCheckpoint(fix: LocationData, state: RecordingState) {
        val session = activeSession ?: return
        val point = TrackPoint(
            sessionId = session.id,
            timestamp = fix.timestamp,
            latitude = fix.latitude,
            longitude = fix.longitude,
            altitude = fix.altitude,
        )
        trackBuffer += point

        val updatedSession = session.copy(
            endTimestamp = fix.timestamp,
            distanceMeters = state.distanceMeters,
            averageSpeed = state.averageSpeed,
            maxSpeed = state.maxSpeed,
            elevationGain = state.elevationGain,
            elevationLoss = state.elevationLoss,
            minAltitude = minAltitude,
            maxAltitude = maxAltitude,
            latitudeMax = latitudeMax,
            latitudeMin = latitudeMin,
            longitudeMax = longitudeMax,
            longitudeMin = longitudeMin,
        )
        activeSession = updatedSession

        sessionRepository.appendTrackPoint(point)
        sessionRepository.updateActive(updatedSession)
        sessionRepository.saveCheckpoint(
            RecordingCheckpoint(
                sessionId = session.id,
                movingDistanceMeters = movingDistanceMeters,
                movingTimeMs = movingTimeMs,
                referenceAltitude = referenceAltitude,
                lastLatitude = fix.latitude,
                lastLongitude = fix.longitude,
                lastAltitude = fix.altitude,
                lastSpeed = fix.speed,
                lastHorizontalAccuracy = fix.horizontalAccuracy,
                lastTimestamp = fix.timestamp,
                lastSampledAtMs = lastSampledAtMs,
            ),
        )
    }

    /**
     * Reserves an attachment slot for a waypoint about to be saved, if a recording is currently
     * active. Must be called before the waypoint's own (suspending) database insert starts, and
     * the result passed to [completeWaypointAttachment] once that insert finishes.
     *
     * This two-step reserve/complete handshake (rather than a single post-insert call) is what
     * lets [stop] wait for a save that began while still recording instead of racing it: without
     * it, a save whose DB insert is still in flight when the user taps "Arrêter" would find
     * [isRecording] already false by the time it tries to record its id, and be silently dropped.
     */
    suspend fun reserveWaypointAttachment(): Boolean = mutex.withLock {
        val reserved = _state.value.isRecording
        if (reserved) pendingAttachments.update { it + 1 }
        reserved
    }

    /**
     * Completes a reservation from [reserveWaypointAttachment] by attaching [id]. Always attaches
     * when [reserved] is true — even if the recording has since been stopped — because [stop]
     * waits for [pendingAttachments] to drain before finalizing the session's waypoint list, so a
     * reserved save is guaranteed to still be counted. A no-op when [reserved] is false.
     */
    suspend fun completeWaypointAttachment(reserved: Boolean, id: Long) {
        if (!reserved) return
        mutex.withLock {
            createdWaypointIds = createdWaypointIds + id
            pendingAttachments.update { it - 1 }
        }
    }

    /**
     * Stops accumulating, persists the final [Session] and attaches any waypoints saved during
     * the recording to it. A no-op if no recording was in progress.
     */
    suspend fun stop() {
        val job = recordingJob ?: return
        recordingJob = null
        job.cancelAndJoin()

        // Capturing-and-clearing createdWaypointIds happens atomically with the isRecording reset
        // below, both under [mutex] together with the pendingAttachments == 0 check. This closes
        // two races: (1) a rapid Stop-then-Start can no longer wipe this recording's ids out from
        // under it, since by the time start() observes isRecording == false the capture has
        // already happened; (2) a concurrent reserveWaypointAttachment() can never sneak a new
        // reservation in between "pending == 0" being observed and the state being finalized,
        // since both are decided inside the same critical section.
        var finalState: RecordingState
        var waypointIds: List<Long>
        while (true) {
            val captured = mutex.withLock {
                if (pendingAttachments.value != 0) return@withLock null
                val state = _state.value
                _state.value = RecordingState()
                val ids = createdWaypointIds
                createdWaypointIds = emptyList()
                state to ids
            }
            if (captured == null) {
                pendingAttachments.first { it == 0 }
                continue
            }
            finalState = captured.first
            waypointIds = captured.second
            break
        }
        if (!finalState.isRecording) return

        val session = activeSession
        activeSession = null

        // Aucun fix GPS n'a été accepté (ou le process a été tué avant même que la ligne active ne
        // soit insérée) : persister/garder la session écrirait des agrégats tout-à-zéro
        // (sentinelles ±Infinity retombées à 0.0) qui établiraient de faux records (latitude/
        // longitude extrêmes à 0°, altitude min à 0 m) et débloqueraient de faux succès. On
        // l'abandonne, avec son éventuel checkpoint.
        if (lastAcceptedFix == null || session == null) {
            session?.let { sessionRepository.discardActive(it.id) }
            sessionRepository.clearCheckpoint()
            return
        }

        val finalSession = session.copy(
            endTimestamp = System.currentTimeMillis(),
            distanceMeters = finalState.distanceMeters,
            averageSpeed = finalState.averageSpeed,
            maxSpeed = finalState.maxSpeed,
            elevationGain = finalState.elevationGain,
            elevationLoss = finalState.elevationLoss,
            minAltitude = minAltitude.takeIf { it.isFinite() } ?: 0.0,
            maxAltitude = maxAltitude.takeIf { it.isFinite() } ?: 0.0,
            latitudeMax = latitudeMax.takeIf { it.isFinite() } ?: 0.0,
            latitudeMin = latitudeMin.takeIf { it.isFinite() } ?: 0.0,
            longitudeMax = longitudeMax.takeIf { it.isFinite() } ?: 0.0,
            longitudeMin = longitudeMin.takeIf { it.isFinite() } ?: 0.0,
        )
        sessionRepository.finalizeActive(finalSession, decimatedTrack())
        sessionRepository.clearCheckpoint()
        waypointRepository.attachToSession(waypointIds, finalSession.id)
    }

    /**
     * Returns the buffered track, uniformly thinned down to at most [TRACK_MAX_POINTS] so a long
     * multi-hour recording doesn't write an unbounded number of rows. Uniform decimation (rather
     * than dropping the tail) keeps the shape of the altitude/route profile intact end-to-end.
     */
    private fun decimatedTrack(): List<TrackPoint> {
        if (trackBuffer.size <= TRACK_MAX_POINTS) return trackBuffer.toList()
        val stride = trackBuffer.size.toDouble() / TRACK_MAX_POINTS
        return (0 until TRACK_MAX_POINTS).map { i -> trackBuffer[(i * stride).toInt()] }
    }

    private companion object {
        /** Fixes less accurate than this are ignored for distance/elevation (spec: > 20 m). */
        const val MAX_ACCURACY_METERS = 20f

        /** Minimum altitude change counted into D+/D- (spec: 3-5 m); picked mid-range. */
        const val ALTITUDE_THRESHOLD_METERS = 4.0

        /** Below this speed the user is considered stationary; excluded from the moving average. */
        const val MOVING_SPEED_THRESHOLD_MS = 0.5f

        /** Minimum spacing between track-buffer samples, so a fast fix rate doesn't bloat memory. */
        const val TRACK_SAMPLE_INTERVAL_MS = 5_000L

        /** Hard cap on stored track points per session; longer tracks are uniformly decimated. */
        const val TRACK_MAX_POINTS = 1_000
    }
}

/** Default trace name based on [namePrefix] and the recording's start date/time. */
private fun defaultSessionName(namePrefix: String, startTimestamp: Long): String {
    val stamp = defaultNameTimestampFormatter().format(Instant.ofEpochMilli(startTimestamp).atZone(ZoneId.systemDefault()))
    return "$namePrefix $stamp"
}
