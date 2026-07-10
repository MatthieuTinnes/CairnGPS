package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [Session] rows. */
@Dao
interface SessionDao {

    /** Inserts a session and returns its generated [Session.id]. */
    @Insert
    suspend fun insert(session: Session): Long

    /** Observes every session, most recently started first. Re-emits on any change to the table. */
    @Query("SELECT * FROM sessions ORDER BY startTimestamp DESC")
    fun observeAll(): Flow<List<Session>>

    /** Returns a single session by id, or `null` if it no longer exists. */
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?

    /** Deletes the session with the given id; a no-op if none matches. */
    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
