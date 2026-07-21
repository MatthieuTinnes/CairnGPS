package app.matthieu.cairngps.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real 1→7 migration chain against a hand-seeded legacy v1 database, since
 * `app/schemas` only exports the current (v7) schema — versions 1–6 were never committed, so
 * `androidx.room:room-testing`'s `MigrationTestHelper` (which needs a starting-version schema
 * file) can't be used here. Room's own "legacy SQLite → Room" path stands in for it instead: open
 * a hand-written v1 file through [Room.databaseBuilder] with every migration registered, and let
 * Room run the chain then validate the result against the compiled v7 entities itself — a wrong
 * migration throws on open, which is the test. A future 7→8 migration, once `7.json` exists as a
 * *previous* schema, would be a good candidate for `MigrationTestHelper` instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile = context.getDatabasePath("migration-test.db")
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        dbFile.delete()
    }

    /** The [Waypoint] table as it existed at v1: before `sessionId` (1→2) and `icon` (4→5). */
    private fun seedV1(insertStatements: List<String>) {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { seed ->
            seed.execSQL(
                "CREATE TABLE IF NOT EXISTS `waypoints` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`latitude` REAL NOT NULL, " +
                    "`longitude` REAL NOT NULL, " +
                    "`altitude` REAL NOT NULL, " +
                    "`speed` REAL NOT NULL, " +
                    "`horizontalAccuracy` REAL NOT NULL, " +
                    "`satellitesUsedInFix` INTEGER, " +
                    "`timestamp` INTEGER NOT NULL)",
            )
            insertStatements.forEach(seed::execSQL)
            seed.version = 1
        }
    }

    private fun openMigrated(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.name)
        .addMigrations(*AppDatabase.ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()
        .also { db = it }

    @Test
    fun `migrating a v1 database to v6 preserves seeded waypoints with the new defaults`() = runBlocking {
        seedV1(
            listOf(
                "INSERT INTO waypoints (name, latitude, longitude, altitude, speed, horizontalAccuracy, satellitesUsedInFix, timestamp) " +
                    "VALUES ('Col de la Schlucht', 48.05, 7.02, 1139.0, 0.0, 4.5, 12, 1700000000000)",
                "INSERT INTO waypoints (name, latitude, longitude, altitude, speed, horizontalAccuracy, satellitesUsedInFix, timestamp) " +
                    "VALUES ('No-sat point', 48.1, 7.1, 900.0, 1.2, 8.0, NULL, 1700000100000)",
            ),
        )

        val database = openMigrated()
        val waypoints = database.waypointDao().getAll().sortedBy { it.timestamp }

        assertEquals(2, waypoints.size)
        val first = waypoints[0]
        assertEquals("Col de la Schlucht", first.name)
        assertEquals(48.05, first.latitude, 0.0)
        assertEquals(7.02, first.longitude, 0.0)
        assertEquals(1139.0, first.altitude, 0.0)
        assertEquals(12, first.satellitesUsedInFix)
        assertNull(first.sessionId) // column added in 1->2, no session existed pre-migration
        assertEquals("flag", first.icon) // column added in 4->5, defaults to "flag"

        val second = waypoints[1]
        assertEquals("No-sat point", second.name)
        assertNull(second.satellitesUsedInFix)
    }

    @Test
    fun `migrated database accepts reads and writes on every table added by a migration`() = runBlocking {
        seedV1(
            listOf(
                "INSERT INTO waypoints (name, latitude, longitude, altitude, speed, horizontalAccuracy, satellitesUsedInFix, timestamp) " +
                    "VALUES ('Depart', 45.0, 6.0, 1000.0, 0.0, 5.0, 8, 1700000000000)",
            ),
        )
        val database = openMigrated()

        // 1->2: sessions table. A normally-inserted (finished) session defaults isActive to false.
        val sessionId = database.sessionDao().insert(
            Session(
                name = "Trace", startTimestamp = 0L, endTimestamp = 1_000L,
                distanceMeters = 100.0, averageSpeed = 1f, maxSpeed = 2f,
                elevationGain = 10.0, elevationLoss = 5.0, minAltitude = 990.0, maxAltitude = 1010.0,
                latitudeMax = 45.1, latitudeMin = 44.9, longitudeMax = 6.1, longitudeMin = 5.9,
            ),
        )
        assertEquals(sessionId, database.sessionDao().getAll().single { it.id == sessionId }.id)
        assertNull(database.sessionDao().getActive()) // 5->6 isActive column: not flagged active

        // 2->3: records + achievements tables.
        database.recordDao().upsert(RecordEntry(type = RecordType.MAX_SPEED.name, value = 12.0, achievedAt = 0L))
        assertEquals(1, database.recordDao().getAll().size)
        database.achievementDao().insert(AchievementState(id = "sessions_1", unlockedAt = 0L))
        assertEquals(1, database.achievementDao().getAll().size)

        // 3->4: track_points table, FK'd to the session created above.
        database.trackPointDao().insert(
            TrackPoint(sessionId = sessionId, timestamp = 0L, latitude = 45.0, longitude = 6.0, altitude = 1000.0),
        )
        assertEquals(1, database.trackPointDao().getBySession(sessionId).size)

        // 5->6: recording_checkpoint table.
        database.recordingCheckpointDao().upsert(
            RecordingCheckpoint(
                sessionId = sessionId, movingDistanceMeters = 1.0, movingTimeMs = 1000L, referenceAltitude = 1000.0,
                lastLatitude = 45.0, lastLongitude = 6.0, lastAltitude = 1000.0, lastSpeed = 1f,
                lastHorizontalAccuracy = 5f, lastTimestamp = 0L, lastSampledAtMs = 0L,
            ),
        )
        assertEquals(sessionId, database.recordingCheckpointDao().get()?.sessionId)
        database.recordingCheckpointDao().deleteAll()
        assertNull(database.recordingCheckpointDao().get())
    }

    @Test
    fun `a pre-migration waypoint can attach to a session created after migrating`() = runBlocking {
        seedV1(
            listOf(
                "INSERT INTO waypoints (name, latitude, longitude, altitude, speed, horizontalAccuracy, satellitesUsedInFix, timestamp) " +
                    "VALUES ('Sommet', 45.9, 6.87, 4805.0, 0.0, 5.0, 14, 1700000000000)",
            ),
        )
        val database = openMigrated()
        val seededId = database.waypointDao().getAll().single().id

        val sessionId = database.sessionDao().insert(
            Session(
                name = "Trace", startTimestamp = 0L, endTimestamp = 1_000L,
                distanceMeters = 0.0, averageSpeed = 0f, maxSpeed = 0f,
                elevationGain = 0.0, elevationLoss = 0.0, minAltitude = 0.0, maxAltitude = 0.0,
                latitudeMax = 0.0, latitudeMin = 0.0, longitudeMax = 0.0, longitudeMin = 0.0,
            ),
        )
        database.waypointDao().attachToSession(listOf(seededId), sessionId)

        assertEquals(sessionId, database.waypointDao().getById(seededId)?.sessionId)
    }

    @Test
    fun `migrated database accepts reads and writes on the 6-7 gamification_flags table`() = runBlocking {
        seedV1(emptyList())
        val database = openMigrated()

        database.gamificationFlagDao().insert(GamificationFlag(key = "app_export", setAt = 0L))
        assertEquals(1, database.gamificationFlagDao().getAll().size)
        // Setting the same flag again is a no-op (mirrors AchievementDao.insert's semantics).
        assertEquals(-1L, database.gamificationFlagDao().insert(GamificationFlag(key = "app_export", setAt = 1L)))
        assertEquals(1, database.gamificationFlagDao().getAll().size)

        database.gamificationFlagDao().deleteAll()
        assertEquals(0, database.gamificationFlagDao().getAll().size)
    }
}
