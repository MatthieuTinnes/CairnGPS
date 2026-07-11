package app.matthieu.cairngps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The kind of extreme tracked by a [RecordEntry]. [higherIsBetter] decides whether
 * [RecordsRepository.submit] accepts a candidate value that is higher or lower than the one
 * currently stored.
 */
enum class RecordType(val higherIsBetter: Boolean) {
    MAX_SPEED(higherIsBetter = true),
    MAX_ALTITUDE(higherIsBetter = true),
    MIN_ALTITUDE(higherIsBetter = false),
    NORTHERNMOST(higherIsBetter = true),
    SOUTHERNMOST(higherIsBetter = false),
    EASTERNMOST(higherIsBetter = true),
    WESTERNMOST(higherIsBetter = false),
    MAX_ELEVATION_GAIN(higherIsBetter = true),
    MAX_DISTANCE(higherIsBetter = true),
    MAX_SATELLITES(higherIsBetter = true),
}

/**
 * The best value ever reached for a given [RecordType], across every session and live tracking
 * combined — a "hall of fame" row that only ever improves (see [RecordsRepository.submit]).
 * One row per [RecordType]; a type with no row yet simply has no record.
 *
 * @property type         Which record this row tracks; the primary key ([RecordType.name]).
 * @property value        The record value, in the same unit as its source metric (m/s for speeds,
 *                         meters for altitude/elevation/distance, degrees for the geographic
 *                         extremes).
 * @property latitude     Latitude where the record was set, for the geographic extremes.
 * @property longitude    Longitude where the record was set, for the geographic extremes.
 * @property achievedAt   When the record was set, in milliseconds since the epoch.
 * @property sessionId    The session the record came from, or `null` if set from live tracking.
 */
@Entity(tableName = "records")
data class RecordEntry(
    @PrimaryKey val type: String,
    val value: Double,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val achievedAt: Long,
    val sessionId: Long? = null,
)
