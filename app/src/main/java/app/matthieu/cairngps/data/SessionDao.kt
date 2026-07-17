package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [Session] rows. */
@Dao
interface SessionDao {

    /** Inserts a session and returns its generated [Session.id]. */
    @Insert
    suspend fun insert(session: Session): Long

    /**
     * Inserts every session in [sessions], replacing any existing row with the same id. Used to
     * restore a backup, where ids must be preserved to keep waypoint/track-point/record references
     * valid; must run before the child tables are restored.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<Session>)

    /** Returns every session currently stored, for exporting a backup. */
    @Query("SELECT * FROM sessions")
    suspend fun getAll(): List<Session>

    /** Deletes every session, used before restoring a backup. */
    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    /** Observes every session, most recently started first. Re-emits on any change to the table. */
    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC")
    fun observeAll(): Flow<List<Session>>

    /** Returns a single session by id, or `null` if it no longer exists. */
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    /** Deletes the session with the given id; a no-op if none matches. */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Renames the session with [id]; a no-op if none matches. */
    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}
