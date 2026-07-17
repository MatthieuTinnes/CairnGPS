package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [Waypoint] rows. */
@Dao
interface WaypointDao {

    /** Inserts a waypoint and returns its generated [Waypoint.id]. */
    @Insert
    suspend fun insert(waypoint: Waypoint): Long

    /**
     * Inserts every waypoint in [waypoints], replacing any existing row with the same id. Used to
     * restore a backup, where ids must be preserved to keep [Waypoint.sessionId] references valid.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(waypoints: List<Waypoint>)

    /** Returns every waypoint currently stored, for exporting a backup. */
    @Query("SELECT * FROM waypoints")
    suspend fun getAll(): List<Waypoint>

    /** Deletes every waypoint, used before restoring a backup. */
    @Query("DELETE FROM waypoints")
    suspend fun deleteAll()

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

    /** Changes the icon of the waypoint with [id]; a no-op if none matches. */
    @Query("UPDATE waypoints SET icon = :icon WHERE id = :id")
    suspend fun updateIcon(id: Long, icon: String)

    /** Renames the waypoint with [id] and changes its icon in one write; a no-op if none matches. */
    @Query("UPDATE waypoints SET name = :name, icon = :icon WHERE id = :id")
    suspend fun updateNameAndIcon(id: Long, name: String, icon: String)
}
