package app.matthieu.cairngps.data

import android.location.Location

/**
 * A single GPS fix, normalized into plain units.
 *
 * @property latitude       Latitude in decimal degrees.
 * @property longitude      Longitude in decimal degrees.
 * @property altitude       Altitude above mean sea level (EGM96 geoid), in meters. GPS chips
 *                          report altitude above the WGS84 ellipsoid, which reads tens of
 *                          meters off from map altitudes (Komoot, IGN...); [toLocationData]
 *                          corrects for that using [Egm96Geoid].
 * @property speed          Ground speed, in meters per second.
 * @property horizontalAccuracy Estimated horizontal accuracy (radius, 68% confidence), in meters.
 * @property verticalAccuracy   Estimated vertical accuracy, in meters, or `null` if the
 *                              current fix does not report one.
 * @property timestamp      UTC time of this fix, in milliseconds since the epoch.
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val horizontalAccuracy: Float,
    val verticalAccuracy: Float?,
    val timestamp: Long,
)

/**
 * Maps a framework [Location] into our domain [LocationData], converting altitude from the
 * WGS84 ellipsoid to mean sea level using [geoidSeparationMeters] (the local EGM96 undulation,
 * see [Egm96Geoid.separationMeters]).
 */
fun Location.toLocationData(geoidSeparationMeters: Double): LocationData = LocationData(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude - geoidSeparationMeters,
    speed = speed,
    horizontalAccuracy = accuracy,
    verticalAccuracy = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
    timestamp = time,
)
