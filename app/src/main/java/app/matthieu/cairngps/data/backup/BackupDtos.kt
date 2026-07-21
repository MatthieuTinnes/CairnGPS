package app.matthieu.cairngps.data.backup

import app.matthieu.cairngps.data.AchievementState
import app.matthieu.cairngps.data.GamificationFlag
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.Waypoint
import kotlinx.serialization.Serializable

/**
 * Serialization DTOs mirroring the Room `@Entity` classes field-for-field, kept deliberately
 * separate from them: [CairnBackup] serializes these, never the entities directly, so renaming or
 * restructuring a Room entity can no longer silently change the backup file's JSON shape. Every
 * DTO here matches its entity's current fields exactly (same names/types/defaults), which is what
 * keeps existing [CairnBackup.CURRENT_VERSION] = 1 backups importable.
 *
 * Any deliberate change to a DTO's shape (not just its source entity) must bump
 * [CairnBackup.CURRENT_VERSION] and get an explicit upgrade path in [CairnBackup] — see its doc.
 */
@Serializable
data class WaypointDto(
    val id: Long = 0,
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

fun Waypoint.toDto() = WaypointDto(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    horizontalAccuracy = horizontalAccuracy,
    satellitesUsedInFix = satellitesUsedInFix,
    timestamp = timestamp,
    sessionId = sessionId,
    icon = icon,
)

fun WaypointDto.toEntity() = Waypoint(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    horizontalAccuracy = horizontalAccuracy,
    satellitesUsedInFix = satellitesUsedInFix,
    timestamp = timestamp,
    sessionId = sessionId,
    icon = icon,
)

/**
 * Deliberately has no `isActive` field: a backup is a snapshot of finished data, and an
 * in-progress recording's live accumulator ([app.matthieu.cairngps.data.RecordingCheckpoint])
 * isn't backed up at all, so restoring one as "active" would leave it stuck. [toEntity] always
 * restores as an inactive (finished) session.
 */
@Serializable
data class SessionDto(
    val id: Long = 0,
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
)

fun Session.toDto() = SessionDto(
    id = id,
    name = name,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    distanceMeters = distanceMeters,
    averageSpeed = averageSpeed,
    maxSpeed = maxSpeed,
    elevationGain = elevationGain,
    elevationLoss = elevationLoss,
    minAltitude = minAltitude,
    maxAltitude = maxAltitude,
    latitudeMax = latitudeMax,
    latitudeMin = latitudeMin,
    longitudeMax = longitudeMax,
    longitudeMin = longitudeMin,
)

fun SessionDto.toEntity() = Session(
    id = id,
    name = name,
    startTimestamp = startTimestamp,
    endTimestamp = endTimestamp,
    distanceMeters = distanceMeters,
    averageSpeed = averageSpeed,
    maxSpeed = maxSpeed,
    elevationGain = elevationGain,
    elevationLoss = elevationLoss,
    minAltitude = minAltitude,
    maxAltitude = maxAltitude,
    latitudeMax = latitudeMax,
    latitudeMin = latitudeMin,
    longitudeMax = longitudeMax,
    longitudeMin = longitudeMin,
    // isActive defaults to false on Session — never restore a backup as an in-progress recording.
)

@Serializable
data class TrackPointDto(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
)

fun TrackPoint.toDto() = TrackPointDto(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
)

fun TrackPointDto.toEntity() = TrackPoint(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
)

@Serializable
data class RecordDto(
    val type: String,
    val value: Double,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val achievedAt: Long,
    val sessionId: Long? = null,
)

fun RecordEntry.toDto() = RecordDto(
    type = type,
    value = value,
    latitude = latitude,
    longitude = longitude,
    achievedAt = achievedAt,
    sessionId = sessionId,
)

fun RecordDto.toEntity() = RecordEntry(
    type = type,
    value = value,
    latitude = latitude,
    longitude = longitude,
    achievedAt = achievedAt,
    sessionId = sessionId,
)

@Serializable
data class AchievementStateDto(
    val id: String,
    val unlockedAt: Long,
)

fun AchievementState.toDto() = AchievementStateDto(id = id, unlockedAt = unlockedAt)

fun AchievementStateDto.toEntity() = AchievementState(id = id, unlockedAt = unlockedAt)

@Serializable
data class GamificationFlagDto(
    val key: String,
    val setAt: Long,
)

fun GamificationFlag.toDto() = GamificationFlagDto(key = key, setAt = setAt)

fun GamificationFlagDto.toEntity() = GamificationFlag(key = key, setAt = setAt)
