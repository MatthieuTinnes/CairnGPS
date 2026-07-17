package app.matthieu.cairngps.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A single sampled position captured while a [Session] was being recorded, used to draw the
 * altitude profile and route trace on the session detail screen.
 *
 * This is a downsampled subset of the fixes accepted during recording (see
 * [RecordingRepository]), not every raw GPS fix — keeping the table small regardless of how long
 * a session runs. Units mirror [LocationData].
 *
 * @property id           Auto-generated primary key.
 * @property sessionId    Id of the owning [Session]. `CASCADE` on delete: a track has no meaning
 *                         once its session is gone.
 * @property timestamp    Fix time, in milliseconds since the epoch.
 * @property latitude     Latitude in decimal degrees.
 * @property longitude    Longitude in decimal degrees.
 * @property altitude     Altitude in meters.
 */
@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
@Serializable
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
)
