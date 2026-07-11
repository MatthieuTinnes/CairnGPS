package app.matthieu.cairngps.ui.gamification

import androidx.annotation.StringRes
import app.matthieu.cairngps.R
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordType
import app.matthieu.cairngps.data.Session

/**
 * A family groups achievements that share the same underlying metric (and, in the UI, the same
 * progression bar towards the next palier). [GEO] is the exception: crossing a notable
 * parallel/meridian is a one-shot condition, not a scalar threshold, so it has no progress bar.
 */
enum class AchievementFamily {
    ALTITUDE,
    SPEED,
    SATELLITES,
    DISTANCE,
    SESSIONS,
    GEO,
}

/**
 * One unlockable achievement. [threshold] is compared against [GamificationMetrics.valueFor] for
 * [family] (see [Achievements.metricFor]) and is expressed in the metric's own unit: meters for
 * [AchievementFamily.ALTITUDE]/[AchievementFamily.DISTANCE], m/s for [AchievementFamily.SPEED],
 * a satellite count for [AchievementFamily.SATELLITES], a session count for
 * [AchievementFamily.SESSIONS]. [AchievementFamily.GEO] achievements ignore [threshold] entirely
 * and are unlocked via [geoCheck] instead, since "crossed the equator" isn't a ">=" comparison.
 */
data class AchievementDef(
    val id: String,
    val family: AchievementFamily,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val threshold: Double = 0.0,
    val geoCheck: ((GamificationMetrics) -> Boolean)? = null,
)

/**
 * Live+historical aggregates the achievement catalog and the records page are evaluated against.
 * Built by [app.matthieu.cairngps.data.GamificationManager] from stored [app.matthieu.cairngps.data.RecordEntry]
 * rows and every [app.matthieu.cairngps.data.Session]. A `null` field means "no data yet" —
 * distinct from zero, so an achievement never falsely unlocks before any fix has been seen.
 */
data class GamificationMetrics(
    val maxAltitude: Double? = null, // meters
    val maxSpeedMs: Float? = null, // m/s
    val maxSatellites: Int? = null,
    val cumulativeDistanceMeters: Double = 0.0,
    val sessionCount: Int = 0,
    val latitudeMax: Double? = null,
    val latitudeMin: Double? = null,
    val longitudeMax: Double? = null,
    val longitudeMin: Double? = null,
)

/** Progress of one [AchievementFamily] towards its next locked palier, for the progress bar. */
data class FamilyProgress(
    val currentThreshold: Double,
    val nextThreshold: Double?, // null once every palier in the family is unlocked
    val currentValue: Double,
    val fraction: Float, // 0f..1f towards nextThreshold; 1f when nextThreshold is null
)

/**
 * The catalog of unlockable achievements — paliers by altitude, speed, simultaneous satellites,
 * cumulative distance, session count, and notable geographic crossings. Deliberately plain data
 * (no Room annotations): only the *unlocked* state is persisted (`AchievementState`), so adding a
 * new palier here never requires a database migration.
 */
object Achievements {

    /** Speed paliers are authored in km/h but compared against [GamificationMetrics.maxSpeedMs]. */
    private fun kmhToMs(kmh: Double) = kmh / 3.6

    /** Distance paliers are authored in km but compared against meters. */
    private fun kmToMeters(km: Double) = km * 1000.0

    val ALL: List<AchievementDef> = listOf(
        // Altitude franchie
        AchievementDef(
            "altitude_1000", AchievementFamily.ALTITUDE,
            R.string.achievement_altitude_1000_title, R.string.achievement_altitude_1000_desc,
            threshold = 1000.0,
        ),
        AchievementDef(
            "altitude_2000", AchievementFamily.ALTITUDE,
            R.string.achievement_altitude_2000_title, R.string.achievement_altitude_2000_desc,
            threshold = 2000.0,
        ),
        AchievementDef(
            "altitude_3000", AchievementFamily.ALTITUDE,
            R.string.achievement_altitude_3000_title, R.string.achievement_altitude_3000_desc,
            threshold = 3000.0,
        ),

        // Vitesse atteinte
        AchievementDef(
            "speed_30", AchievementFamily.SPEED,
            R.string.achievement_speed_30_title, R.string.achievement_speed_30_desc,
            threshold = kmhToMs(30.0),
        ),
        AchievementDef(
            "speed_50", AchievementFamily.SPEED,
            R.string.achievement_speed_50_title, R.string.achievement_speed_50_desc,
            threshold = kmhToMs(50.0),
        ),
        AchievementDef(
            "speed_100", AchievementFamily.SPEED,
            R.string.achievement_speed_100_title, R.string.achievement_speed_100_desc,
            threshold = kmhToMs(100.0),
        ),

        // Nombre de satellites fixés simultanément
        AchievementDef(
            "satellites_8", AchievementFamily.SATELLITES,
            R.string.achievement_satellites_8_title, R.string.achievement_satellites_8_desc,
            threshold = 8.0,
        ),
        AchievementDef(
            "satellites_12", AchievementFamily.SATELLITES,
            R.string.achievement_satellites_12_title, R.string.achievement_satellites_12_desc,
            threshold = 12.0,
        ),
        AchievementDef(
            "satellites_20", AchievementFamily.SATELLITES,
            R.string.achievement_satellites_20_title, R.string.achievement_satellites_20_desc,
            threshold = 20.0,
        ),

        // Distance cumulée
        AchievementDef(
            "distance_10", AchievementFamily.DISTANCE,
            R.string.achievement_distance_10_title, R.string.achievement_distance_10_desc,
            threshold = kmToMeters(10.0),
        ),
        AchievementDef(
            "distance_50", AchievementFamily.DISTANCE,
            R.string.achievement_distance_50_title, R.string.achievement_distance_50_desc,
            threshold = kmToMeters(50.0),
        ),
        AchievementDef(
            "distance_100", AchievementFamily.DISTANCE,
            R.string.achievement_distance_100_title, R.string.achievement_distance_100_desc,
            threshold = kmToMeters(100.0),
        ),

        // Nombre de sessions
        AchievementDef(
            "sessions_1", AchievementFamily.SESSIONS,
            R.string.achievement_sessions_1_title, R.string.achievement_sessions_1_desc,
            threshold = 1.0,
        ),
        AchievementDef(
            "sessions_10", AchievementFamily.SESSIONS,
            R.string.achievement_sessions_10_title, R.string.achievement_sessions_10_desc,
            threshold = 10.0,
        ),
        AchievementDef(
            "sessions_50", AchievementFamily.SESSIONS,
            R.string.achievement_sessions_50_title, R.string.achievement_sessions_50_desc,
            threshold = 50.0,
        ),

        // Géographiques : franchir un parallèle/méridien notable. Based on the lifetime lat/lng
        // bounding box (NORTHERNMOST/SOUTHERNMOST/EASTERNMOST/WESTERNMOST records), not a single
        // fix, so "crossed" here means "has been on both sides of that line at some point".
        AchievementDef(
            "geo_45th_parallel", AchievementFamily.GEO,
            R.string.achievement_geo_45th_parallel_title, R.string.achievement_geo_45th_parallel_desc,
            geoCheck = { m -> (m.latitudeMax ?: Double.NEGATIVE_INFINITY) >= 45.0 },
        ),
        AchievementDef(
            "geo_equator", AchievementFamily.GEO,
            R.string.achievement_geo_equator_title, R.string.achievement_geo_equator_desc,
            geoCheck = { m ->
                (m.latitudeMax ?: Double.NEGATIVE_INFINITY) >= 0.0 &&
                    (m.latitudeMin ?: Double.POSITIVE_INFINITY) <= 0.0
            },
        ),
        AchievementDef(
            "geo_greenwich", AchievementFamily.GEO,
            R.string.achievement_geo_greenwich_title, R.string.achievement_geo_greenwich_desc,
            geoCheck = { m ->
                (m.longitudeMax ?: Double.NEGATIVE_INFINITY) >= 0.0 &&
                    (m.longitudeMin ?: Double.POSITIVE_INFINITY) <= 0.0
            },
        ),
    )

    /**
     * Builds [GamificationMetrics] from the persisted [RecordEntry] rows and every [Session] —
     * shared by [app.matthieu.cairngps.data.GamificationManager] (to evaluate unlocks) and the
     * Succès/Records ViewModels (to render current progress), so the two never drift apart.
     */
    fun metricsFrom(records: List<RecordEntry>, sessions: List<Session>): GamificationMetrics {
        fun valueOf(type: RecordType): Double? = records.firstOrNull { it.type == type.name }?.value
        return GamificationMetrics(
            maxAltitude = valueOf(RecordType.MAX_ALTITUDE),
            maxSpeedMs = valueOf(RecordType.MAX_SPEED)?.toFloat(),
            maxSatellites = valueOf(RecordType.MAX_SATELLITES)?.toInt(),
            cumulativeDistanceMeters = sessions.sumOf { it.distanceMeters },
            sessionCount = sessions.size,
            latitudeMax = valueOf(RecordType.NORTHERNMOST),
            latitudeMin = valueOf(RecordType.SOUTHERNMOST),
            longitudeMax = valueOf(RecordType.EASTERNMOST),
            longitudeMin = valueOf(RecordType.WESTERNMOST),
        )
    }

    /** The current value of [family]'s metric, or `null` if [family] is [AchievementFamily.GEO]. */
    fun metricFor(family: AchievementFamily, metrics: GamificationMetrics): Double? = when (family) {
        AchievementFamily.ALTITUDE -> metrics.maxAltitude
        AchievementFamily.SPEED -> metrics.maxSpeedMs?.toDouble()
        AchievementFamily.SATELLITES -> metrics.maxSatellites?.toDouble()
        AchievementFamily.DISTANCE -> metrics.cumulativeDistanceMeters
        AchievementFamily.SESSIONS -> metrics.sessionCount.toDouble()
        AchievementFamily.GEO -> null
    }

    /** Whether [def]'s condition is currently satisfied by [metrics]. */
    fun isUnlocked(def: AchievementDef, metrics: GamificationMetrics): Boolean {
        val geoCheck = def.geoCheck
        if (geoCheck != null) return geoCheck(metrics)
        val value = metricFor(def.family, metrics) ?: return false
        return value >= def.threshold
    }

    /**
     * Progress towards [family]'s next locked palier, for the progress bar on the Succès screen.
     * Returns `null` for [AchievementFamily.GEO] (no scalar metric to show progress against —
     * each geographic achievement is either unlocked or not).
     */
    fun progressToNext(family: AchievementFamily, metrics: GamificationMetrics): FamilyProgress? {
        if (family == AchievementFamily.GEO) return null
        val paliers = ALL.filter { it.family == family }.sortedBy { it.threshold }
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
}
