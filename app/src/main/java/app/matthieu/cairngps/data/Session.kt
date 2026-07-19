package app.matthieu.cairngps.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * A recorded track (session): the aggregated stats of a GPS recording between a start and an end
 * time. Individual GPS fixes are not stored, only the running aggregates computed live by
 * [RecordingRepository] while the recording was active.
 *
 * Units mirror [LocationData]: speeds in m/s, altitude/distance in meters.
 *
 * @property id               Auto-generated primary key.
 * @property name             User-editable label, defaulting to the start date/time.
 * @property startTimestamp   Recording start time, in milliseconds since the epoch.
 * @property endTimestamp     Recording end time, in milliseconds since the epoch.
 * @property distanceMeters   Total horizontal distance covered.
 * @property averageSpeed     Average speed while moving (stationary periods excluded), in m/s.
 * @property maxSpeed         Peak instantaneous speed, in m/s.
 * @property elevationGain    Cumulative positive elevation change (D+), in meters.
 * @property elevationLoss    Cumulative negative elevation change (D−), in meters.
 * @property minAltitude      Lowest altitude reached, in meters.
 * @property maxAltitude      Highest altitude reached, in meters.
 * @property latitudeMax      Northernmost latitude reached — feeds the geographic records (see
 *                             `GamificationManager`).
 * @property latitudeMin      Southernmost latitude reached.
 * @property longitudeMax     Easternmost longitude reached.
 * @property longitudeMin     Westernmost longitude reached.
 * @property isActive         Whether this row is the in-progress recording rather than a finished
 *                             session — see [RecordingRepository]'s class doc. Excluded from every
 *                             normal read path ([SessionDao.getAll]/[SessionDao.observeAll]), so a
 *                             recording in progress never leaks into records, achievements or the
 *                             history list with its still-incomplete aggregates.
 */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val distanceMeters: Double,
    val averageSpeed: Float,
    val maxSpeed: Float,
    val elevationGain: Double,
    val elevationLoss: Double,
    val minAltitude: Double,
    val maxAltitude: Double,
    val latitudeMax: Double,
    val latitudeMin: Double,
    val longitudeMax: Double,
    val longitudeMin: Double,
    val isActive: Boolean = false,
) {
    /** Recording duration, derived rather than stored. */
    val durationMillis: Long get() = endTimestamp - startTimestamp
}

/**
 * A [Session] joined with its [TrackPoint]s in a single Room query (see [SessionDao.observeAllWithTracks]),
 * avoiding one separate `trackForSession` observer per session. [Relation] doesn't guarantee
 * ordering, so callers needing chronological order must sort [track] themselves.
 */
data class SessionWithTrackPoints(
    @Embedded val session: Session,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val track: List<TrackPoint>,
)
