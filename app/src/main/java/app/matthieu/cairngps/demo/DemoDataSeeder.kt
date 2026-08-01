package app.matthieu.cairngps.demo

import app.matthieu.cairngps.data.GamificationFlag
import app.matthieu.cairngps.data.GamificationFlagDao
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionDao
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.TrackPointDao
import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointDao
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One sampled point every 30 s, the same order of magnitude a real recording keeps after decimation. */
private const val TRACK_POINT_INTERVAL_SECONDS = 30

/**
 * Each walk covers slightly more than one full loop. An exactly closed loop would end on its own
 * starting altitude and give D+ == D- to the meter on every single session, which reads as
 * generated the moment two sessions are compared side by side.
 */
private const val TRACK_LOOP_FRACTION = 1.015

/**
 * Peak instantaneous speed relative to the fastest 30-second segment. A real recording sees short
 * bursts well above any half-minute average, and `maxSpeed` is what the records page shows.
 */
private const val PEAK_SPEED_FACTOR = 1.35f

/**
 * Fills the throwaway demo database with a fictional hiking history the first time [DemoMode] runs.
 *
 * Only sessions, tracks, waypoints and the settings-related flags are written: records and
 * achievements are deliberately *not* seeded, because
 * [app.matthieu.cairngps.data.GamificationManager] already derives both from exactly this data as
 * soon as it observes it. Seeding them by hand would risk a demo whose records contradict its own
 * sessions.
 */
class DemoDataSeeder(
    private val sessionDao: SessionDao,
    private val trackPointDao: TrackPointDao,
    private val waypointDao: WaypointDao,
    private val gamificationFlagDao: GamificationFlagDao,
) {

    /** Populates the database unless it already holds a session; safe to call on every start. */
    suspend fun seedIfEmpty() {
        if (sessionDao.getAll().isNotEmpty()) return

        val today = LocalDate.now()
        val sessionIds = DEMO_SESSIONS.map { spec ->
            val startTimestamp = spec.startTimestamp(today)
            val track = spec.buildTrack(startTimestamp)
            val id = sessionDao.insert(spec.toSession(startTimestamp, track))
            trackPointDao.insertAll(track.map { it.copy(sessionId = id) })
            id
        }

        waypointDao.insertAll(
            DEMO_WAYPOINTS.map { spec ->
                spec.toWaypoint(today = today, sessionId = sessionIds.getOrNull(spec.sessionIndex))
            },
        )

        val now = System.currentTimeMillis()
        gamificationFlagDao.insertAll(DEMO_FLAGS.map { GamificationFlag(key = it, setAt = now) })
    }
}

/** A fictional recording: where it happened, when, how long, and the shape of its loop. */
private data class DemoSessionSpec(
    val name: String,
    val daysAgo: Long,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val baseAltitude: Double,
    val radiusMeters: Double,
    val climbMeters: Double,
    val shape: Double,
) {

    fun startTimestamp(today: LocalDate): Long =
        today.minusDays(daysAgo)
            .atTime(startTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** Walks a full loop of [DemoRoute] over the session's duration, sampled at a fixed cadence. */
    fun buildTrack(startTimestamp: Long): List<TrackPoint> {
        val route = DemoRoute(
            centerLatitude = centerLatitude,
            centerLongitude = centerLongitude,
            baseAltitude = baseAltitude,
            radiusMeters = radiusMeters,
            climbMeters = climbMeters,
            shape = shape,
        )
        val durationSeconds = durationMinutes * 60
        val steps = durationSeconds / TRACK_POINT_INTERVAL_SECONDS
        return (0..steps).map { step ->
            val elapsedSeconds = step * TRACK_POINT_INTERVAL_SECONDS
            val point = route.pointAt(TRACK_LOOP_FRACTION * elapsedSeconds / durationSeconds)
            TrackPoint(
                // Rewritten with the real session id once the session row exists.
                sessionId = 0L,
                timestamp = startTimestamp + elapsedSeconds * 1_000L,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude,
            )
        }
    }

    /**
     * Derives the session's aggregates from [track] rather than declaring them, so the history
     * list, the altitude profile and the records page can never disagree with the trace they are
     * drawn from.
     */
    fun toSession(startTimestamp: Long, track: List<TrackPoint>): Session {
        var distance = 0.0
        var elevationGain = 0.0
        var elevationLoss = 0.0
        var fastestSegment = 0.0
        for (i in 1 until track.size) {
            val previous = track[i - 1]
            val current = track[i]
            val segment = demoDistanceMeters(previous.toDemoPoint(), current.toDemoPoint())
            distance += segment
            fastestSegment = maxOf(fastestSegment, segment / TRACK_POINT_INTERVAL_SECONDS)
            val climb = current.altitude - previous.altitude
            if (climb > 0) elevationGain += climb else elevationLoss -= climb
        }
        val durationSeconds = durationMinutes * 60
        return Session(
            name = name,
            startTimestamp = startTimestamp,
            endTimestamp = startTimestamp + durationSeconds * 1_000L,
            distanceMeters = distance,
            averageSpeed = (distance / durationSeconds).toFloat(),
            maxSpeed = fastestSegment.toFloat() * PEAK_SPEED_FACTOR,
            elevationGain = elevationGain,
            elevationLoss = elevationLoss,
            minAltitude = track.minOf { it.altitude },
            maxAltitude = track.maxOf { it.altitude },
            latitudeMax = track.maxOf { it.latitude },
            latitudeMin = track.minOf { it.latitude },
            longitudeMax = track.maxOf { it.longitude },
            longitudeMin = track.minOf { it.longitude },
        )
    }
}

private fun TrackPoint.toDemoPoint() = DemoPoint(latitude, longitude, altitude)

/**
 * Ten fictional hikes spread over the past ten months across unrelated massifs. The spread is what
 * makes the gamification screens worth capturing: consecutive days for the streak achievement,
 * six distinct calendar months, a session crossing midnight, a fast one for the "cruise" condition,
 * a near-sea-level one, and a high point above 3000 m — while leaving plenty still locked.
 */
private val DEMO_SESSIONS = listOf(
    DemoSessionSpec(
        name = "Boucle des Écrins", daysAgo = 4, startTime = LocalTime.of(8, 15),
        durationMinutes = 80, centerLatitude = 45.0195, centerLongitude = 6.4062,
        baseAltitude = 2412.0, radiusMeters = 520.0, climbMeters = 420.0, shape = 0.9,
    ),
    DemoSessionSpec(
        name = "Plateau du Vercors", daysAgo = 12, startTime = LocalTime.of(9, 40),
        durationMinutes = 95, centerLatitude = 44.9530, centerLongitude = 5.4510,
        baseAltitude = 1355.0, radiusMeters = 900.0, climbMeters = 165.0, shape = 2.1,
    ),
    DemoSessionSpec(
        name = "Cirque et cascades", daysAgo = 13, startTime = LocalTime.of(7, 50),
        durationMinutes = 75, centerLatitude = 42.8330, centerLongitude = 0.1420,
        baseAltitude = 1905.0, radiusMeters = 470.0, climbMeters = 380.0, shape = 4.4,
    ),
    DemoSessionSpec(
        name = "Crêtes des Vosges", daysAgo = 14, startTime = LocalTime.of(10, 5),
        durationMinutes = 85, centerLatitude = 48.0020, centerLongitude = 7.1030,
        baseAltitude = 1148.0, radiusMeters = 610.0, climbMeters = 240.0, shape = 1.4,
    ),
    DemoSessionSpec(
        name = "Sentier du Jura", daysAgo = 38, startTime = LocalTime.of(16, 20),
        durationMinutes = 55, centerLatitude = 46.6210, centerLongitude = 6.0480,
        baseAltitude = 1402.0, radiusMeters = 380.0, climbMeters = 190.0, shape = 3.2,
    ),
    // Fast and long enough to satisfy the "cruise" condition (>= 5 km/h sustained over an hour).
    DemoSessionSpec(
        name = "Traversée du plateau", daysAgo = 55, startTime = LocalTime.of(9, 15),
        durationMinutes = 80, centerLatitude = 44.7800, centerLongitude = 6.2200,
        baseAltitude = 1720.0, radiusMeters = 1400.0, climbMeters = 120.0, shape = 5.0,
    ),
    DemoSessionSpec(
        name = "Vallée du Cantal", daysAgo = 71, startTime = LocalTime.of(8, 0),
        durationMinutes = 100, centerLatitude = 45.0610, centerLongitude = 2.8090,
        baseAltitude = 1596.0, radiusMeters = 700.0, climbMeters = 300.0, shape = 0.4,
    ),
    DemoSessionSpec(
        name = "Lacs du Mercantour", daysAgo = 124, startTime = LocalTime.of(7, 30),
        durationMinutes = 82, centerLatitude = 44.1020, centerLongitude = 7.0510,
        baseAltitude = 2208.0, radiusMeters = 560.0, climbMeters = 350.0, shape = 2.6,
    ),
    // Starts at 22:30 and runs past midnight, and dips to sea level on the way down.
    DemoSessionSpec(
        name = "Calanques et littoral", daysAgo = 196, startTime = LocalTime.of(22, 30),
        durationMinutes = 100, centerLatitude = 43.2090, centerLongitude = 5.4405,
        baseAltitude = 74.0, radiusMeters = 640.0, climbMeters = 88.0, shape = 3.8,
    ),
    DemoSessionSpec(
        name = "Balcon du Mont-Blanc", daysAgo = 285, startTime = LocalTime.of(6, 45),
        durationMinutes = 72, centerLatitude = 45.8720, centerLongitude = 6.8640,
        baseAltitude = 2755.0, radiusMeters = 480.0, climbMeters = 330.0, shape = 1.1,
    ),
)

/** A fictional saved waypoint, positioned relative to the session it was captured during. */
private data class DemoWaypointSpec(
    val name: String,
    val icon: String,
    /** Index into [DEMO_SESSIONS]; out of range means the waypoint was saved outside any recording. */
    val sessionIndex: Int,
    val daysAgo: Long,
    val time: LocalTime,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val satellitesUsedInFix: Int,
) {

    fun toWaypoint(today: LocalDate, sessionId: Long?): Waypoint = Waypoint(
        name = name,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = 0f, // Saving a waypoint means standing still.
        horizontalAccuracy = 3.4f,
        satellitesUsedInFix = satellitesUsedInFix,
        timestamp = today.minusDays(daysAgo)
            .atTime(time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        sessionId = sessionId,
        icon = icon,
    )
}

/**
 * Sixteen waypoints, one per icon in the picker — enough to show the list, the icon variety and
 * the per-session attachment. Two sit outside daylight hours (one after 22 h, one before 6 h) so
 * the night/dawn achievements are part of the captured state.
 */
private val DEMO_WAYPOINTS = listOf(
    DemoWaypointSpec("Col ouest", "terrain", 0, 4, LocalTime.of(9, 12), 45.0243, 6.4021, 2801.0, 24),
    DemoWaypointSpec("Névé sous la crête", "hiking", 0, 4, LocalTime.of(9, 38), 45.0210, 6.4098, 2744.0, 23),
    DemoWaypointSpec("Point de vue", "photo_camera", 1, 12, LocalTime.of(10, 25), 44.9584, 5.4552, 1489.0, 26),
    DemoWaypointSpec("Bifurcation", "pin_drop", 1, 12, LocalTime.of(11, 2), 44.9497, 5.4468, 1338.0, 22),
    DemoWaypointSpec("Source", "water_drop", 2, 13, LocalTime.of(8, 31), 42.8368, 0.1451, 2016.0, 21),
    DemoWaypointSpec("Gué", "waves", 2, 13, LocalTime.of(8, 54), 42.8302, 0.1389, 1874.0, 20),
    DemoWaypointSpec("Refuge", "cottage", 3, 14, LocalTime.of(11, 10), 48.0056, 7.1071, 1281.0, 25),
    DemoWaypointSpec("Hêtraie", "forest", 3, 14, LocalTime.of(11, 34), 47.9988, 7.0996, 1104.0, 23),
    DemoWaypointSpec("Cabane de berger", "cabin", 4, 38, LocalTime.of(16, 48), 46.6242, 6.0512, 1503.0, 22),
    DemoWaypointSpec("Aire de départ", "local_parking", 5, 55, LocalTime.of(9, 16), 44.7742, 6.2148, 1656.0, 27),
    DemoWaypointSpec("Table d'orientation", "star", 6, 71, LocalTime.of(9, 5), 45.0654, 2.8135, 1837.0, 24),
    DemoWaypointSpec("Halte casse-croûte", "restaurant", 6, 71, LocalTime.of(10, 20), 45.0578, 2.8046, 1601.0, 23),
    DemoWaypointSpec("Lac supérieur", "sailing", 7, 124, LocalTime.of(8, 12), 44.1063, 7.0548, 2447.0, 26),
    DemoWaypointSpec("Passage exposé", "warning", 7, 124, LocalTime.of(8, 40), 44.0982, 7.0473, 2310.0, 19),
    // Outside daylight hours: a late-evening stop and a pre-dawn start.
    DemoWaypointSpec("Belvédère de nuit", "flag", 8, 196, LocalTime.of(23, 10), 43.2131, 5.4448, 148.0, 21),
    DemoWaypointSpec("Départ avant l'aube", "park", 9, 285, LocalTime.of(5, 20), 45.8676, 6.8592, 2438.0, 20),
)

/**
 * Flags a fictional user would have accumulated by having used both themes, both coordinate
 * formats and the backup export/import. Left unset on purpose: `geo_confluence` and `cmp_decl`,
 * which are genuinely rare — a demo showing every achievement unlocked would hide the progression
 * bars that are worth capturing.
 */
private val DEMO_FLAGS = listOf(
    "theme_light",
    "theme_dark",
    "format_decimal",
    "format_dms",
    "app_export",
    "app_backup",
)
