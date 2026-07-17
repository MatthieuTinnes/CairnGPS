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

    /** Returns every track point currently stored, for exporting a backup. */
    @Query("SELECT * FROM track_points")
    suspend fun getAll(): List<TrackPoint>

    /** Deletes every track point, used before restoring a backup. */
    @Query("DELETE FROM track_points")
    suspend fun deleteAll()

    /** Observes the track for [sessionId] ordered chronologically. Re-emits on any table change. */
    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: Long): Flow<List<TrackPoint>>
}
