package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [TrackPoint] rows. */
@Dao
interface TrackPointDao {

    /** Inserts a batch of track points, typically the full downsampled track of one session. */
    @Insert
    suspend fun insertAll(points: List<TrackPoint>)

    /** Observes the track for [sessionId] ordered chronologically. Re-emits on any table change. */
    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: Long): Flow<List<TrackPoint>>

    /** Returns the track for [sessionId] ordered chronologically, as a one-shot read. */
    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: Long): List<TrackPoint>
}
