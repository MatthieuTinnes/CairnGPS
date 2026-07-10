package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for saved [Waypoint]s, wrapping the Room [WaypointDao].
 *
 * ViewModels talk to this repository and never touch the DAO or [AppDatabase] directly, keeping
 * persistence details out of the UI layer.
 */
class WaypointRepository(private val dao: WaypointDao) {

    /** Cold flow of all waypoints, most recent first; re-emits whenever the table changes. */
    fun waypoints(): Flow<List<Waypoint>> = dao.observeAll()

    /** Persists [waypoint] and returns its generated id. */
    suspend fun save(waypoint: Waypoint): Long = dao.insert(waypoint)

    /** Returns the waypoint with [id], or `null` if it no longer exists. */
    suspend fun get(id: Long): Waypoint? = dao.getById(id)

    /** Removes the waypoint with [id]. */
    suspend fun delete(id: Long) = dao.deleteById(id)
}
