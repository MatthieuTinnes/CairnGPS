package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [RecordEntry] rows. */
@Dao
interface RecordDao {

    /** Observes every record row currently held. Re-emits on any change to the table. */
    @Query("SELECT * FROM records")
    fun observeAll(): Flow<List<RecordEntry>>

    /** Returns every record row currently held, for exporting a backup. */
    @Query("SELECT * FROM records")
    suspend fun getAll(): List<RecordEntry>

    /** Returns the current record for [type], or `null` if none has been set yet. */
    @Query("SELECT * FROM records WHERE type = :type")
    suspend fun getByType(type: String): RecordEntry?

    /** Inserts the row for [entry], replacing any existing row for the same [RecordEntry.type]. */
    @Upsert
    suspend fun upsert(entry: RecordEntry)

    /**
     * Inserts every record in [entries], replacing any existing row for the same
     * [RecordEntry.type]. Used to restore a backup.
     */
    @Upsert
    suspend fun upsertAll(entries: List<RecordEntry>)

    /** Deletes every record, used before restoring a backup. */
    @Query("DELETE FROM records")
    suspend fun deleteAll()
}
