package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [GamificationFlag] rows. */
@Dao
interface GamificationFlagDao {

    /** Observes every set flag. Re-emits on any change to the table. */
    @Query("SELECT * FROM gamification_flags")
    fun observeAll(): Flow<List<GamificationFlag>>

    /**
     * Inserts [flag], ignoring the call (and returning `-1`) if its key is already set, so setting
     * an already-set flag again is a safe no-op rather than overwriting the original date.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(flag: GamificationFlag): Long

    /** Returns every set flag, for exporting a backup. */
    @Query("SELECT * FROM gamification_flags")
    suspend fun getAll(): List<GamificationFlag>

    /**
     * Inserts every flag in [flags], replacing any existing row with the same key. Used to restore
     * a backup, where the original set date must be preserved exactly.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flags: List<GamificationFlag>)

    /** Deletes every flag, used before restoring a backup. */
    @Query("DELETE FROM gamification_flags")
    suspend fun deleteAll()
}
