package app.matthieu.cairngps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The app's single Room database and the source of truth for all persisted data.
 * Bump [version] and provide a migration whenever the schema changes.
 */
@Database(
    entities = [Waypoint::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun waypointDao(): WaypointDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Returns the process-wide database singleton, building it on first access. */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "cairn.db",
            ).build()
    }
}
