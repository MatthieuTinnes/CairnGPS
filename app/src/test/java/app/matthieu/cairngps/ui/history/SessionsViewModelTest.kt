package app.matthieu.cairngps.ui.history

import app.cash.turbine.test
import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.testutil.FakeRecordingCheckpointDao
import app.matthieu.cairngps.testutil.FakeSessionDao
import app.matthieu.cairngps.testutil.FakeTrackPointDao
import app.matthieu.cairngps.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun sessionOf(id: Long, startTimestamp: Long) = Session(
        id = id,
        name = "s$id",
        startTimestamp = startTimestamp,
        endTimestamp = startTimestamp,
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

    private fun repository(trackPointDao: FakeTrackPointDao = FakeTrackPointDao()) =
        SessionRepository(FakeSessionDao(trackPointDao), trackPointDao, FakeRecordingCheckpointDao())

    @Test
    fun `uiState starts in a loading state, distinct from an empty loaded list`() = runTest {
        val viewModel = SessionsViewModel(repository())

        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(null, initial.sessions)
            assertFalse(initial.isEmpty)
        }
    }

    @Test
    fun `uiState reports isEmpty once loaded with no sessions`() = runTest {
        val dao = FakeSessionDao()
        val repository = SessionRepository(dao, FakeTrackPointDao(), FakeRecordingCheckpointDao())
        val viewModel = SessionsViewModel(repository)

        viewModel.uiState.test {
            awaitItem() // initial loading state
            val loaded = awaitItem()
            assertTrue(loaded.isEmpty)
        }
    }

    @Test
    fun `uiState sorts each session's track chronologically regardless of insertion order`() = runTest {
        val trackPointDao = FakeTrackPointDao()
        val sessionDao = FakeSessionDao(trackPointDao)
        val repository = SessionRepository(sessionDao, trackPointDao, FakeRecordingCheckpointDao())
        val sessionId = sessionDao.insert(sessionOf(id = 0, startTimestamp = 1_000L))
        // Inserted out of chronological order; the ViewModel must sort by timestamp itself
        // ([Relation] doesn't guarantee ordering).
        trackPointDao.insert(TrackPoint(sessionId = sessionId, timestamp = 300L, latitude = 0.0, longitude = 0.0, altitude = 0.0))
        trackPointDao.insert(TrackPoint(sessionId = sessionId, timestamp = 100L, latitude = 0.0, longitude = 0.0, altitude = 0.0))
        trackPointDao.insert(TrackPoint(sessionId = sessionId, timestamp = 200L, latitude = 0.0, longitude = 0.0, altitude = 0.0))

        val viewModel = SessionsViewModel(repository)

        viewModel.uiState.test {
            awaitItem() // initial loading state
            val loaded = awaitItem()
            requireNotNull(loaded.sessions)
            val track = loaded.sessions.single().track
            assertEquals(listOf(100L, 200L, 300L), track.map { it.timestamp })
        }
    }
}
