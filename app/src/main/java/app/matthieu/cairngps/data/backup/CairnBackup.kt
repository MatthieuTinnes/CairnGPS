package app.matthieu.cairngps.data.backup

import app.matthieu.cairngps.data.AppSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The full contents of a user data backup: every Room table (as its dedicated [WaypointDto] /
 * [SessionDto] / [TrackPointDto] / [RecordDto] / [AchievementStateDto], never the Room entities
 * themselves — see the doc on those DTOs) plus the DataStore [AppSettings], serialized to a single
 * JSON file the user can save/share/restore (see [app.matthieu.cairngps.data.BackupRepository]).
 *
 * @property version      Format version, bumped whenever a field is added/removed/renamed in a
 *                         way that breaks older readers. [app.matthieu.cairngps.data.BackupRepository.import]
 *                         rejects a file from a newer major version it doesn't understand.
 * @property exportedAt   When this backup was produced, in milliseconds since the epoch — shown to
 *                         the user before they confirm a restore.
 */
@Serializable
data class CairnBackup(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val waypoints: List<WaypointDto>,
    val sessions: List<SessionDto>,
    val trackPoints: List<TrackPointDto>,
    val records: List<RecordDto>,
    val achievements: List<AchievementStateDto>,
    val settings: AppSettings,
    // Defaulted to empty so a backup exported before the gamification_flags table existed still
    // imports cleanly (ignoreUnknownKeys handles the reverse: a newer backup opened by this
    // version, since the field is simply absent from CairnBackup.version's understood shape only
    // when it grows a *breaking* change, which this isn't).
    val flags: List<GamificationFlagDto> = emptyList(),
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
