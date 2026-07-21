package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.GamificationFlag
import app.matthieu.cairngps.data.GamificationFlagDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [GamificationFlagDao] fake, keyed by [GamificationFlag.key]. */
class FakeGamificationFlagDao : GamificationFlagDao {

    private val table = MutableStateFlow<Map<String, GamificationFlag>>(emptyMap())

    override fun observeAll() = table.map { it.values.toList() }

    override suspend fun insert(flag: GamificationFlag): Long {
        if (table.value.containsKey(flag.key)) return -1L
        table.value = table.value + (flag.key to flag)
        return 1L
    }

    override suspend fun getAll(): List<GamificationFlag> = table.value.values.toList()

    override suspend fun insertAll(flags: List<GamificationFlag>) {
        table.value = table.value + flags.associateBy { it.key }
    }

    override suspend fun deleteAll() {
        table.value = emptyMap()
    }
}
