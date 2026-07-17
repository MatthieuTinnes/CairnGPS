package app.matthieu.cairngps.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The full contents of a user data backup: every Room table plus the DataStore [AppSettings],
 * serialized to a single JSON file the user can save/share/restore (see [BackupRepository]).
 *
 * @property version      Format version, bumped whenever a field is added/removed/renamed in a
 *                         way that breaks older readers. [BackupRepository.import] rejects a file
 *                         from a newer major version it doesn't understand.
 * @property exportedAt   When this backup was produced, in milliseconds since the epoch — shown to
 *                         the user before they confirm a restore.
 */
@Serializable
data class CairnBackup(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val waypoints: List<Waypoint>,
    val sessions: List<Session>,
    val trackPoints: List<TrackPoint>,
    val records: List<RecordEntry>,
    val achievements: List<AchievementState>,
    val settings: AppSettings,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Shared JSON configuration for backups: readable when opened in a text editor, and tolerant of
 * fields a newer app version may have added (so an older CairnGPS can still import a file exported
 * by a newer one, as long as [CairnBackup.version] itself stays compatible).
 */
val BackupJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}
