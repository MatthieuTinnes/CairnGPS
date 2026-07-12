package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for saved [Session]s, wrapping the Room [SessionDao] (and, for the
 * per-point track backing the altitude profile, the [TrackPointDao]).
 *
 * ViewModels talk to this repository and never touch the DAOs or [AppDatabase] directly, keeping
 * persistence details out of the UI layer.
 */
class SessionRepository(
    private val dao: SessionDao,
    private val trackPointDao: TrackPointDao,
) {

    /** Cold flow of all sessions, most recently started first; re-emits whenever the table changes. */
    fun sessions(): Flow<List<Session>> = dao.observeAll()

    /** Persists [session] and returns its generated id. */
    suspend fun save(session: Session): Long = dao.insert(session)

    /**
     * Persists [session] and its downsampled [points] together, stamping each point with the
     * newly generated session id. `points` may be empty (e.g. a recording with too few accepted
     * fixes), in which case no track rows are written and the session simply has no profile.
     */
    suspend fun saveWithTrack(session: Session, points: List<TrackPoint>): Long {
        val sessionId = dao.insert(session)
        if (points.isNotEmpty()) {
            trackPointDao.insertAll(points.map { it.copy(sessionId = sessionId) })
        }
        return sessionId
    }

    /** Returns the session with [id], or `null` if it no longer exists. */
    suspend fun get(id: Long): Session? = dao.getById(id)

    /** Removes the session with [id]. Waypoints attached to it keep existing (sessionId → null). */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Renames the session with [id]. */
    suspend fun rename(id: Long, name: String) = dao.rename(id, name)

    /**
     * Cold flow of the track recorded for session [id], chronologically ordered. Empty for
     * sessions recorded before this feature existed, or too short to have sampled any point.
     */
    fun trackForSession(id: Long): Flow<List<TrackPoint>> = trackPointDao.observeBySession(id)
}
