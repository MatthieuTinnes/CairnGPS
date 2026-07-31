package app.matthieu.cairngps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data-access object for [AchievementState] rows. */
@Dao
interface AchievementDao {

    /** Observes every unlocked achievement. Re-emits on any change to the table. */
    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementState>>

    /**
     * Inserts [state], ignoring the call (and returning `-1`) if its id is already unlocked, so a
     * repeated unlock attempt for the same achievement is a safe no-op rather than overwriting the
     * original unlock date. The caller uses the return value to tell a fresh unlock apart from a
     * repeat, without needing a separate existence check.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(state: AchievementState): Long

    /** Returns every unlocked achievement, for exporting a backup. */
    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<AchievementState>

    /**
     * Inserts every achievement in [states], replacing any existing row with the same id. Used to
     * restore a backup, where the original unlock date must be preserved exactly.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(states: List<AchievementState>)

    /** Deletes every unlocked achievement, used before restoring a backup. */
    @Query("DELETE FROM achievements")
    suspend fun deleteAll()
}
