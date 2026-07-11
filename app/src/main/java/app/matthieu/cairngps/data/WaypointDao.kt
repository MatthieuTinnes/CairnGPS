package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [Waypoint] rows. */
@Dao
interface WaypointDao {

    /** Inserts a waypoint and returns its generated [Waypoint.id]. */
    @Insert
    suspend fun insert(waypoint: Waypoint): Long

    /** Observes every waypoint, most recent first. Re-emits on any change to the table. */
    @Query("SELECT * FROM waypoints ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Waypoint>>

    /** Returns a single waypoint by id, or `null` if it no longer exists. */
    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getById(id: Long): Waypoint?

    /** Deletes the waypoint with the given id; a no-op if none matches. */
    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Observes the waypoints attached to session [sessionId], most recent first. */
    @Query("SELECT * FROM waypoints WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun observeBySession(sessionId: Long): Flow<List<Waypoint>>

    /** Attaches every waypoint in [ids] to session [sessionId]; a no-op for an empty list. */
    @Query("UPDATE waypoints SET sessionId = :sessionId WHERE id IN (:ids)")
    suspend fun attachToSession(ids: List<Long>, sessionId: Long)

    /** Renames the waypoint with [id]; a no-op if none matches. */
    @Query("UPDATE waypoints SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}
