package app.matthieu.cairngps.domain.gamification

import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordType
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.Waypoint
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    private fun recordOf(type: RecordType, value: Double) =
        RecordEntry(type = type.name, value = value, achievedAt = 0L)

    private fun sessionOf(distanceMeters: Double) = Session(
        name = "s",
        startTimestamp = 0L,
        endTimestamp = 0L,
        distanceMeters = distanceMeters,
        averageSpeed = 0f,
        maxSpeed = 0f,
        elevationGain = 0.0,
        elevationLoss = 0.0,
        minAltitude = 0.0,
        maxAltitude = 0.0,
        latitudeMax = 0.0,
        latitudeMin = 0.0,
        longitudeMax = 0.0,
        longitudeMin = 0.0,
    )

    private fun waypointAtLocalTime(hour: Int, minute: Int): Waypoint {
        val timestamp = ZonedDateTime.of(
            LocalDate.of(2024, 6, 1),
            LocalTime.of(hour, minute),
            ZoneId.systemDefault(),
        ).toInstant().toEpochMilli()
        return Waypoint(
            name = "w",
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            speed = 0f,
            horizontalAccuracy = 0f,
            satellitesUsedInFix = null,
            timestamp = timestamp,
        )
    }

    private fun defOf(id: String) = Achievements.ALL.first { it.id == id }

    private fun timestampAt(date: LocalDate, hour: Int = 12, minute: Int = 0): Long =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun sessionOnDay(date: LocalDate) = Session(
        name = "s",
        startTimestamp = timestampAt(date),
        endTimestamp = timestampAt(date),
        distanceMeters = 0.0,
        averageSpeed = 0f,
        maxSpeed = 0f,
        elevationGain = 0.0,
        elevationLoss = 0.0,
        minAltitude = 0.0,
        maxAltitude = 0.0,
        latitudeMax = 0.0,
        latitudeMin = 0.0,
        longitudeMax = 0.0,
        longitudeMin = 0.0,
    )

    // --- metricsFrom ---------------------------------------------------------------------------

    @Test
    fun `metricsFrom on empty inputs never falsely unlocks`() {
        val metrics = Achievements.metricsFrom(emptyList(), emptyList(), emptyList())
        assertEquals(GamificationMetrics(), metrics)
    }

    @Test
    fun `metricsFrom maps record rows to their metric fields`() {
        val records = listOf(
            recordOf(RecordType.MAX_ALTITUDE, 4805.0),
            recordOf(RecordType.MAX_SPEED, 27.5),
            recordOf(RecordType.MAX_SATELLITES, 14.0),
            recordOf(RecordType.NORTHERNMOST, 48.85),
            recordOf(RecordType.SOUTHERNMOST, 43.3),
            recordOf(RecordType.EASTERNMOST, 7.7),
            recordOf(RecordType.WESTERNMOST, -1.5),
        )
        val metrics = Achievements.metricsFrom(records, emptyList(), emptyList())
        assertEquals(4805.0, metrics.maxAltitude)
        assertEquals(27.5f, metrics.maxSpeedMs)
        assertEquals(14, metrics.maxSatellites)
        assertEquals(48.85, metrics.latitudeMax)
        assertEquals(43.3, metrics.latitudeMin)
        assertEquals(7.7, metrics.longitudeMax)
        assertEquals(-1.5, metrics.longitudeMin)
    }

    @Test
    fun `metricsFrom sums session distances into cumulativeDistanceMeters`() {
        val sessions = listOf(sessionOf(1000.0), sessionOf(2500.0))
        val metrics = Achievements.metricsFrom(emptyList(), sessions, emptyList())
        assertEquals(3500.0, metrics.cumulativeDistanceMeters, 0.0)
        assertEquals(2, metrics.sessionCount)
    }

    @Test
    fun `metricsFrom flags a night waypoint at local hour 22 but not 21-59`() {
        val night = Achievements.metricsFrom(emptyList(), emptyList(), listOf(waypointAtLocalTime(22, 0)))
        assertTrue(night.hasNightWaypoint)

        val justBefore = Achievements.metricsFrom(emptyList(), emptyList(), listOf(waypointAtLocalTime(21, 59)))
        assertFalse(justBefore.hasNightWaypoint)
    }

    @Test
    fun `metricsFrom flags a dawn waypoint at local hour 05-59 but not 06-00`() {
        val dawn = Achievements.metricsFrom(emptyList(), emptyList(), listOf(waypointAtLocalTime(5, 59)))
        assertTrue(dawn.hasDawnWaypoint)

        val justAfter = Achievements.metricsFrom(emptyList(), emptyList(), listOf(waypointAtLocalTime(6, 0)))
        assertFalse(justAfter.hasDawnWaypoint)
    }

    @Test
    fun `metricsFrom computes the longest run of consecutive calendar days`() {
        val sessions = listOf(
            sessionOnDay(LocalDate.of(2024, 6, 1)),
            sessionOnDay(LocalDate.of(2024, 6, 2)),
            sessionOnDay(LocalDate.of(2024, 6, 3)),
            sessionOnDay(LocalDate.of(2024, 6, 10)), // breaks the streak
        )
        val metrics = Achievements.metricsFrom(emptyList(), sessions, emptyList())
        assertEquals(3, metrics.longestDayStreak)
    }

    @Test
    fun `metricsFrom counts distinct calendar months across sessions`() {
        val sessions = listOf(
            sessionOnDay(LocalDate.of(2024, 1, 5)),
            sessionOnDay(LocalDate.of(2024, 1, 20)), // same month as above
            sessionOnDay(LocalDate.of(2024, 6, 1)),
        )
        val metrics = Achievements.metricsFrom(emptyList(), sessions, emptyList())
        assertEquals(2, metrics.distinctMonthsCount)
    }

    @Test
    fun `metricsFrom flags a session whose start and end local dates differ`() {
        val session = Session(
            name = "s",
            startTimestamp = timestampAt(LocalDate.of(2024, 6, 1), hour = 23, minute = 30),
            endTimestamp = timestampAt(LocalDate.of(2024, 6, 2), hour = 0, minute = 30),
            distanceMeters = 0.0, averageSpeed = 0f, maxSpeed = 0f,
            elevationGain = 0.0, elevationLoss = 0.0, minAltitude = 0.0, maxAltitude = 0.0,
            latitudeMax = 0.0, latitudeMin = 0.0, longitudeMax = 0.0, longitudeMin = 0.0,
        )
        val metrics = Achievements.metricsFrom(emptyList(), listOf(session), emptyList())
        assertTrue(metrics.hasMidnightSession)
    }

    // --- isUnlocked ------------------------------------------------------------------------------

    @Test
    fun `isUnlocked when the metric exactly equals the threshold`() {
        val def = defOf("altitude_1000")
        assertTrue(Achievements.isUnlocked(def, GamificationMetrics(maxAltitude = 1000.0)))
    }

    @Test
    fun `isUnlocked is false when the metric is below the threshold`() {
        val def = defOf("altitude_1000")
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics(maxAltitude = 999.999)))
    }

    @Test
    fun `isUnlocked is false when the metric is null`() {
        val def = defOf("altitude_1000")
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics(maxAltitude = null)))
    }

    @Test
    fun `isUnlocked geo_45th_parallel requires latitudeMax of at least 45`() {
        val def = defOf("geo_45th_parallel")
        assertTrue(Achievements.isUnlocked(def, GamificationMetrics(latitudeMax = 45.0)))
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics(latitudeMax = 44.999)))
    }

    @Test
    fun `isUnlocked geo_equator requires having been on both hemispheres`() {
        val def = defOf("geo_equator")
        assertTrue(Achievements.isUnlocked(def, GamificationMetrics(latitudeMax = 10.0, latitudeMin = -5.0)))
        // Only ever seen the northern hemisphere: stays locked even though latitudeMax >= 0.
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics(latitudeMax = 10.0, latitudeMin = 5.0)))
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics()))
    }

    @Test
    fun `isUnlocked geo_greenwich requires having been on both sides of the meridian`() {
        val def = defOf("geo_greenwich")
        assertTrue(Achievements.isUnlocked(def, GamificationMetrics(longitudeMax = 10.0, longitudeMin = -5.0)))
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics(longitudeMax = 10.0, longitudeMin = 5.0)))
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics()))
    }

    @Test
    fun `isUnlocked waypoints_icones requires every reference icon to have been used`() {
        val def = defOf("waypoints_icones")
        val everyIcon = setOf(
            "flag", "terrain", "forest", "water_drop", "park", "hiking", "cottage", "cabin",
            "restaurant", "photo_camera", "local_parking", "warning", "star", "pin_drop", "sailing", "waves",
        )
        assertTrue(Achievements.isUnlocked(def, GamificationMetrics(distinctWaypointIcons = everyIcon)))
        assertFalse(Achievements.isUnlocked(def, GamificationMetrics(distinctWaypointIcons = everyIcon - "flag")))
    }

    @Test
    fun `isUnlocked reads persisted ETAT-EVENEMENT flags`() {
        assertTrue(Achievements.isUnlocked(defOf("app_export"), GamificationMetrics(flags = setOf("app_export"))))
        assertFalse(Achievements.isUnlocked(defOf("app_export"), GamificationMetrics()))

        assertTrue(
            Achievements.isUnlocked(
                defOf("app_themes"),
                GamificationMetrics(flags = setOf("theme_light", "theme_dark")),
            ),
        )
        assertFalse(Achievements.isUnlocked(defOf("app_themes"), GamificationMetrics(flags = setOf("theme_light"))))
    }

    @Test
    fun `deferred stub achievements never unlock regardless of how extreme the metrics are`() {
        val stubIds = setOf(
            "speed_still", "waypoints_arrivee", "compass_nord", "compass_rose", "geo_antipodes", "app_globe",
        )
        val extremeMetrics = GamificationMetrics(
            maxAltitude = 100_000.0,
            maxSpeedMs = 100_000f,
            maxSatellites = 1000,
            cumulativeDistanceMeters = 1e9,
            sessionCount = 100_000,
            waypointCount = 100_000,
            longestDayStreak = 10_000,
            distinctMonthsCount = 12,
            flags = setOf(
                "theme_light", "theme_dark", "format_decimal", "format_dms",
                "app_export", "app_backup", "cmp_decl", "geo_confluence",
            ),
        )
        for (id in stubIds) {
            assertFalse(id, Achievements.isUnlocked(defOf(id), extremeMetrics))
        }
    }

    @Test
    fun `catalog has 72 unique achievements`() {
        assertEquals(72, Achievements.ALL.size)
        assertEquals(72, Achievements.ALL.map { it.id }.toSet().size)
    }

    // --- progressToNext ----------------------------------------------------------------------------

    @Test
    fun `progressToNext computes the fraction between the surrounding paliers`() {
        val progress = Achievements.progressToNext(
            AchievementFamily.ALTITUDE,
            GamificationMetrics(maxAltitude = 1213.0),
        )
        requireNotNull(progress)
        assertEquals(1000.0, progress.currentThreshold, 0.0)
        assertEquals(1426.0, progress.nextThreshold)
        assertEquals(0.5f, progress.fraction, 1e-6f)
    }

    @Test
    fun `progressToNext below the first palier starts from a zero threshold`() {
        val progress = Achievements.progressToNext(
            AchievementFamily.ALTITUDE,
            GamificationMetrics(maxAltitude = 500.0),
        )
        requireNotNull(progress)
        assertEquals(0.0, progress.currentThreshold, 0.0)
        assertEquals(1000.0, progress.nextThreshold)
        assertEquals(0.5f, progress.fraction, 1e-6f)
    }

    @Test
    fun `progressToNext past the last palier has no next threshold and full fraction`() {
        val progress = Achievements.progressToNext(
            AchievementFamily.ALTITUDE,
            GamificationMetrics(maxAltitude = 9000.0),
        )
        requireNotNull(progress)
        assertEquals(8849.0, progress.currentThreshold, 0.0)
        assertNull(progress.nextThreshold)
        assertEquals(1f, progress.fraction, 0f)
    }

    @Test
    fun `progressToNext ignores one-shot achievements mixed into a scalar family`() {
        // altitude_neg/dplus_* are oneShotCheck-based ALTITUDE entries; they must never appear as
        // paliers alongside the threshold-based ones (altitude_1000, altitude_puy_de_dome...).
        val progress = Achievements.progressToNext(
            AchievementFamily.ALTITUDE,
            GamificationMetrics(maxAltitude = 500.0),
        )
        requireNotNull(progress)
        assertEquals(1000.0, progress.nextThreshold)
    }

    @Test
    fun `progressToNext is null for a family made entirely of one-shot achievements`() {
        assertNull(Achievements.progressToNext(AchievementFamily.GEO, GamificationMetrics()))
        assertNull(Achievements.progressToNext(AchievementFamily.TIME, GamificationMetrics()))
        assertNull(Achievements.progressToNext(AchievementFamily.BOUSSOLE, GamificationMetrics()))
        assertNull(Achievements.progressToNext(AchievementFamily.MAITRISE, GamificationMetrics()))
    }

    @Test
    fun `progressToNext treats a null metric as zero`() {
        val progress = Achievements.progressToNext(
            AchievementFamily.ALTITUDE,
            GamificationMetrics(maxAltitude = null),
        )
        requireNotNull(progress)
        assertEquals(0.0, progress.currentValue, 0.0)
        assertEquals(0f, progress.fraction, 0f)
    }

    // --- xpFor ---------------------------------------------------------------------------------

    @Test
    fun `xpFor an empty set is zero`() {
        assertEquals(0, Achievements.xpFor(emptySet()))
    }

    @Test
    fun `xpFor sums the xp of known unlocked ids`() {
        // altitude_1000 is FACILE (20), speed_30 is DECOUVERTE (10).
        assertEquals(30, Achievements.xpFor(setOf("altitude_1000", "speed_30")))
    }

    @Test
    fun `xpFor ignores unknown ids`() {
        assertEquals(20, Achievements.xpFor(setOf("altitude_1000", "not_a_real_id")))
    }

    // --- nextAchievement ----------------------------------------------------------------------

    @Test
    fun `nextAchievement picks the family with the highest fraction`() {
        // 29 km-h out of the 30 km-h palier: far closer than any other family at zero.
        val metrics = GamificationMetrics(maxSpeedMs = (29.0 / 3.6).toFloat())
        val next = Achievements.nextAchievement(metrics)
        requireNotNull(next)
        assertEquals("speed_30", next.def.id)
    }

    @Test
    fun `nextAchievement breaks ties by catalog order`() {
        // Every scalar family sits at zero progress: ALTITUDE is first in AchievementFamily.entries.
        val next = Achievements.nextAchievement(GamificationMetrics())
        requireNotNull(next)
        assertEquals("altitude_1000", next.def.id)
    }

    @Test
    fun `nextAchievement is null once every scalar family is fully unlocked`() {
        val metrics = GamificationMetrics(
            maxAltitude = 8849.0,
            maxSpeedMs = (800.0 / 3.6).toFloat(),
            maxSatellites = 30,
            cumulativeDistanceMeters = 40_075_000.0,
            sessionCount = 100,
            waypointCount = 50,
        )
        assertNull(Achievements.nextAchievement(metrics))
    }
}
