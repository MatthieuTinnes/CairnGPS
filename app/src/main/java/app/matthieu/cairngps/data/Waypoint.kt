package app.matthieu.cairngps.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A user-saved GPS waypoint: a snapshot of the GPS state at capture time, tagged with a name.
 *
 * Units mirror [LocationData] (the capture source): speed is stored in m/s and converted to km/h
 * only at display time, altitude and accuracy are in meters.
 *
 * @property id                    Auto-generated primary key.
 * @property name                  User-entered label.
 * @property latitude              Latitude in decimal degrees.
 * @property longitude             Longitude in decimal degrees.
 * @property altitude              Altitude in meters.
 * @property speed                 Ground speed at capture, in meters per second.
 * @property horizontalAccuracy    Horizontal accuracy radius at capture, in meters.
 * @property satellitesUsedInFix   Number of satellites used in the fix at capture time, or `null`
 *                                 when no GNSS status was available.
 * @property timestamp             Creation time, in milliseconds since the epoch.
 * @property sessionId             Id of the [Session] this waypoint was captured during, or `null`
 *                                 when the waypoint was saved outside of a recording. `SET NULL` on
 *                                 delete: removing a trace never deletes the waypoints saved during it.
 * @property icon                  Stable key into `WaypointIcons` (e.g. `"flag"`) picking which
 *                                 glyph represents this waypoint. Stored as a name rather than a
 *                                 font codepoint so it survives font/theme changes; unknown keys
 *                                 (e.g. from a future app version) fall back to the flag.
 */
@Entity(
    tableName = "waypoints",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("sessionId")],
)
@Serializable
data class Waypoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val horizontalAccuracy: Float,
    val satellitesUsedInFix: Int?,
    val timestamp: Long,
    val sessionId: Long? = null,
    val icon: String = "flag",
)
