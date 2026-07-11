package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for saved [Session]s, wrapping the Room [SessionDao].
 *
 * ViewModels talk to this repository and never touch the DAO or [AppDatabase] directly, keeping
 * persistence details out of the UI layer.
 */
class SessionRepository(private val dao: SessionDao) {

    /** Cold flow of all sessions, most recently started first; re-emits whenever the table changes. */
    fun sessions(): Flow<List<Session>> = dao.observeAll()

    /** Persists [session] and returns its generated id. */
    suspend fun save(session: Session): Long = dao.insert(session)

    /** Returns the session with [id], or `null` if it no longer exists. */
    suspend fun get(id: Long): Session? = dao.getById(id)

    /** Removes the session with [id]. Waypoints attached to it keep existing (sessionId → null). */
    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Renames the session with [id]. */
    suspend fun rename(id: Long, name: String) = dao.rename(id, name)
}
