package app.matthieu.cairngps.domain.gamification

import androidx.annotation.StringRes
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordType
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.Waypoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A family groups achievements that share the same underlying metric (and, in the UI, the same
 * progression bar towards the next palier). [ALTITUDE]/[SPEED]/[SATELLITES]/[DISTANCE]/[SESSIONS]/
 * [REPERES] each have one; [BOUSSOLE], [GEO], [TIME] and [MAITRISE] don't — every achievement in
 * those families is a one-shot condition (see [AchievementDef.oneShotCheck]), not a scalar
 * threshold, so [progressToNext] naturally returns `null` for them (see [metricFor]).
 */
enum class AchievementFamily {
    ALTITUDE,
    SPEED,
    SATELLITES,
    DISTANCE,
    SESSIONS,
    GEO,
    TIME,
    REPERES,
    BOUSSOLE,
    MAITRISE,
}

/**
 * When [AchievementDef.isUnlocked] is (re-)evaluated. Purely descriptive/grouping — every type is
 * evaluated the same way (against the latest [GamificationMetrics] snapshot), since that snapshot
 * is cheaply recomputed on every change to the underlying persisted data (see
 * [app.matthieu.cairngps.data.GamificationManager]), making re-checking an already-unlocked
 * achievement a safe no-op regardless of what triggered the recomputation.
 */
enum class AchievementType {
    /** Evaluated against the current position/GnssStatus (a live fix or a lifetime record). */
    INSTANT,

    /** Evaluated against one finished session's own aggregates. */
    SESSION,

    /** Evaluated against aggregates across every session/waypoint ever recorded. */
    CUMULATIF,

    /** Evaluated against a persistent flag that, once set, never clears (see [GamificationMetrics.flags]). */
    ETAT,

    /** Evaluated on a specific user action (export, backup, theme/format switch...). */
    EVENEMENT,
}

/** The XP paliers a new achievement must be filed under — see `succes.md` §1. Only six values exist. */
enum class SuccesXp(val points: Int) {
    DECOUVERTE(10),
    FACILE(20),
    MOYEN(40),
    DIFFICILE(75),
    TRES_DIFFICILE(125),
    EXCEPTIONNEL(200),
}

/**
 * One unlockable achievement. [threshold] is compared against [GamificationMetrics.valueFor] for
 * [family] (see [Achievements.metricFor]) and is expressed in the metric's own unit: meters for
 * [AchievementFamily.ALTITUDE]/[AchievementFamily.DISTANCE], m/s for [AchievementFamily.SPEED],
 * a satellite count for [AchievementFamily.SATELLITES], a session count for
 * [AchievementFamily.SESSIONS], a waypoint count for [AchievementFamily.REPERES].
 * [oneShotCheck], when set, ignores [threshold] and the family's scalar metric entirely — used for
 * every condition that isn't a ">=" comparison (crossing a meridian, a locked/deferred achievement,
 * a flag-based [AchievementType.ETAT]/[AchievementType.EVENEMENT] condition...). [xp] is the amount
 * added to the user's lifetime total (see [Achievements.xpFor]) the moment this achievement unlocks.
 */
data class AchievementDef(
    val id: String,
    val family: AchievementFamily,
    val type: AchievementType,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val xp: SuccesXp,
    val threshold: Double = 0.0,
    val oneShotCheck: ((GamificationMetrics) -> Boolean)? = null,
)

/**
 * Live+historical aggregates the achievement catalog and the records page are evaluated against.
 * Built by [app.matthieu.cairngps.data.GamificationManager] from stored [app.matthieu.cairngps.data.RecordEntry]
 * rows, every [app.matthieu.cairngps.data.Session], every saved [Waypoint] and every persisted
 * [AchievementType.ETAT]/[AchievementType.EVENEMENT] flag. A `null`/zero/empty field means "no data
 * yet" — distinct from a genuine value, so an achievement never falsely unlocks before any fix,
 * session or flag has been seen.
 */
data class GamificationMetrics(
    val maxAltitude: Double? = null, // meters
    val minAltitude: Double? = null, // meters
    val maxSpeedMs: Float? = null, // m/s
    val maxSatellites: Int? = null,
    val maxConstellations: Int? = null,
    val maxSatelliteElevationDegrees: Float? = null,
    val minHorizontalAccuracy: Float? = null, // meters
    val minAbsLatitude: Double? = null, // degrees; lowest |latitude| ever recorded
    val cumulativeDistanceMeters: Double = 0.0,
    val maxSessionDistanceMeters: Double = 0.0,
    val cumulativeElevationGain: Double = 0.0, // meters, summed across every session
    val maxSessionElevationGain: Double = 0.0, // meters, best single session
    val maxSessionDurationMillis: Long = 0L,
    val hasCruiseSession: Boolean = false, // a session averaging >= 5 km/h for >= 1 hour
    val sessionCount: Int = 0,
    val longestDayStreak: Int = 0, // longest run of consecutive calendar days with a session
    val distinctMonthsCount: Int = 0, // distinct calendar months (Jan-Dec) with at least one session
    val hasMidnightSession: Boolean = false, // a session whose start/end local dates differ
    val hasNewYearSession: Boolean = false, // a session started on Jan 1st (local date)
    val hasSolsticeSession: Boolean = false, // a session started on Jun 21st (local date)
    val hasLeapDaySession: Boolean = false, // a session started on Feb 29th (local date)
    val latitudeMax: Double? = null,
    val latitudeMin: Double? = null,
    val longitudeMax: Double? = null,
    val longitudeMin: Double? = null,
    val hasNightWaypoint: Boolean = false, // a waypoint saved at local hour >= 22
    val hasDawnWaypoint: Boolean = false, // a waypoint saved at local hour < 6
    val waypointCount: Int = 0,
    val maxWaypointsInSession: Int = 0,
    val distinctWaypointIcons: Set<String> = emptySet(),
    /** Persisted [AchievementType.ETAT]/[AchievementType.EVENEMENT] flag keys ever set. */
    val flags: Set<String> = emptySet(),
)

/** The next locked achievement closest to unlocking, for the Succès screen's highlight card. */
data class NextAchievement(
    val def: AchievementDef,
    val progress: FamilyProgress,
)

/** Progress of one [AchievementFamily] towards its next locked palier, for the progress bar. */
data class FamilyProgress(
    val currentThreshold: Double,
    val nextThreshold: Double?, // null once every palier in the family is unlocked
    val currentValue: Double,
    val fraction: Float, // 0f..1f towards nextThreshold; 1f when nextThreshold is null
)

/**
 * The catalog of unlockable achievements — see `succes.md` for the full spec this mirrors.
 * Deliberately plain data (no Room annotations): only the *unlocked* state is persisted
 * (`AchievementState`), so adding a new palier here never requires a database migration.
 *
 * Six achievements are declared but never unlock yet (`oneShotCheck = { false }`, each tagged
 * `// STUB`): they need a dedicated subsystem (a persisted 1° visited-cells grid, a tracked globe
 * rotation, a held-heading timer, cardinal-direction distance tracking, a compass-guided arrival
 * check, or a stationary-time timer) that hasn't been built yet. They stay visible and locked
 * rather than absent, so the catalog size (and any future UI listing "every succès") matches the
 * spec's 72 without a half-finished mechanic silently unlocking early.
 */
object Achievements {

    /** Speed paliers are authored in km/h but compared against [GamificationMetrics.maxSpeedMs]. */
    private fun kmhToMs(kmh: Double) = kmh / 3.6

    /** Distance paliers are authored in km but compared against meters. */
    private fun kmToMeters(km: Double) = km * 1000.0

    /**
     * The waypoint icon keys `WPT_ICONES` requires one of each of, frozen here rather than read
     * from `WaypointIcons.all` — per `succes.md` §4.6, adding a new icon to the picker later must
     * never retroactively make an already-unlocked achievement incomplete again.
     */
    private val REFERENCE_WAYPOINT_ICONS = setOf(
        "flag", "terrain", "forest", "water_drop", "park", "hiking", "cottage", "cabin",
        "restaurant", "photo_camera", "local_parking", "warning", "star", "pin_drop", "sailing", "waves",
    )

    val ALL: List<AchievementDef> = listOf(
        // --- Altitude (13) ----------------------------------------------------------------------
        AchievementDef(
            "altitude_1000", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_1000_title, R.string.achievement_altitude_1000_desc,
            threshold = 1000.0, xp = SuccesXp.FACILE,
        ),
        AchievementDef(
            "altitude_puy_de_dome", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_puy_de_dome_title, R.string.achievement_altitude_puy_de_dome_desc,
            threshold = 1426.0, xp = SuccesXp.FACILE,
        ),
        AchievementDef(
            "altitude_2000", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_2000_title, R.string.achievement_altitude_2000_desc,
            threshold = 2000.0, xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "altitude_3000", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_3000_title, R.string.achievement_altitude_3000_desc,
            threshold = 3000.0, xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "altitude_neg", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_neg_title, R.string.achievement_altitude_neg_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> (m.minAltitude ?: Double.POSITIVE_INFINITY) <= 0.0 },
        ),
        AchievementDef(
            "altitude_fuji", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_fuji_title, R.string.achievement_altitude_fuji_desc,
            threshold = 3776.0, xp = SuccesXp.DIFFICILE,
        ),
        AchievementDef(
            "altitude_mont_blanc", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_mont_blanc_title, R.string.achievement_altitude_mont_blanc_desc,
            threshold = 4805.0, xp = SuccesXp.DIFFICILE,
        ),
        AchievementDef(
            "altitude_kilimanjaro", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_kilimanjaro_title, R.string.achievement_altitude_kilimanjaro_desc,
            threshold = 5825.0, xp = SuccesXp.TRES_DIFFICILE,
        ),
        AchievementDef(
            "altitude_denali", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_denali_title, R.string.achievement_altitude_denali_desc,
            threshold = 6190.0, xp = SuccesXp.TRES_DIFFICILE,
        ),
        AchievementDef(
            "altitude_everest", AchievementFamily.ALTITUDE, AchievementType.INSTANT,
            R.string.achievement_altitude_everest_title, R.string.achievement_altitude_everest_desc,
            threshold = 8849.0, xp = SuccesXp.EXCEPTIONNEL,
        ),
        AchievementDef(
            "dplus_500", AchievementFamily.ALTITUDE, AchievementType.SESSION,
            R.string.achievement_dplus_500_title, R.string.achievement_dplus_500_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> m.maxSessionElevationGain >= 500.0 },
        ),
        AchievementDef(
            "dplus_1000", AchievementFamily.ALTITUDE, AchievementType.SESSION,
            R.string.achievement_dplus_1000_title, R.string.achievement_dplus_1000_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> m.maxSessionElevationGain >= 1000.0 },
        ),
        AchievementDef(
            "dplus_everest", AchievementFamily.ALTITUDE, AchievementType.CUMULATIF,
            R.string.achievement_dplus_everest_title, R.string.achievement_dplus_everest_desc,
            xp = SuccesXp.DIFFICILE,
            oneShotCheck = { m -> m.cumulativeElevationGain >= 8849.0 },
        ),

        // --- Vitesse (7) --------------------------------------------------------------------------
        AchievementDef(
            "speed_30", AchievementFamily.SPEED, AchievementType.INSTANT,
            R.string.achievement_speed_30_title, R.string.achievement_speed_30_desc,
            threshold = kmhToMs(30.0), xp = SuccesXp.DECOUVERTE,
        ),
        AchievementDef(
            "speed_still", AchievementFamily.SPEED, AchievementType.SESSION,
            R.string.achievement_speed_still_title, R.string.achievement_speed_still_desc,
            xp = SuccesXp.DECOUVERTE,
            oneShotCheck = { false }, // STUB: needs a tracked stationary-time-in-session timer
        ),
        AchievementDef(
            "speed_50", AchievementFamily.SPEED, AchievementType.INSTANT,
            R.string.achievement_speed_50_title, R.string.achievement_speed_50_desc,
            threshold = kmhToMs(50.0), xp = SuccesXp.FACILE,
        ),
        AchievementDef(
            "speed_cruise", AchievementFamily.SPEED, AchievementType.SESSION,
            R.string.achievement_speed_cruise_title, R.string.achievement_speed_cruise_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> m.hasCruiseSession },
        ),
        AchievementDef(
            "speed_100", AchievementFamily.SPEED, AchievementType.INSTANT,
            R.string.achievement_speed_100_title, R.string.achievement_speed_100_desc,
            threshold = kmhToMs(100.0), xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "speed_300", AchievementFamily.SPEED, AchievementType.INSTANT,
            R.string.achievement_speed_300_title, R.string.achievement_speed_300_desc,
            threshold = kmhToMs(300.0), xp = SuccesXp.DIFFICILE,
        ),
        AchievementDef(
            "speed_800", AchievementFamily.SPEED, AchievementType.INSTANT,
            R.string.achievement_speed_800_title, R.string.achievement_speed_800_desc,
            threshold = kmhToMs(800.0), xp = SuccesXp.TRES_DIFFICILE,
        ),

        // --- Satellites (8) -----------------------------------------------------------------------
        AchievementDef(
            "satellites_8", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_satellites_8_title, R.string.achievement_satellites_8_desc,
            threshold = 8.0, xp = SuccesXp.DECOUVERTE,
        ),
        AchievementDef(
            "satellites_12", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_satellites_12_title, R.string.achievement_satellites_12_desc,
            threshold = 12.0, xp = SuccesXp.FACILE,
        ),
        AchievementDef(
            "sat_zenith", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_sat_zenith_title, R.string.achievement_sat_zenith_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> (m.maxSatelliteElevationDegrees ?: 0f) >= 85f },
        ),
        AchievementDef(
            "satellites_20", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_satellites_20_title, R.string.achievement_satellites_20_desc,
            threshold = 20.0, xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "sat_const_4", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_sat_const_4_title, R.string.achievement_sat_const_4_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> (m.maxConstellations ?: 0) >= 4 },
        ),
        AchievementDef(
            "acc_3m", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_acc_3m_title, R.string.achievement_acc_3m_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> (m.minHorizontalAccuracy ?: Float.POSITIVE_INFINITY) <= 3f },
        ),
        AchievementDef(
            "satellites_30", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_satellites_30_title, R.string.achievement_satellites_30_desc,
            threshold = 30.0, xp = SuccesXp.DIFFICILE,
        ),
        AchievementDef(
            "sat_const_6", AchievementFamily.SATELLITES, AchievementType.INSTANT,
            R.string.achievement_sat_const_6_title, R.string.achievement_sat_const_6_desc,
            xp = SuccesXp.TRES_DIFFICILE,
            oneShotCheck = { m -> (m.maxConstellations ?: 0) >= 6 },
        ),

        // --- Distance (8) -------------------------------------------------------------------------
        AchievementDef(
            "distance_10", AchievementFamily.DISTANCE, AchievementType.CUMULATIF,
            R.string.achievement_distance_10_title, R.string.achievement_distance_10_desc,
            threshold = kmToMeters(10.0), xp = SuccesXp.FACILE,
        ),
        AchievementDef(
            "distance_marathon", AchievementFamily.DISTANCE, AchievementType.CUMULATIF,
            R.string.achievement_distance_marathon_title, R.string.achievement_distance_marathon_desc,
            threshold = kmToMeters(42.195), xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "distance_50", AchievementFamily.DISTANCE, AchievementType.CUMULATIF,
            R.string.achievement_distance_50_title, R.string.achievement_distance_50_desc,
            threshold = kmToMeters(50.0), xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "distance_session_20", AchievementFamily.DISTANCE, AchievementType.SESSION,
            R.string.achievement_distance_session_20_title, R.string.achievement_distance_session_20_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> m.maxSessionDistanceMeters >= kmToMeters(20.0) },
        ),
        AchievementDef(
            "distance_100", AchievementFamily.DISTANCE, AchievementType.CUMULATIF,
            R.string.achievement_distance_100_title, R.string.achievement_distance_100_desc,
            threshold = kmToMeters(100.0), xp = SuccesXp.DIFFICILE,
        ),
        AchievementDef(
            "distance_500", AchievementFamily.DISTANCE, AchievementType.CUMULATIF,
            R.string.achievement_distance_500_title, R.string.achievement_distance_500_desc,
            threshold = kmToMeters(500.0), xp = SuccesXp.TRES_DIFFICILE,
        ),
        AchievementDef(
            "distance_1000", AchievementFamily.DISTANCE, AchievementType.CUMULATIF,
            R.string.achievement_distance_1000_title, R.string.achievement_distance_1000_desc,
            threshold = kmToMeters(1000.0), xp = SuccesXp.EXCEPTIONNEL,
        ),
        AchievementDef(
            "distance_equateur", AchievementFamily.DISTANCE, AchievementType.CUMULATIF,
            R.string.achievement_distance_equateur_title, R.string.achievement_distance_equateur_desc,
            threshold = kmToMeters(40_075.0), xp = SuccesXp.EXCEPTIONNEL,
        ),

        // --- Sessions (8) -------------------------------------------------------------------------
        AchievementDef(
            "sessions_1", AchievementFamily.SESSIONS, AchievementType.CUMULATIF,
            R.string.achievement_sessions_1_title, R.string.achievement_sessions_1_desc,
            threshold = 1.0, xp = SuccesXp.DECOUVERTE,
        ),
        AchievementDef(
            "sessions_10", AchievementFamily.SESSIONS, AchievementType.CUMULATIF,
            R.string.achievement_sessions_10_title, R.string.achievement_sessions_10_desc,
            threshold = 10.0, xp = SuccesXp.FACILE,
        ),
        AchievementDef(
            "sessions_50", AchievementFamily.SESSIONS, AchievementType.CUMULATIF,
            R.string.achievement_sessions_50_title, R.string.achievement_sessions_50_desc,
            threshold = 50.0, xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "sessions_streak_7", AchievementFamily.SESSIONS, AchievementType.CUMULATIF,
            R.string.achievement_sessions_streak_7_title, R.string.achievement_sessions_streak_7_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> m.longestDayStreak >= 7 },
        ),
        AchievementDef(
            "sessions_3h", AchievementFamily.SESSIONS, AchievementType.SESSION,
            R.string.achievement_sessions_3h_title, R.string.achievement_sessions_3h_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> m.maxSessionDurationMillis >= 3 * 60 * 60 * 1000L },
        ),
        AchievementDef(
            "sessions_100", AchievementFamily.SESSIONS, AchievementType.CUMULATIF,
            R.string.achievement_sessions_100_title, R.string.achievement_sessions_100_desc,
            threshold = 100.0, xp = SuccesXp.DIFFICILE,
        ),
        AchievementDef(
            "sessions_streak_30", AchievementFamily.SESSIONS, AchievementType.CUMULATIF,
            R.string.achievement_sessions_streak_30_title, R.string.achievement_sessions_streak_30_desc,
            xp = SuccesXp.TRES_DIFFICILE,
            oneShotCheck = { m -> m.longestDayStreak >= 30 },
        ),
        AchievementDef(
            "sessions_12_mois", AchievementFamily.SESSIONS, AchievementType.CUMULATIF,
            R.string.achievement_sessions_12_mois_title, R.string.achievement_sessions_12_mois_desc,
            xp = SuccesXp.TRES_DIFFICILE,
            oneShotCheck = { m -> m.distinctMonthsCount >= 12 },
        ),

        // --- Repères (5) --------------------------------------------------------------------------
        AchievementDef(
            "waypoints_1", AchievementFamily.REPERES, AchievementType.EVENEMENT,
            R.string.achievement_waypoints_1_title, R.string.achievement_waypoints_1_desc,
            threshold = 1.0, xp = SuccesXp.DECOUVERTE,
        ),
        AchievementDef(
            "waypoints_session_10", AchievementFamily.REPERES, AchievementType.SESSION,
            R.string.achievement_waypoints_session_10_title, R.string.achievement_waypoints_session_10_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> m.maxWaypointsInSession >= 10 },
        ),
        AchievementDef(
            "waypoints_arrivee", AchievementFamily.REPERES, AchievementType.INSTANT,
            R.string.achievement_waypoints_arrivee_title, R.string.achievement_waypoints_arrivee_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { false }, // STUB: needs a live compass-guided arrival check
        ),
        AchievementDef(
            "waypoints_50", AchievementFamily.REPERES, AchievementType.EVENEMENT,
            R.string.achievement_waypoints_50_title, R.string.achievement_waypoints_50_desc,
            threshold = 50.0, xp = SuccesXp.MOYEN,
        ),
        AchievementDef(
            "waypoints_icones", AchievementFamily.REPERES, AchievementType.EVENEMENT,
            R.string.achievement_waypoints_icones_title, R.string.achievement_waypoints_icones_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> m.distinctWaypointIcons.containsAll(REFERENCE_WAYPOINT_ICONS) },
        ),

        // --- Boussole (3) -------------------------------------------------------------------------
        AchievementDef(
            "compass_nord", AchievementFamily.BOUSSOLE, AchievementType.INSTANT,
            R.string.achievement_compass_nord_title, R.string.achievement_compass_nord_desc,
            xp = SuccesXp.DECOUVERTE,
            oneShotCheck = { false }, // STUB: needs a held-heading timer
        ),
        AchievementDef(
            "compass_rose", AchievementFamily.BOUSSOLE, AchievementType.CUMULATIF,
            R.string.achievement_compass_rose_title, R.string.achievement_compass_rose_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { false }, // STUB: needs per-cardinal-direction distance tracking
        ),
        AchievementDef(
            "compass_decl", AchievementFamily.BOUSSOLE, AchievementType.INSTANT,
            R.string.achievement_compass_decl_title, R.string.achievement_compass_decl_desc,
            xp = SuccesXp.DIFFICILE,
            oneShotCheck = { m -> "cmp_decl" in m.flags },
        ),

        // --- Géographie (9) -----------------------------------------------------------------------
        // Based on the lifetime lat/lng bounding box (NORTHERNMOST/SOUTHERNMOST/EASTERNMOST/
        // WESTERNMOST records), not a single fix, so "crossed" here means "has been on both sides of
        // that line at some point".
        AchievementDef(
            "geo_45th_parallel", AchievementFamily.GEO, AchievementType.INSTANT,
            R.string.achievement_geo_45th_parallel_title, R.string.achievement_geo_45th_parallel_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> (m.latitudeMax ?: Double.NEGATIVE_INFINITY) >= 45.0 },
        ),
        AchievementDef(
            "geo_greenwich", AchievementFamily.GEO, AchievementType.ETAT,
            R.string.achievement_geo_greenwich_title, R.string.achievement_geo_greenwich_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m ->
                (m.longitudeMax ?: Double.NEGATIVE_INFINITY) >= 0.0 &&
                    (m.longitudeMin ?: Double.POSITIVE_INFINITY) <= 0.0
            },
        ),
        AchievementDef(
            "geo_confluence", AchievementFamily.GEO, AchievementType.INSTANT,
            R.string.achievement_geo_confluence_title, R.string.achievement_geo_confluence_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> "geo_confluence" in m.flags },
        ),
        AchievementDef(
            "geo_tropiques", AchievementFamily.GEO, AchievementType.INSTANT,
            R.string.achievement_geo_tropiques_title, R.string.achievement_geo_tropiques_desc,
            xp = SuccesXp.DIFFICILE,
            oneShotCheck = { m -> (m.minAbsLatitude ?: Double.POSITIVE_INFINITY) <= 23.44 },
        ),
        AchievementDef(
            "geo_equator", AchievementFamily.GEO, AchievementType.ETAT,
            R.string.achievement_geo_equator_title, R.string.achievement_geo_equator_desc,
            xp = SuccesXp.TRES_DIFFICILE,
            oneShotCheck = { m ->
                (m.latitudeMax ?: Double.NEGATIVE_INFINITY) >= 0.0 &&
                    (m.latitudeMin ?: Double.POSITIVE_INFINITY) <= 0.0
            },
        ),
        AchievementDef(
            "geo_arctique", AchievementFamily.GEO, AchievementType.INSTANT,
            R.string.achievement_geo_arctique_title, R.string.achievement_geo_arctique_desc,
            xp = SuccesXp.TRES_DIFFICILE,
            oneShotCheck = { m -> (m.latitudeMax ?: Double.NEGATIVE_INFINITY) >= 66.56 },
        ),
        AchievementDef(
            "geo_45s", AchievementFamily.GEO, AchievementType.INSTANT,
            R.string.achievement_geo_45s_title, R.string.achievement_geo_45s_desc,
            xp = SuccesXp.TRES_DIFFICILE,
            oneShotCheck = { m -> (m.latitudeMin ?: Double.POSITIVE_INFINITY) <= -45.0 },
        ),
        AchievementDef(
            "geo_ligne_zero", AchievementFamily.GEO, AchievementType.INSTANT,
            R.string.achievement_geo_ligne_zero_title, R.string.achievement_geo_ligne_zero_desc,
            xp = SuccesXp.EXCEPTIONNEL,
            oneShotCheck = { m -> (m.minAbsLatitude ?: Double.POSITIVE_INFINITY) <= 0.009 },
        ),
        AchievementDef(
            "geo_antipodes", AchievementFamily.GEO, AchievementType.ETAT,
            R.string.achievement_geo_antipodes_title, R.string.achievement_geo_antipodes_desc,
            xp = SuccesXp.EXCEPTIONNEL,
            oneShotCheck = { false }, // STUB: needs a persisted 1° visited-cells grid
        ),

        // --- Temps (6) ----------------------------------------------------------------------------
        // Saving a waypoint at a notable local hour.
        AchievementDef(
            "time_night_owl", AchievementFamily.TIME, AchievementType.EVENEMENT,
            R.string.achievement_time_night_owl_title, R.string.achievement_time_night_owl_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> m.hasNightWaypoint },
        ),
        AchievementDef(
            "time_early_bird", AchievementFamily.TIME, AchievementType.EVENEMENT,
            R.string.achievement_time_early_bird_title, R.string.achievement_time_early_bird_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> m.hasDawnWaypoint },
        ),
        AchievementDef(
            "time_minuit", AchievementFamily.TIME, AchievementType.SESSION,
            R.string.achievement_time_minuit_title, R.string.achievement_time_minuit_desc,
            xp = SuccesXp.FACILE,
            oneShotCheck = { m -> m.hasMidnightSession },
        ),
        AchievementDef(
            "time_new_year", AchievementFamily.TIME, AchievementType.SESSION,
            R.string.achievement_time_new_year_title, R.string.achievement_time_new_year_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> m.hasNewYearSession },
        ),
        AchievementDef(
            "time_solstice", AchievementFamily.TIME, AchievementType.SESSION,
            R.string.achievement_time_solstice_title, R.string.achievement_time_solstice_desc,
            xp = SuccesXp.MOYEN,
            oneShotCheck = { m -> m.hasSolsticeSession },
        ),
        AchievementDef(
            "time_leap_day", AchievementFamily.TIME, AchievementType.SESSION,
            R.string.achievement_time_leap_day_title, R.string.achievement_time_leap_day_desc,
            xp = SuccesXp.TRES_DIFFICILE,
            oneShotCheck = { m -> m.hasLeapDaySession },
        ),

        // --- Maîtrise (5) -------------------------------------------------------------------------
        AchievementDef(
            "app_export", AchievementFamily.MAITRISE, AchievementType.EVENEMENT,
            R.string.achievement_app_export_title, R.string.achievement_app_export_desc,
            xp = SuccesXp.DECOUVERTE,
            oneShotCheck = { m -> "app_export" in m.flags },
        ),
        AchievementDef(
            "app_backup", AchievementFamily.MAITRISE, AchievementType.EVENEMENT,
            R.string.achievement_app_backup_title, R.string.achievement_app_backup_desc,
            xp = SuccesXp.DECOUVERTE,
            oneShotCheck = { m -> "app_backup" in m.flags },
        ),
        AchievementDef(
            "app_themes", AchievementFamily.MAITRISE, AchievementType.ETAT,
            R.string.achievement_app_themes_title, R.string.achievement_app_themes_desc,
            xp = SuccesXp.DECOUVERTE,
            oneShotCheck = { m -> "theme_light" in m.flags && "theme_dark" in m.flags },
        ),
        AchievementDef(
            "app_formats", AchievementFamily.MAITRISE, AchievementType.ETAT,
            R.string.achievement_app_formats_title, R.string.achievement_app_formats_desc,
            xp = SuccesXp.DECOUVERTE,
            oneShotCheck = { m -> "format_decimal" in m.flags && "format_dms" in m.flags },
        ),
        AchievementDef(
            "app_globe", AchievementFamily.MAITRISE, AchievementType.EVENEMENT,
            R.string.achievement_app_globe_title, R.string.achievement_app_globe_desc,
            xp = SuccesXp.DECOUVERTE,
            oneShotCheck = { false }, // STUB: needs a tracked cumulative globe-rotation angle
        ),
    )

    /**
     * Builds [GamificationMetrics] from the persisted [RecordEntry] rows, every [Session], every
     * saved [Waypoint] and every persisted [flags] key — shared by
     * [app.matthieu.cairngps.data.GamificationManager] (to evaluate unlocks) and the Succès/Records
     * ViewModels (to render current progress), so the two never drift apart.
     */
    fun metricsFrom(
        records: List<RecordEntry>,
        sessions: List<Session>,
        waypoints: List<Waypoint>,
        flags: Set<String> = emptySet(),
    ): GamificationMetrics {
        fun valueOf(type: RecordType): Double? = records.firstOrNull { it.type == type.name }?.value
        fun localHourOf(timestamp: Long): Int =
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).hour
        fun localDateOf(timestamp: Long): LocalDate =
            Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

        // Longest run of consecutive calendar days across every session's start date.
        fun longestDayStreak(): Int {
            val days = sessions.map { localDateOf(it.startTimestamp) }.distinct().sorted()
            if (days.isEmpty()) return 0
            var longest = 1
            var current = 1
            for (i in 1 until days.size) {
                current = if (days[i] == days[i - 1].plusDays(1)) current + 1 else 1
                longest = maxOf(longest, current)
            }
            return longest
        }

        val waypointsBySession = waypoints.filter { it.sessionId != null }.groupBy { it.sessionId }

        return GamificationMetrics(
            maxAltitude = valueOf(RecordType.MAX_ALTITUDE),
            minAltitude = valueOf(RecordType.MIN_ALTITUDE),
            maxSpeedMs = valueOf(RecordType.MAX_SPEED)?.toFloat(),
            maxSatellites = valueOf(RecordType.MAX_SATELLITES)?.toInt(),
            maxConstellations = valueOf(RecordType.MAX_CONSTELLATIONS)?.toInt(),
            maxSatelliteElevationDegrees = valueOf(RecordType.MAX_SATELLITE_ELEVATION)?.toFloat(),
            minHorizontalAccuracy = valueOf(RecordType.MIN_HORIZONTAL_ACCURACY)?.toFloat(),
            minAbsLatitude = valueOf(RecordType.MIN_ABS_LATITUDE),
            cumulativeDistanceMeters = sessions.sumOf { it.distanceMeters },
            maxSessionDistanceMeters = sessions.maxOfOrNull { it.distanceMeters } ?: 0.0,
            cumulativeElevationGain = sessions.sumOf { it.elevationGain },
            maxSessionElevationGain = sessions.maxOfOrNull { it.elevationGain } ?: 0.0,
            maxSessionDurationMillis = sessions.maxOfOrNull { it.durationMillis } ?: 0L,
            hasCruiseSession = sessions.any {
                it.averageSpeed >= (5.0 / 3.6).toFloat() && it.durationMillis >= 3_600_000L
            },
            sessionCount = sessions.size,
            longestDayStreak = longestDayStreak(),
            distinctMonthsCount = sessions.map { localDateOf(it.startTimestamp).monthValue }.distinct().size,
            hasMidnightSession = sessions.any {
                localDateOf(it.startTimestamp) != localDateOf(it.endTimestamp)
            },
            hasNewYearSession = sessions.any {
                val d = localDateOf(it.startTimestamp)
                d.monthValue == 1 && d.dayOfMonth == 1
            },
            hasSolsticeSession = sessions.any {
                val d = localDateOf(it.startTimestamp)
                d.monthValue == 6 && d.dayOfMonth == 21
            },
            hasLeapDaySession = sessions.any {
                val d = localDateOf(it.startTimestamp)
                d.monthValue == 2 && d.dayOfMonth == 29
            },
            latitudeMax = valueOf(RecordType.NORTHERNMOST),
            latitudeMin = valueOf(RecordType.SOUTHERNMOST),
            longitudeMax = valueOf(RecordType.EASTERNMOST),
            longitudeMin = valueOf(RecordType.WESTERNMOST),
            hasNightWaypoint = waypoints.any { localHourOf(it.timestamp) >= 22 },
            hasDawnWaypoint = waypoints.any { localHourOf(it.timestamp) < 6 },
            waypointCount = waypoints.size,
            maxWaypointsInSession = waypointsBySession.maxOfOrNull { it.value.size } ?: 0,
            distinctWaypointIcons = waypoints.map { it.icon }.toSet(),
            flags = flags,
        )
    }

    /**
     * The current value of [family]'s metric, or `null` if [family] has no shared scalar metric
     * ([AchievementFamily.GEO]/[AchievementFamily.TIME]/[AchievementFamily.BOUSSOLE]/
     * [AchievementFamily.MAITRISE] — every achievement in those families is a one-shot condition).
     */
    fun metricFor(family: AchievementFamily, metrics: GamificationMetrics): Double? = when (family) {
        AchievementFamily.ALTITUDE -> metrics.maxAltitude
        AchievementFamily.SPEED -> metrics.maxSpeedMs?.toDouble()
        AchievementFamily.SATELLITES -> metrics.maxSatellites?.toDouble()
        AchievementFamily.DISTANCE -> metrics.cumulativeDistanceMeters
        AchievementFamily.SESSIONS -> metrics.sessionCount.toDouble()
        AchievementFamily.REPERES -> metrics.waypointCount.toDouble()
        AchievementFamily.GEO -> null
        AchievementFamily.TIME -> null
        AchievementFamily.BOUSSOLE -> null
        AchievementFamily.MAITRISE -> null
    }

    /** Whether [def]'s condition is currently satisfied by [metrics]. */
    fun isUnlocked(def: AchievementDef, metrics: GamificationMetrics): Boolean {
        val oneShotCheck = def.oneShotCheck
        if (oneShotCheck != null) return oneShotCheck(metrics)
        val value = metricFor(def.family, metrics) ?: return false
        return value >= def.threshold
    }

    /**
     * Progress towards [family]'s next locked palier, for the progress bar on the Succès screen.
     * Only threshold-based achievements (`oneShotCheck == null`) count as paliers; a family made
     * entirely of one-shot achievements naturally has none, so this returns `null` for it without
     * needing to special-case which families those are.
     */
    fun progressToNext(family: AchievementFamily, metrics: GamificationMetrics): FamilyProgress? {
        val paliers = ALL.filter { it.family == family && it.oneShotCheck == null }.sortedBy { it.threshold }
        if (paliers.isEmpty()) return null

        val value = metricFor(family, metrics) ?: 0.0
        val next = paliers.firstOrNull { value < it.threshold }
        val previousThreshold = paliers.lastOrNull { value >= it.threshold }?.threshold ?: 0.0
        val fraction = if (next == null) {
            1f
        } else {
            val span = next.threshold - previousThreshold
            if (span <= 0.0) 1f else ((value - previousThreshold) / span).toFloat().coerceIn(0f, 1f)
        }
        return FamilyProgress(
            currentThreshold = previousThreshold,
            nextThreshold = next?.threshold,
            currentValue = value,
            fraction = fraction,
        )
    }

    /** Sum of [AchievementDef.xp]'s points for every unlocked id, i.e. the user's lifetime XP total. */
    fun xpFor(unlockedIds: Set<String>): Int = ALL.filter { it.id in unlockedIds }.sumOf { it.xp.points }

    /**
     * The locked achievement closest to unlocking, across every family with a scalar progress bar
     * (see [progressToNext]), for the Succès screen's highlight card. Ties are broken by catalog
     * order. Returns `null` once every such family is fully unlocked.
     */
    fun nextAchievement(metrics: GamificationMetrics): NextAchievement? {
        val candidates = AchievementFamily.entries.mapNotNull { family ->
            val progress = progressToNext(family, metrics) ?: return@mapNotNull null
            val nextThreshold = progress.nextThreshold ?: return@mapNotNull null
            val def = ALL.firstOrNull { it.family == family && it.threshold == nextThreshold }
                ?: return@mapNotNull null
            def to progress
        }
        val (def, progress) = candidates.maxByOrNull { it.second.fraction } ?: return null
        return NextAchievement(def, progress)
    }
}
