package app.matthieu.cairngps.ui.gamification

import app.cash.turbine.test
import app.matthieu.cairngps.data.AchievementState
import app.matthieu.cairngps.data.AchievementsRepository
import app.matthieu.cairngps.data.RecordEntry
import app.matthieu.cairngps.data.RecordType
import app.matthieu.cairngps.data.RecordsRepository
import app.matthieu.cairngps.data.SessionRepository
import app.matthieu.cairngps.data.WaypointRepository
import app.matthieu.cairngps.domain.gamification.Achievements
import app.matthieu.cairngps.testutil.FakeAchievementDao
import app.matthieu.cairngps.testutil.FakeRecordDao
import app.matthieu.cairngps.testutil.FakeRecordingCheckpointDao
import app.matthieu.cairngps.testutil.FakeSessionDao
import app.matthieu.cairngps.testutil.FakeTrackPointDao
import app.matthieu.cairngps.testutil.FakeWaypointDao
import app.matthieu.cairngps.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class AchievementsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val achievementDao = FakeAchievementDao()
    private val recordDao = FakeRecordDao()
    private val sessionDao = FakeSessionDao()
    private val waypointDao = FakeWaypointDao()

    private fun viewModel() = AchievementsViewModel(
        AchievementsRepository(achievementDao),
        RecordsRepository(recordDao),
        SessionRepository(sessionDao, FakeTrackPointDao(), FakeRecordingCheckpointDao()),
        WaypointRepository(waypointDao),
    )

    @Test
    fun `uiState exposes every catalog entry with unlockedCount and totalCount`() = runTest {
        achievementDao.insert(AchievementState(id = "sessions_1", unlockedAt = 500L))

        viewModel().uiState.test {
            awaitItem() // initial (loading) value
            val loaded = awaitItem()
            requireNotNull(loaded.items)
            assertEquals(Achievements.ALL.size, loaded.items.size)
            assertEquals(Achievements.ALL.size, loaded.totalCount)
            assertEquals(1, loaded.unlockedCount)

            val sessionsOne = loaded.items.first { it.def.id == "sessions_1" }
            assertEquals(500L, sessionsOne.unlockedAt)
            assertEquals(true, sessionsOne.isUnlocked)

            val stillLocked = loaded.items.first { it.def.id == "altitude_1000" }
            assertNull(stillLocked.unlockedAt)
        }
    }

    @Test
    fun `uiState next reacts to a new record emitted mid-collection`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            awaitItem() // initial (loading) value
            val beforeRecord = awaitItem()
            // Nothing recorded yet: altitude is the closest scalar family, at zero progress.
            assertEquals("altitude_1000", beforeRecord.next?.def?.id)

            // 29 km/h is nearly at the 30 km/h speed palier: closer than altitude's zero progress.
            recordDao.upsert(RecordEntry(type = RecordType.MAX_SPEED.name, value = 29.0 / 3.6, achievedAt = 0L))

            val afterRecord = awaitItem()
            assertEquals("speed_30", afterRecord.next?.def?.id)
        }
    }
}
