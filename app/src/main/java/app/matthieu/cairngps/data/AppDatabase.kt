package app.matthieu.cairngps.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import app.matthieu.cairngps.demo.DemoMode

/**
 * The app's single Room database and the source of truth for all persisted data.
 * Bump [version] and provide a migration whenever the schema changes.
 */
@Database(
    entities = [
        Waypoint::class,
        Session::class,
        RecordEntry::class,
        AchievementState::class,
        TrackPoint::class,
        RecordingCheckpoint::class,
        GamificationFlag::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun waypointDao(): WaypointDao

    abstract fun sessionDao(): SessionDao

    abstract fun recordDao(): RecordDao

    abstract fun achievementDao(): AchievementDao

    abstract fun trackPointDao(): TrackPointDao

    abstract fun recordingCheckpointDao(): RecordingCheckpointDao

    abstract fun gamificationFlagDao(): GamificationFlagDao

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

        /**
         * Adds the `records` and `achievements` tables backing the gamification layer: cross-
         * session extremes (records) and unlocked-achievement state, respectively. Neither table
         * references existing ones, so this migration only creates tables — no data movement.
         */
        private val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `records` (" +
                    "`type` TEXT NOT NULL, " +
                    "`value` REAL NOT NULL, " +
                    "`latitude` REAL, " +
                    "`longitude` REAL, " +
                    "`achievedAt` INTEGER NOT NULL, " +
                    "`sessionId` INTEGER, " +
                    "PRIMARY KEY(`type`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `achievements` (" +
                    "`id` TEXT NOT NULL, " +
                    "`unlockedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
        }

        /**
         * Adds the `track_points` table backing the altitude profile / route trace on the session
         * detail screen: one row per downsampled fix accepted during a recording, linked to its
         * `sessions` row via a `CASCADE`-on-delete foreign key (a track is meaningless once its
         * session is gone, unlike waypoints which survive their session's deletion).
         */
        private val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `track_points` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`sessionId` INTEGER NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`latitude` REAL NOT NULL, " +
                    "`longitude` REAL NOT NULL, " +
                    "`altitude` REAL NOT NULL, " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_track_points_sessionId` ON `track_points` (`sessionId`)",
            )
        }

        /**
         * Adds the `icon` column backing the waypoint icon picker (screens 6a/6b): a stable key
         * (e.g. `"flag"`) into `WaypointIcons`, not a raw font codepoint, so it survives font
         * changes. Every existing waypoint defaults to `"flag"`, preserving today's look.
         */
        private val MIGRATION_4_5 = Migration(4, 5) { db ->
            db.execSQL(
                "ALTER TABLE `waypoints` ADD COLUMN `icon` TEXT NOT NULL DEFAULT 'flag'",
            )
        }

        /**
         * Adds what [RecordingRepository] needs to resume an in-progress recording after the
         * process is killed mid-hike: an `isActive` flag on `sessions` (0 for every session that
         * existed before this migration — they are all finished) and the single-row
         * `recording_checkpoint` table holding the live accumulator. See [Session.isActive] and
         * [RecordingCheckpoint].
         */
        private val MIGRATION_5_6 = Migration(5, 6) { db ->
            db.execSQL(
                "ALTER TABLE `sessions` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recording_checkpoint` (" +
                    "`sessionId` INTEGER PRIMARY KEY NOT NULL, " +
                    "`movingDistanceMeters` REAL NOT NULL, " +
                    "`movingTimeMs` INTEGER NOT NULL, " +
                    "`referenceAltitude` REAL, " +
                    "`lastLatitude` REAL NOT NULL, " +
                    "`lastLongitude` REAL NOT NULL, " +
                    "`lastAltitude` REAL NOT NULL, " +
                    "`lastSpeed` REAL NOT NULL, " +
                    "`lastHorizontalAccuracy` REAL NOT NULL, " +
                    "`lastTimestamp` INTEGER NOT NULL, " +
                    "`lastSampledAtMs` INTEGER NOT NULL)",
            )
        }

        /**
         * Adds the `gamification_flags` table backing the succès catalog's `ETAT`/`EVENEMENT`
         * conditions (both hemispheres visited, both themes used, a trace exported...) — see
         * [GamificationFlag]. No existing table changes: these flags are new, independent state.
         */
        private val MIGRATION_6_7 = Migration(6, 7) { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `gamification_flags` (" +
                    "`key` TEXT NOT NULL, " +
                    "`setAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`key`))",
            )
        }

        /** Exposed for migration tests, which need to build their own [Room.databaseBuilder]. */
        internal val ALL_MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        /** Returns the process-wide database singleton, building it on first access. */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        // Demo mode (debug builds only) opens a separate file seeded with fictional data, so a
        // screenshot session never reads — or writes to — the real hikes in cairn.db.
        private fun databaseName(): String = if (DemoMode.isEnabled) "cairn-demo.db" else "cairn.db"

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                databaseName(),
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
