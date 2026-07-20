package app.matthieu.cairngps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The in-memory accumulator of an active [RecordingRepository] recording, durably checkpointed
 * alongside the active [Session] row so a process death mid-recording can be resumed instead of
 * losing the whole trace. A single row (keyed by [sessionId], the active session), upserted at the
 * same cadence as track-point sampling — see [RecordingRepository.onFix] — so the extra writes
 * this adds stay battery-friendly rather than firing on every GPS fix.
 *
 * Deliberately not part of any backup: an in-progress recording isn't finished data (see
 * `app.matthieu.cairngps.data.backup.SessionDto`), and this checkpoint alone — without the raw
 * fixes that produced it — wouldn't let a different app install resume it anyway.
 *
 * @property sessionId               Id of the active [Session] this checkpoint belongs to.
 * @property movingDistanceMeters    Running total of [RecordingRepository]'s moving-only distance.
 * @property movingTimeMs            Running total of time spent above the moving-speed threshold.
 * @property referenceAltitude       Last altitude counted into D+/D-, or `null` before any fix.
 * @property lastLatitude            Latitude of the last accepted fix.
 * @property lastLongitude           Longitude of the last accepted fix.
 * @property lastAltitude            Altitude of the last accepted fix, in meters.
 * @property lastSpeed               Speed of the last accepted fix, in m/s.
 * @property lastHorizontalAccuracy  Horizontal accuracy of the last accepted fix, in meters.
 * @property lastTimestamp           Timestamp of the last accepted fix, in milliseconds since the epoch.
 * @property lastSampledAtMs         Fix timestamp the track-sampling cadence was last measured from.
 */
@Entity(tableName = "recording_checkpoint")
data class RecordingCheckpoint(
    @PrimaryKey val sessionId: Long,
    val movingDistanceMeters: Double,
    val movingTimeMs: Long,
    val referenceAltitude: Double?,
    val lastLatitude: Double,
    val lastLongitude: Double,
    val lastAltitude: Double,
    val lastSpeed: Float,
    val lastHorizontalAccuracy: Float,
    val lastTimestamp: Long,
    val lastSampledAtMs: Long,
)
