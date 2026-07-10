package app.matthieu.cairngps.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
 */
@Entity(tableName = "waypoints")
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
)
