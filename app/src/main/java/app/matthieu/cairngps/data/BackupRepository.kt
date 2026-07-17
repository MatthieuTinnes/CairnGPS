package app.matthieu.cairngps.data

import androidx.room.withTransaction
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Why an import was rejected — lets the UI layer pick the right localized message. */
enum class BackupImportError {
    /** The file isn't valid JSON, or doesn't match the [CairnBackup] shape. */
    UNREADABLE,

    /** [CairnBackup.version] is newer than [CairnBackup.CURRENT_VERSION]. */
    FUTURE_VERSION,
}

/** Thrown when an imported file isn't a [CairnBackup] this app version can restore. */
class InvalidBackupException(val reason: BackupImportError, cause: Throwable? = null) :
    Exception("Invalid backup file: $reason", cause)

/**
 * Exports and imports the full set of user data (waypoints, sessions, track points, records,
 * achievements, settings) as a single [CairnBackup] JSON file — see the Settings screen.
 *
 * Streams are plain `java.io` types rather than `Uri`/`ContentResolver`, so this class stays free
 * of Android UI concerns; the caller (a Composable, via [android.content.ContentResolver]) opens
 * the stream chosen through the system file picker and hands it in. This class writes/reads but
 * never closes the stream — the caller owns that lifecycle.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val waypointDao: WaypointDao,
    private val sessionDao: SessionDao,
    private val trackPointDao: TrackPointDao,
    private val recordDao: RecordDao,
    private val achievementDao: AchievementDao,
    private val settingsRepository: SettingsRepository,
) {

    /** Writes every piece of persisted user data to [output] as pretty-printed JSON. */
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val backup = CairnBackup(
            exportedAt = System.currentTimeMillis(),
            waypoints = waypointDao.getAll(),
            sessions = sessionDao.getAll(),
            trackPoints = trackPointDao.getAll(),
            records = recordDao.getAll(),
            achievements = achievementDao.getAll(),
            settings = settingsRepository.current(),
        )
        // flush(), not close(): the caller owns the stream's lifecycle (see class doc).
        output.writer().apply {
            write(BackupJson.encodeToString(backup))
            flush()
        }
    }

    /**
     * Reads a [CairnBackup] from [input] and replaces every existing waypoint, session, track
     * point, record, achievement and setting with its contents. All-or-nothing: a malformed file
     * throws before any table is touched, and the whole restore runs in one transaction so a
     * mid-restore failure leaves the previous data intact.
     *
     * @throws InvalidBackupException if [input] isn't a readable [CairnBackup], or its
     *                                 [CairnBackup.version] is newer than this app understands.
     */
    suspend fun import(input: InputStream) = withContext(Dispatchers.IO) {
        val backup = try {
            BackupJson.decodeFromString<CairnBackup>(input.reader().readText())
        } catch (e: SerializationException) {
            throw InvalidBackupException(BackupImportError.UNREADABLE, e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException(BackupImportError.UNREADABLE, e)
        } catch (e: IOException) {
            throw InvalidBackupException(BackupImportError.UNREADABLE, e)
        }
        if (backup.version > CairnBackup.CURRENT_VERSION) {
            throw InvalidBackupException(BackupImportError.FUTURE_VERSION)
        }

        database.withTransaction {
            // Children before parents: track points and waypoints reference sessions.
            trackPointDao.deleteAll()
            waypointDao.deleteAll()
            recordDao.deleteAll()
            achievementDao.deleteAll()
            sessionDao.deleteAll()

            // Parents before children on the way back in, so foreign keys always resolve.
            sessionDao.insertAll(backup.sessions)
            waypointDao.insertAll(backup.waypoints)
            trackPointDao.insertAll(backup.trackPoints)
            recordDao.upsertAll(backup.records)
            achievementDao.insertAll(backup.achievements)
        }
        settingsRepository.replaceAll(backup.settings)
    }
}
