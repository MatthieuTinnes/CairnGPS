package app.matthieu.cairngps.ui.location

import app.cash.turbine.test
import app.matthieu.cairngps.data.Constellation
import app.matthieu.cairngps.data.LocationData
import app.matthieu.cairngps.data.LocationRepository
import app.matthieu.cairngps.data.RecordingRepository
import app.matthieu.cairngps.data.SatelliteInfo
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.testutil.FakeWaypointDao
import app.matthieu.cairngps.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val waypointDao = FakeWaypointDao()

    private fun fixOf(latitude: Double = 47.0, longitude: Double = 6.0) = LocationData(
        latitude = latitude,
        longitude = longitude,
        altitude = 500.0,
        speed = 3f,
        horizontalAccuracy = 4f,
        verticalAccuracy = null,
        timestamp = 1_000L,
    )

    private fun satelliteOf(usedInFix: Boolean) = SatelliteInfo(
        constellation = Constellation.GPS,
        svid = 1,
        cn0DbHz = 30f,
        usedInFix = usedInFix,
        azimuthDegrees = 0f,
        elevationDegrees = 45f,
    )

    private fun viewModel(
        locationRepository: LocationRepository = mockk(),
        recordingRepository: RecordingRepository = mockk(),
    ) = LocationViewModel(locationRepository, WaypointRepository(waypointDao), recordingRepository)

    @Test
    fun `uiState has no fix before any tracking starts`() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.uiState.value.hasFix)
        assertEquals(null, viewModel.uiState.value.satellitesUsedInFix)
    }

    @Test
    fun `startTracking propagates a fix and satellite counts into uiState`() = runTest {
        val locationRepository = mockk<LocationRepository> {
            every { locationUpdates() } returns flowOf(fixOf())
            every { satelliteUpdates() } returns
                flowOf(listOf(satelliteOf(usedInFix = true), satelliteOf(usedInFix = false)))
            every { gpsProviderEnabled() } returns emptyFlow()
        }
        val viewModel = viewModel(locationRepository = locationRepository)

        viewModel.uiState.test {
            assertFalse(awaitItem().hasFix) // initial

            viewModel.startTracking()
            runCurrent()

            // Fix and satellite collection run in sibling coroutines; only the settled state
            // matters, not the order of the two intermediate emissions.
            val settled = expectMostRecentItem()
            assertTrue(settled.hasFix)
            assertEquals(47.0, settled.fix?.latitude)
            assertEquals(1, settled.satellitesUsedInFix)
            assertEquals(2, settled.satellitesVisible)

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun trackingViewModel(
        fixes: MutableSharedFlow<LocationData>,
        providerEnabled: MutableSharedFlow<Boolean> = MutableSharedFlow(),
        recordingRepository: RecordingRepository = mockk(),
    ): LocationViewModel {
        val locationRepository = mockk<LocationRepository> {
            every { locationUpdates() } returns fixes
            every { satelliteUpdates() } returns emptyFlow()
            every { gpsProviderEnabled() } returns providerEnabled
        }
        return viewModel(locationRepository = locationRepository, recordingRepository = recordingRepository)
    }

    @Test
    fun `fix becomes stale when no update arrives within the timeout`() = runTest {
        val fixes = MutableSharedFlow<LocationData>()
        val viewModel = trackingViewModel(fixes)

        viewModel.startTracking()
        runCurrent()
        fixes.emit(fixOf())
        runCurrent()
        assertTrue(viewModel.uiState.value.hasFix)

        advanceTimeBy(5_001.milliseconds)

        assertFalse(viewModel.uiState.value.hasFix)
        assertTrue(viewModel.uiState.value.isFixLost)
    }

    @Test
    fun `fix stays fresh as long as updates keep arriving within the timeout`() = runTest {
        val fixes = MutableSharedFlow<LocationData>()
        val viewModel = trackingViewModel(fixes)

        viewModel.startTracking()
        runCurrent()
        fixes.emit(fixOf())
        runCurrent()
        advanceTimeBy(3_000.milliseconds)
        fixes.emit(fixOf())
        runCurrent()
        advanceTimeBy(3_000.milliseconds)

        assertTrue(viewModel.uiState.value.hasFix)
        assertFalse(viewModel.uiState.value.isFixLost)
    }

    @Test
    fun `a new fix after a signal loss restores the fresh state`() = runTest {
        val fixes = MutableSharedFlow<LocationData>()
        val viewModel = trackingViewModel(fixes)

        viewModel.startTracking()
        runCurrent()
        fixes.emit(fixOf())
        runCurrent()
        advanceTimeBy(5_001.milliseconds)
        assertTrue(viewModel.uiState.value.isFixLost)

        fixes.emit(fixOf(latitude = 45.0))
        runCurrent()

        assertTrue(viewModel.uiState.value.hasFix)
        assertFalse(viewModel.uiState.value.isFixLost)
        assertEquals(45.0, viewModel.uiState.value.fix?.latitude)
    }

    @Test
    fun `disabling the GPS provider marks the fix stale without waiting for the timeout`() = runTest {
        val fixes = MutableSharedFlow<LocationData>()
        val providerEnabled = MutableSharedFlow<Boolean>()
        val viewModel = trackingViewModel(fixes, providerEnabled = providerEnabled)

        viewModel.startTracking()
        runCurrent()
        fixes.emit(fixOf())
        runCurrent()
        assertTrue(viewModel.uiState.value.hasFix)

        providerEnabled.emit(false)
        runCurrent()

        assertFalse(viewModel.uiState.value.hasFix)
        assertTrue(viewModel.uiState.value.isFixLost)
    }

    @Test
    fun `saveWaypoint is a no-op when the fix has gone stale`() = runTest {
        val fixes = MutableSharedFlow<LocationData>()
        val viewModel = trackingViewModel(fixes)

        viewModel.startTracking()
        runCurrent()
        fixes.emit(fixOf())
        runCurrent()
        advanceTimeBy(5_001.milliseconds)

        viewModel.saveWaypoint("Sommet")
        runCurrent()

        assertTrue(waypointDao.getAll().isEmpty())
    }

    @Test
    fun `saveWaypoint is a no-op when no fix is available yet`() = runTest {
        val viewModel = viewModel()

        viewModel.saveWaypoint("Sommet")
        runCurrent()

        assertTrue(waypointDao.getAll().isEmpty())
    }

    @Test
    fun `saveWaypoint persists the current fix with the satellite count captured with it`() = runTest {
        val recordingRepository = mockk<RecordingRepository> {
            coEvery { reserveWaypointAttachment() } returns false
            coEvery { completeWaypointAttachment(any(), any()) } returns Unit
        }
        val locationRepository = mockk<LocationRepository> {
            every { locationUpdates() } returns flowOf(fixOf(latitude = 48.85, longitude = 2.35))
            every { satelliteUpdates() } returns flowOf(listOf(satelliteOf(usedInFix = true)))
            every { gpsProviderEnabled() } returns emptyFlow()
        }
        val viewModel = viewModel(locationRepository = locationRepository, recordingRepository = recordingRepository)

        viewModel.uiState.test {
            awaitItem() // initial
            viewModel.startTracking()
            awaitItem() // fix
            awaitItem() // satellites

            viewModel.saveWaypoint("Sommet")
            runCurrent()

            val saved = waypointDao.getAll().single()
            assertEquals("Sommet", saved.name)
            assertEquals(48.85, saved.latitude, 0.0)
            assertEquals(2.35, saved.longitude, 0.0)
            assertEquals(1, saved.satellitesUsedInFix)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
