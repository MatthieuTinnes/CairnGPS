package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [TrackPoint] rows. */
@Dao
interface TrackPointDao {

    /**
     * Inserts a batch of track points, typically the full downsampled track of one session.
     * Replaces on id conflict so the same method also restores a backup, where ids must be
     * preserved; must run after the owning sessions are restored (`sessionId` FK).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrackPoint>)

    /** Appends a single sampled point, used to durably persist an in-progress recording's track. */
    @Insert
    suspend fun insert(point: TrackPoint): Long

    /** Returns every track point currently stored, for exporting a backup. */
    @Query("SELECT * FROM track_points")
    suspend fun getAll(): List<TrackPoint>

    /** Returns the track sampled so far for [sessionId], chronologically ordered — a one-shot read. */
    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: Long): List<TrackPoint>

    /** Deletes every track point, used before restoring a backup. */
    @Query("DELETE FROM track_points")
    suspend fun deleteAll()

    /** Deletes every track point for [sessionId], used to replace incremental samples with the final decimated set. */
    @Query("DELETE FROM track_points WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    /** Observes the track for [sessionId] ordered chronologically. Re-emits on any table change. */
    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: Long): Flow<List<TrackPoint>>
}
