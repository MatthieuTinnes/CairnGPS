package app.matthieu.cairngps.data

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.matthieu.cairngps.testutil.FakeAchievementDao
import app.matthieu.cairngps.testutil.FakeGamificationFlagDao
import app.matthieu.cairngps.testutil.FakeRecordDao
import app.matthieu.cairngps.testutil.FakeRecordingCheckpointDao
import app.matthieu.cairngps.testutil.FakeSessionDao
import app.matthieu.cairngps.testutil.FakeTrackPointDao
import app.matthieu.cairngps.testutil.FakeWaypointDao
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Uses the real [android.location.Location.distanceBetween] and [android.hardware.GeomagneticField]
 * (both called from [GamificationManager.submitLiveFix]), which need Robolectric — same reason
 * [RecordingRepositoryTest] pulls it in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GamificationManagerTest {

    private val t0 = 1_700_000_000_000L

    private fun fix(
        latitude: Double = 45.9,
        longitude: Double = 6.87,
        altitude: Double = 500.0,
        speed: Float = 1f,
        accuracy: Float = 5f,
        timestamp: Long = t0,
    ) = LocationData(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = speed,
        horizontalAccuracy = accuracy,
        verticalAccuracy = null,
        timestamp = timestamp,
    )

    /** Mirrors [RecordingRepositoryTest]'s harness: everything in-memory, one injectable dispatcher. */
    private class Harness(scheduler: TestCoroutineScheduler) {
        val fixes = MutableSharedFlow<LocationData>(extraBufferCapacity = 16)
        val locationRepository: LocationRepository = mockk {
            every { locationUpdates() } returns fixes
            every { satelliteUpdates() } returns MutableSharedFlow()
        }
        val recordDao = FakeRecordDao()
        val achievementDao = FakeAchievementDao()
        val flagDao = FakeGamificationFlagDao()
        val trackPointDao = FakeTrackPointDao()
        val sessionDao = FakeSessionDao(trackPointDao)
        val checkpointDao = FakeRecordingCheckpointDao()
        val sessionRepository = SessionRepository(sessionDao, trackPointDao, checkpointDao)
        val waypointRepository = WaypointRepository(FakeWaypointDao())
        val recordsRepository = RecordsRepository(recordDao)
        val achievementsRepository = AchievementsRepository(achievementDao)
        val flagsRepository = GamificationFlagsRepository(flagDao)

        val manager = GamificationManager(
            context = ApplicationProvider.getApplicationContext<Application>().also { app ->
                Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            locationRepository = locationRepository,
            sessionRepository = sessionRepository,
            waypointRepository = waypointRepository,
            recordsRepository = recordsRepository,
            achievementsRepository = achievementsRepository,
            gamificationFlagsRepository = flagsRepository,
            dispatcher = StandardTestDispatcher(scheduler),
        )
    }

    private suspend fun isAltitudeNegUnlocked(h: Harness) =
        h.achievementDao.getAll().any { it.id == "altitude_neg" }

    @Test
    fun `noisy live fix with negative altitude does not set the record or unlock altitude_neg`() = runTest {
        val h = Harness(testScheduler)
        h.manager.startLiveTracking()
        advanceUntilIdle()

        h.fixes.emit(fix(altitude = -5.0, accuracy = 30f))
        advanceUntilIdle()

        assertNull(h.recordDao.getByType(RecordType.MIN_ALTITUDE.name))
        assertFalse(isAltitudeNegUnlocked(h))
    }

    @Test
    fun `accurate live fix with negative altitude sets the record and unlocks altitude_neg`() = runTest {
        val h = Harness(testScheduler)
        h.manager.startLiveTracking()
        advanceUntilIdle()

        h.fixes.emit(fix(altitude = -5.0, accuracy = 5f))
        advanceUntilIdle()

        assertEqualsDouble(-5.0, h.recordDao.getByType(RecordType.MIN_ALTITUDE.name)?.value)
        assertTrue(isAltitudeNegUnlocked(h))
    }

    private fun assertEqualsDouble(expected: Double, actual: Double?) {
        org.junit.Assert.assertEquals(expected, actual ?: Double.NaN, 0.0)
    }
}
