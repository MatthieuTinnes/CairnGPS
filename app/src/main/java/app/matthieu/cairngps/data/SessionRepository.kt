package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for saved [Session]s, wrapping the Room [SessionDao] (and, for the
 * per-point track backing the altitude profile, the [TrackPointDao]) and the active recording's
 * [RecordingCheckpointDao] — the two extra tables [RecordingRepository] uses to resume an
 * in-progress recording after a process death (see its class doc).
 *
 * ViewModels talk to this repository and never touch the DAOs or [AppDatabase] directly, keeping
 * persistence details out of the UI layer.
 */
class SessionRepository(
    private val dao: SessionDao,
    private val trackPointDao: TrackPointDao,
    private val checkpointDao: RecordingCheckpointDao,
) {

    /** Cold flow of every finished session, most recently started first; excludes an in-progress recording, if any. */
    fun sessions(): Flow<List<Session>> = dao.observeAll()

    /**
     * Cold flow of every finished session joined with its track in a single Room query, for the
     * Traces tab's sparkline previews — see [SessionWithTrackPoints].
     */
    fun sessionsWithTracks(): Flow<List<SessionWithTrackPoints>> = dao.observeAllWithTracks()

    /** Returns the session with [id], or `null` if it no longer exists. */
    suspend fun get(id: Long): Session? = dao.getById(id)

    /** Number of finished sessions stored; excludes an in-progress recording, if any. */
    suspend fun finishedCount(): Int = dao.countFinished()

    /** Removes the session with [id]. Waypoints attached to it keep existing (sessionId → null). */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Renames the session with [id]. */
    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    /**
     * Cold flow of the track recorded for session [id], chronologically ordered. Empty for
     * sessions recorded before this feature existed, or too short to have sampled any point.
     */
    fun trackForSession(id: Long): Flow<List<TrackPoint>> = trackPointDao.observeBySession(id)

    // --- Active-recording persistence: used only by RecordingRepository to make an in-progress
    // recording resumable after a process death. None of this is visible through [sessions] above
    // (see [Session.isActive]).

    /** Inserts [session] as the active in-progress recording and returns its generated id. */
    suspend fun insertActive(session: Session): Long = dao.insert(session.copy(isActive = true))

    /** The in-progress recording's session row, if the process was killed and restarted mid-recording. */
    suspend fun getActive(): Session? = dao.getActive()

    /** Overwrites the active session's live aggregates — called periodically while recording. */
    suspend fun updateActive(session: Session) = dao.update(session)

    /** Appends one sampled point to the active recording's track, durably rather than only in memory. */
    suspend fun appendTrackPoint(point: TrackPoint) = trackPointDao.insert(point)

    /** Every point sampled so far for session [id] — used to rebuild the in-memory track buffer on resume. */
    suspend fun trackPointsFor(id: Long): List<TrackPoint> = trackPointDao.getBySession(id)

    /**
     * Finalizes the active recording: marks [session] inactive with its final aggregates, and
     * replaces its incrementally-appended track with the final decimated [points].
     */
    suspend fun finalizeActive(session: Session, points: List<TrackPoint>) {
        dao.update(session.copy(isActive = false))
        trackPointDao.deleteBySession(session.id)
        if (points.isNotEmpty()) {
            trackPointDao.insertAll(points.map { it.copy(sessionId = session.id) })
        }
    }

    /** Discards an active recording that ended without a single accepted GPS fix. */
    suspend fun discardActive(sessionId: Long) = dao.deleteById(sessionId)

    /** The active recording's live accumulator (distance/time/altitude reference), if any. */
    suspend fun getCheckpoint(): RecordingCheckpoint? = checkpointDao.get()

    /** Persists [checkpoint], replacing any previous one. */
    suspend fun saveCheckpoint(checkpoint: RecordingCheckpoint) = checkpointDao.upsert(checkpoint)

    /** Clears the checkpoint once a recording is finalized or discarded. */
    suspend fun clearCheckpoint() = checkpointDao.deleteAll()
}
