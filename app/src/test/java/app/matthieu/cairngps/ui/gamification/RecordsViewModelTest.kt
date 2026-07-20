package app.matthieu.cairngps.ui.gamification

import app.cash.turbine.test
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordType
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.testutil.FakeRecordDao
import app.matthieu.cairngps.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecordsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `uiState lists every display type in order, missing ones as null`() = runTest {
        val dao = FakeRecordDao()
        dao.upsert(RecordEntry(type = RecordType.MAX_SPEED.name, value = 10.0, achievedAt = 0L))
        val viewModel = RecordsViewModel(RecordsRepository(dao))

        viewModel.uiState.test {
            assertEquals(RecordsUiState(), awaitItem()) // initial (loading) value

            val loaded = awaitItem()
            requireNotNull(loaded.items)
            assertEquals(9, loaded.items.size)
            assertEquals(RecordType.MAX_SPEED, loaded.items[0].type)
            assertEquals(10.0, loaded.items[0].entry?.value)
            // Every other display slot has no record yet.
            assertTrue(loaded.items.drop(1).all { it.entry == null })
        }
    }

    @Test
    fun `uiState never surfaces MAX_SATELLITES even when it is stored`() = runTest {
        val dao = FakeRecordDao()
        dao.upsert(RecordEntry(type = RecordType.MAX_SATELLITES.name, value = 14.0, achievedAt = 0L))
        val viewModel = RecordsViewModel(RecordsRepository(dao))

        viewModel.uiState.test {
            awaitItem() // initial
            val loaded = awaitItem()
            requireNotNull(loaded.items)
            assertTrue(loaded.items.none { it.type == RecordType.MAX_SATELLITES })
        }
    }
}
