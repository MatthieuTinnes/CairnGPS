package app.matthieu.cairngps.ui.settings

import app.cash.turbine.test
import app.matthieu.cairngps.data.AppSettings
import app.matthieu.cairngps.data.CoordinateFormat
import app.matthieu.cairngps.data.NorthReference
import app.matthieu.cairngps.data.SettingsRepository
import app.matthieu.cairngps.data.ThemeMode
import app.matthieu.cairngps.data.UnitSystem
import app.matthieu.cairngps.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `settings starts at the default value then reflects the repository`() = runTest {
        val stored = AppSettings(unitSystem = UnitSystem.IMPERIAL)
        val repository = mockk<SettingsRepository> {
            every { settings } returns MutableStateFlow(stored)
        }
        val viewModel = SettingsViewModel(repository)

        viewModel.settings.test {
            assertEquals(AppSettings(), awaitItem()) // initial (stateIn) value
            assertEquals(stored, awaitItem())
        }
    }

    @Test
    fun `setUnitSystem writes through to the repository`() = runTest {
        val repository = mockk<SettingsRepository> {
            every { settings } returns MutableStateFlow(AppSettings())
            coEvery { setUnitSystem(any()) } returns Unit
        }
        val viewModel = SettingsViewModel(repository)

        viewModel.setUnitSystem(UnitSystem.IMPERIAL)
        runCurrent()

        coVerify { repository.setUnitSystem(UnitSystem.IMPERIAL) }
    }

    @Test
    fun `setCoordinateFormat setThemeMode and setNorthReference write through to the repository`() = runTest {
        val repository = mockk<SettingsRepository> {
            every { settings } returns MutableStateFlow(AppSettings())
            coEvery { setCoordinateFormat(any()) } returns Unit
            coEvery { setThemeMode(any()) } returns Unit
            coEvery { setNorthReference(any()) } returns Unit
        }
        val viewModel = SettingsViewModel(repository)

        viewModel.setCoordinateFormat(CoordinateFormat.DMS)
        viewModel.setThemeMode(ThemeMode.LIGHT)
        viewModel.setNorthReference(NorthReference.TRUE)
        runCurrent()

        coVerify { repository.setCoordinateFormat(CoordinateFormat.DMS) }
        coVerify { repository.setThemeMode(ThemeMode.LIGHT) }
        coVerify { repository.setNorthReference(NorthReference.TRUE) }
    }
}
