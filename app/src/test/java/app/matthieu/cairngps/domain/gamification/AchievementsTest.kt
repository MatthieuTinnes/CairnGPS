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

    // --- progressToNext ----------------------------------------------------------------------------

    @Test
    fun `progressToNext is null for GEO and TIME families`() {
        assertNull(Achievements.progressToNext(AchievementFamily.GEO, GamificationMetrics()))
        assertNull(Achievements.progressToNext(AchievementFamily.TIME, GamificationMetrics()))
    }

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
            GamificationMetrics(maxAltitude = 6000.0),
        )
        requireNotNull(progress)
        assertEquals(5825.0, progress.currentThreshold, 0.0)
        assertNull(progress.nextThreshold)
        assertEquals(1f, progress.fraction, 0f)
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
        assertEquals(35, Achievements.xpFor(setOf("altitude_1000", "speed_30")))
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
            maxAltitude = 5825.0,
            maxSpeedMs = (800.0 / 3.6).toFloat(),
            maxSatellites = 20,
            cumulativeDistanceMeters = 100_000.0,
            sessionCount = 50,
        )
        assertNull(Achievements.nextAchievement(metrics))
    }
}
