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
}
