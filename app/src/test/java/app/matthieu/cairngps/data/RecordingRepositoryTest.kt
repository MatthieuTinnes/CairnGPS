package app.matthieu.cairngps.data

import android.location.Location
import app.cash.turbine.test
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Uses the real [Location.distanceBetween] (needs Robolectric, unlike the rest of the suite which
 * runs on plain JVM), which is why this class alone pulls in [RobolectricTestRunner].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordingRepositoryTest {

    private val t0 = 1_700_000_000_000L

    private fun fix(
        latitude: Double,
        longitude: Double,
        altitude: Double = 500.0,
        speed: Float = 1f,
        accuracy: Float = 5f,
        timestamp: Long,
    ) = LocationData(
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = speed,
        horizontalAccuracy = accuracy,
        verticalAccuracy = null,
        timestamp = timestamp,
    )

    private fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    /**
     * Buffered so [MutableSharedFlow.emit] never blocks on a slow/absent collector: tests still
     * must [advanceUntilIdle] after [RecordingRepository.start] before emitting, since a value
     * emitted before the collector subscribes (replay = 0) would otherwise be lost.
     */
    private class Harness(scheduler: TestCoroutineScheduler) {
        val fixes = MutableSharedFlow<LocationData>(extraBufferCapacity = 16)
        val locationRepository: LocationRepository = mockk {
            every { locationUpdates() } returns fixes
        }
        val trackPointDao = FakeTrackPointDao()
        val sessionDao = FakeSessionDao(trackPointDao)
        val checkpointDao = FakeRecordingCheckpointDao()
        val sessionRepository = SessionRepository(sessionDao, trackPointDao, checkpointDao)
        val waypointDao = FakeWaypointDao()
        val repository = RecordingRepository(
            locationRepository,
            sessionRepository,
            WaypointRepository(waypointDao),
            StandardTestDispatcher(scheduler),
        )
    }

    private suspend fun TestScope.pushFix(harness: Harness, value: LocationData) {
        harness.fixes.emit(value)
        advanceUntilIdle()
    }

    @Test
    fun `fix with horizontal accuracy above 20m is dropped`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, accuracy = 25f, timestamp = t0))

        assertEquals(0.0, h.repository.state.value.distanceMeters, 0.0)
        assertNull(h.repository.state.value.currentAltitude)
    }

    @Test
    fun `distance accumulates between accepted fixes`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, timestamp = t0))
        pushFix(h, fix(47.001, 6.0, timestamp = t0 + 1_000))

        val expected = distanceBetween(47.0, 6.0, 47.001, 6.0)
        assertEquals(expected.toDouble(), h.repository.state.value.distanceMeters, 0.01)
    }

    @Test
    fun `elevation change below the 4m threshold is not counted`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, altitude = 100.0, timestamp = t0))
        pushFix(h, fix(47.0, 6.0, altitude = 103.9, timestamp = t0 + 1_000))

        assertEquals(0.0, h.repository.state.value.elevationGain, 0.0)
        assertEquals(0.0, h.repository.state.value.elevationLoss, 0.0)
    }

    @Test
    fun `elevation gain is counted at the threshold and the reference rebases to avoid double counting`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, altitude = 100.0, timestamp = t0))
        pushFix(h, fix(47.0, 6.0, altitude = 104.0, timestamp = t0 + 1_000)) // +4: counted, reference -> 104
        pushFix(h, fix(47.0, 6.0, altitude = 107.0, timestamp = t0 + 2_000)) // +3 from the new reference: not counted

        assertEquals(4.0, h.repository.state.value.elevationGain, 0.0)
    }

    @Test
    fun `elevation loss is counted on a descent past the threshold`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, altitude = 100.0, timestamp = t0))
        pushFix(h, fix(47.0, 6.0, altitude = 95.0, timestamp = t0 + 1_000))

        assertEquals(5.0, h.repository.state.value.elevationLoss, 0.0)
    }

    @Test
    fun `moving average excludes time spent below the moving speed threshold`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, speed = 0.3f, timestamp = t0))
        // Stationary (speed <= 0.5 m/s): excluded from the moving-only average.
        pushFix(h, fix(47.0, 6.0, speed = 0.3f, timestamp = t0 + 1_000))
        // Moving, 10s after the stationary fix.
        pushFix(h, fix(47.001, 6.0, speed = 2f, timestamp = t0 + 11_000))

        val movingSegment = distanceBetween(47.0, 6.0, 47.001, 6.0)
        val expectedAverageSpeed = movingSegment / 10.0

        assertEquals(expectedAverageSpeed, h.repository.state.value.averageSpeed.toDouble(), 0.01)
    }

    @Test
    fun `maxSpeed retains the peak speed seen`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, speed = 1f, timestamp = t0))
        pushFix(h, fix(47.0, 6.0, speed = 3f, timestamp = t0 + 1_000))
        pushFix(h, fix(47.0, 6.0, speed = 2f, timestamp = t0 + 2_000))

        assertEquals(3f, h.repository.state.value.maxSpeed)
    }

    @Test
    fun `stop with zero accepted fixes discards the session`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, accuracy = 30f, timestamp = t0)) // rejected: too inaccurate

        // Discarded: nothing was persisted, so RecordingService must not arm the review prompt.
        assertTrue(h.repository.stop() is StopResult.Discarded)
        advanceUntilIdle()

        assertTrue(h.sessionDao.getAll().isEmpty())
        assertNull(h.checkpointDao.get())
    }

    @Test
    fun `stop with no recording in progress returns NotRecording`() = runTest {
        val h = Harness(testScheduler)

        assertEquals(StopResult.NotRecording, h.repository.stop())
    }

    @Test
    fun `rejectedAccuracyMeters is set only once rejected fixes form a sustained streak`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, accuracy = 30f, timestamp = t0))
        pushFix(h, fix(47.0, 6.0, accuracy = 30f, timestamp = t0 + 1_000))
        // A lone rejected fix (or two) isn't surfaced yet, to avoid flickering the warning on and off.
        assertNull(h.repository.state.value.rejectedAccuracyMeters)

        pushFix(h, fix(47.0, 6.0, accuracy = 33f, timestamp = t0 + 2_000)) // 3rd in a row: crosses the streak
        assertEquals(33f, h.repository.state.value.rejectedAccuracyMeters)
    }

    @Test
    fun `an accepted fix clears rejectedAccuracyMeters`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        repeat(3) { i -> pushFix(h, fix(47.0, 6.0, accuracy = 30f, timestamp = t0 + i * 1_000L)) }
        assertEquals(30f, h.repository.state.value.rejectedAccuracyMeters)

        pushFix(h, fix(47.0, 6.0, timestamp = t0 + 3_000))
        assertNull(h.repository.state.value.rejectedAccuracyMeters)
    }

    @Test
    fun `stop discarding a recording emits on discardedEvents`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        h.repository.discardedEvents.test {
            repeat(3) { i -> pushFix(h, fix(47.0, 6.0, accuracy = 30f, timestamp = t0 + i * 1_000L)) }

            val result = h.repository.stop()
            advanceUntilIdle()

            assertTrue(result is StopResult.Discarded)
            assertEquals(30f, (result as StopResult.Discarded).lastRejectedAccuracyMeters)
            assertEquals(30f, awaitItem().lastRejectedAccuracyMeters)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stop finalizes the session with its aggregates and bounding box`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, altitude = 500.0, timestamp = t0))
        pushFix(h, fix(47.001, 6.001, altitude = 510.0, timestamp = t0 + 5_000))
        pushFix(h, fix(46.999, 5.999, altitude = 495.0, timestamp = t0 + 10_000))

        val result = h.repository.stop()
        advanceUntilIdle()

        val session = h.sessionDao.getAll().single()
        assertEquals(session.id, (result as StopResult.Saved).sessionId)
        assertFalse(session.isActive)
        assertEquals(10.0, session.elevationGain, 0.0)
        assertEquals(15.0, session.elevationLoss, 0.0)
        assertEquals(495.0, session.minAltitude, 0.0)
        assertEquals(510.0, session.maxAltitude, 0.0)
        assertEquals(47.001, session.latitudeMax, 0.0)
        assertEquals(46.999, session.latitudeMin, 0.0)
        assertEquals(6.001, session.longitudeMax, 0.0)
        assertEquals(5.999, session.longitudeMin, 0.0)

        val expectedDistance = distanceBetween(47.0, 6.0, 47.001, 6.001) +
            distanceBetween(47.001, 6.001, 46.999, 5.999)
        assertEquals(expectedDistance.toDouble(), session.distanceMeters, 0.05)

        assertTrue(h.trackPointDao.getBySession(session.id).isNotEmpty())
    }

    @Test
    fun `checkpoint is persisted at most once per 5s of fix time`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        pushFix(h, fix(47.0, 6.0, timestamp = t0)) // first fix always samples (lastSampledAtMs starts at 0)
        pushFix(h, fix(47.0, 6.0, timestamp = t0 + 1_000))
        pushFix(h, fix(47.0, 6.0, timestamp = t0 + 2_000))

        assertEquals(1, h.checkpointDao.upsertCallCount)
        assertEquals(t0, h.checkpointDao.get()?.lastTimestamp)

        pushFix(h, fix(47.0, 6.0, timestamp = t0 + 5_000)) // 5s past the last sample: samples again

        assertEquals(2, h.checkpointDao.upsertCallCount)
        assertEquals(t0 + 5_000, h.checkpointDao.get()?.lastTimestamp)
    }

    @Test
    fun `resumeIfActive restores the accumulator from the last checkpoint`() = runTest {
        val trackPointDao = FakeTrackPointDao()
        val sessionDao = FakeSessionDao(trackPointDao)
        val checkpointDao = FakeRecordingCheckpointDao()
        val sessionRepository = SessionRepository(sessionDao, trackPointDao, checkpointDao)
        val waypointRepository = WaypointRepository(FakeWaypointDao())

        val fixes1 = MutableSharedFlow<LocationData>(extraBufferCapacity = 16)
        val locationRepository1: LocationRepository = mockk { every { locationUpdates() } returns fixes1 }
        val repository1 = RecordingRepository(
            locationRepository1, sessionRepository, waypointRepository, StandardTestDispatcher(testScheduler),
        )

        repository1.start("Trace")
        advanceUntilIdle()
        fixes1.emit(fix(47.0, 6.0, timestamp = t0))
        advanceUntilIdle()
        fixes1.emit(fix(47.001, 6.0, timestamp = t0 + 5_000)) // triggers a checkpoint write
        advanceUntilIdle()

        val distanceBeforeDeath = repository1.state.value.distanceMeters
        // Regression check: a checkpoint write must not clear the session's isActive flag, or
        // nothing below would ever find it to resume.
        assertTrue(sessionDao.getActive()?.isActive == true)

        // Simulate a process death: a brand new repository instance over the same DAOs.
        val fixes2 = MutableSharedFlow<LocationData>(extraBufferCapacity = 16)
        val locationRepository2: LocationRepository = mockk { every { locationUpdates() } returns fixes2 }
        val repository2 = RecordingRepository(
            locationRepository2, sessionRepository, waypointRepository, StandardTestDispatcher(testScheduler),
        )

        val resumed = repository2.resumeIfActive()
        advanceUntilIdle()

        assertTrue(resumed)
        assertEquals(distanceBeforeDeath, repository2.state.value.distanceMeters, 0.0)

        val nextSegment = distanceBetween(47.001, 6.0, 47.002, 6.0)
        fixes2.emit(fix(47.002, 6.0, timestamp = t0 + 10_000))
        advanceUntilIdle()

        assertEquals(distanceBeforeDeath + nextSegment, repository2.state.value.distanceMeters, 0.01)
    }

    @Test
    fun `resumeIfActive returns false when no recording was active`() = runTest {
        val h = Harness(testScheduler)

        assertFalse(h.repository.resumeIfActive())
    }

    @Test
    fun `waypoints reserved during a recording are attached to its session at stop`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()
        pushFix(h, fix(47.0, 6.0, timestamp = t0)) // an accepted fix, so stop() doesn't discard the session

        val reserved = h.repository.reserveWaypointAttachment()
        assertTrue(reserved)
        val waypointId = h.waypointDao.insert(
            Waypoint(
                name = "Sommet",
                latitude = 47.0,
                longitude = 6.0,
                altitude = 500.0,
                speed = 0f,
                horizontalAccuracy = 5f,
                satellitesUsedInFix = null,
                timestamp = t0,
            ),
        )
        h.repository.completeWaypointAttachment(reserved, waypointId)

        h.repository.stop()
        advanceUntilIdle()

        val session = h.sessionDao.getAll().single()
        val savedWaypoint = h.waypointDao.getAll().single()
        assertEquals(session.id, savedWaypoint.sessionId)
    }

    @Test
    fun `reserveWaypointAttachment returns false once the recording has stopped`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()
        pushFix(h, fix(47.0, 6.0, timestamp = t0))
        h.repository.stop()
        advanceUntilIdle()

        assertFalse(h.repository.reserveWaypointAttachment())
    }

    @Test
    fun `a long recording's track is decimated to at most 1000 points`() = runTest {
        val h = Harness(testScheduler)
        h.repository.start("Trace")
        advanceUntilIdle()

        repeat(1_050) { i -> pushFix(h, fix(47.0, 6.0, timestamp = t0 + i * 5_000L)) }

        h.repository.stop()
        advanceUntilIdle()

        val session = h.sessionDao.getAll().single()
        assertTrue(h.trackPointDao.getBySession(session.id).size <= 1_000)
    }
}
