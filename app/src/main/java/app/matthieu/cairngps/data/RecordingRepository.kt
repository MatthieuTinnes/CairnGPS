package app.matthieu.cairngps.data

import android.Manifest
import android.location.Location
import androidx.annotation.RequiresPermission
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * survive navigating away from the Position screen. There is no foreground service yet (planned
 * later per CLAUDE.md), so the recording is still tied to the app process and stops if the
 * process is killed in the background.
 */
class RecordingRepository(
    private val locationRepository: LocationRepository,
    private val sessionRepository: SessionRepository,
    private val waypointRepository: WaypointRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
    // locationUpdates() in start(). stop() reads them only after cancelAndJoin() on that
    // coroutine's Job, which happens-before those reads.
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

    /** Starts accumulating positions. Idempotent: a no-op while already recording. */
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun start() {
        if (isRecording) return

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

        _state.value = RecordingState(isRecording = true, startTimestamp = System.currentTimeMillis())

        recordingJob = scope.launch {
            locationRepository.locationUpdates().collect { fix -> onFix(fix) }
        }
    }

    private fun onFix(fix: LocationData) {
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
        _state.value = current.copy(
            distanceMeters = distanceMeters,
            averageSpeed = averageSpeed,
            maxSpeed = maxOf(current.maxSpeed, fix.speed),
            elevationGain = elevationGain,
            elevationLoss = elevationLoss,
            currentAltitude = fix.altitude,
            currentSpeed = fix.speed,
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

        val session = Session(
            name = defaultSessionName(finalState.startTimestamp),
            startTimestamp = finalState.startTimestamp,
            endTimestamp = System.currentTimeMillis(),
            distanceMeters = finalState.distanceMeters,
            averageSpeed = finalState.averageSpeed,
            maxSpeed = finalState.maxSpeed,
            elevationGain = finalState.elevationGain,
            elevationLoss = finalState.elevationLoss,
            // No fix was ever accepted: fall back to 0 rather than leaking the +/-Infinity sentinels.
            minAltitude = minAltitude.takeIf { it.isFinite() } ?: 0.0,
            maxAltitude = maxAltitude.takeIf { it.isFinite() } ?: 0.0,
            latitudeMax = latitudeMax.takeIf { it.isFinite() } ?: 0.0,
            latitudeMin = latitudeMin.takeIf { it.isFinite() } ?: 0.0,
            longitudeMax = longitudeMax.takeIf { it.isFinite() } ?: 0.0,
            longitudeMin = longitudeMin.takeIf { it.isFinite() } ?: 0.0,
        )
        val sessionId = sessionRepository.save(session)
        waypointRepository.attachToSession(waypointIds, sessionId)
    }

    private companion object {
        /** Fixes less accurate than this are ignored for distance/elevation (spec: > 20 m). */
        const val MAX_ACCURACY_METERS = 20f

        /** Minimum altitude change counted into D+/D- (spec: 3-5 m); picked mid-range. */
        const val ALTITUDE_THRESHOLD_METERS = 4.0

        /** Below this speed the user is considered stationary; excluded from the moving average. */
        const val MOVING_SPEED_THRESHOLD_MS = 0.5f
    }
}

private val sessionNameFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())

/** Default trace name based on the recording's start date/time, e.g. `Trace 10/07/2026 14:30`. */
private fun defaultSessionName(startTimestamp: Long): String {
    val stamp = sessionNameFormatter.format(Instant.ofEpochMilli(startTimestamp).atZone(ZoneId.systemDefault()))
    return "Trace $stamp"
}
