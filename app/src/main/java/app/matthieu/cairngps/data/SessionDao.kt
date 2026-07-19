package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    /** Overwrites every column of an existing row, matched by [Session.id]. */
    @Update
    suspend fun update(session: Session)

    /** Returns every finished session currently stored, for exporting a backup. Excludes the row (if any) still being recorded — see [Session.isActive]. */
    @Query("SELECT * FROM sessions WHERE isActive = 0")
    suspend fun getAll(): List<Session>

    /** Deletes every session, used before restoring a backup. */
    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    /** Observes every finished session, most recently started first. Excludes the row (if any) still being recorded — see [Session.isActive]. */
    @Query("SELECT * FROM sessions WHERE isActive = 0 ORDER BY startTimestamp DESC")
    fun observeAll(): Flow<List<Session>>

    /**
     * Same as [observeAll] but joins each session with its track in one query (see
     * [SessionWithTrackPoints]), instead of the caller combining one [TrackPointDao.observeBySession]
     * Flow per session — avoids the N+1 observer pattern (audit 4.1).
     */
    @Transaction
    @Query("SELECT * FROM sessions WHERE isActive = 0 ORDER BY startTimestamp DESC")
    fun observeAllWithTracks(): Flow<List<SessionWithTrackPoints>>

    /** Returns a single session by id, or `null` if it no longer exists. */
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    /** The in-progress recording's session row, if the process was killed and restarted mid-recording. */
    @Query("SELECT * FROM sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): Session?

    /** Deletes the session with the given id; a no-op if none matches. */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Renames the session with [id]; a no-op if none matches. */
    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}
