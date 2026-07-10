package app.matthieu.cairngps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The app's single Room database and the source of truth for all persisted data.
 * Bump [version] and provide a migration whenever the schema changes.
 */
@Database(
    entities = [Waypoint::class, Session::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun waypointDao(): WaypointDao

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Adds the `sessions` table and links `waypoints` to it via a nullable `sessionId` FK
         * (`ON DELETE SET NULL`, so deleting a trace never deletes the waypoints saved during it).
         * Column types/constraints mirror exactly what Room generates from the entities so the
         * post-migration schema passes Room's runtime validation.
         */
        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sessions` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`startTimestamp` INTEGER NOT NULL, " +
                    "`endTimestamp` INTEGER NOT NULL, " +
                    "`distanceMeters` REAL NOT NULL, " +
                    "`averageSpeed` REAL NOT NULL, " +
                    "`maxSpeed` REAL NOT NULL, " +
                    "`elevationGain` REAL NOT NULL, " +
                    "`elevationLoss` REAL NOT NULL, " +
                    "`minAltitude` REAL NOT NULL, " +
                    "`maxAltitude` REAL NOT NULL, " +
                    "`latitudeMax` REAL NOT NULL, " +
                    "`latitudeMin` REAL NOT NULL, " +
                    "`longitudeMax` REAL NOT NULL, " +
                    "`longitudeMin` REAL NOT NULL)",
            )
            db.execSQL(
                "ALTER TABLE `waypoints` ADD COLUMN `sessionId` INTEGER " +
                    "REFERENCES `sessions`(`id`) ON DELETE SET NULL",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_waypoints_sessionId` ON `waypoints` (`sessionId`)",
            )
        }

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
            )
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
