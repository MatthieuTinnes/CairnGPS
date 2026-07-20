package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Room data-access object for the single-row [RecordingCheckpoint]. */
@Dao
interface RecordingCheckpointDao {

    /** Replaces the checkpoint row (there is at most one, for whichever recording is active). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: RecordingCheckpoint)

    /** Returns the current checkpoint, or `null` if no recording has sampled a fix yet. */
    @Query("SELECT * FROM recording_checkpoint LIMIT 1")
    suspend fun get(): RecordingCheckpoint?

    /** Clears the checkpoint once a recording is finalized or discarded. */
    @Query("DELETE FROM recording_checkpoint")
    suspend fun deleteAll()
}
