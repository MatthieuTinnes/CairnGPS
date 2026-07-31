package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.AchievementDao
import app.matthieu.cairngps.data.AchievementState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [AchievementDao] fake, keyed by [AchievementState.id]. */
class FakeAchievementDao : AchievementDao {

    private val table = MutableStateFlow<Map<String, AchievementState>>(emptyMap())

    override fun observeAll() = table.map { it.values.toList() }

    override suspend fun insert(state: AchievementState): Long {
        if (table.value.containsKey(state.id)) return -1L
        table.value = table.value + (state.id to state)
        return 1L
    }

    override suspend fun getAll(): List<AchievementState> = table.value.values.toList()

    override suspend fun insertAll(states: List<AchievementState>) {
        table.value = table.value + states.associateBy { it.id }
    }

    override suspend fun deleteAll() {
        table.value = emptyMap()
    }
}
