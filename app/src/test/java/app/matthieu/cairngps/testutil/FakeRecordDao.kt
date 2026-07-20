package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.RecordDao
import app.matthieu.cairngps.data.RecordEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [RecordDao] fake, keyed by [RecordEntry.type]. */
class FakeRecordDao : RecordDao {

    private val table = MutableStateFlow<Map<String, RecordEntry>>(emptyMap())

    /** Number of [upsertAll] calls made so far, to verify batch-write contracts. */
    var upsertAllCallCount: Int = 0
        private set

    override fun observeAll() = table.map { it.values.toList() }

    override suspend fun getAll(): List<RecordEntry> = table.value.values.toList()

    override suspend fun getByType(type: String): RecordEntry? = table.value[type]

    override suspend fun upsert(entry: RecordEntry) {
        table.value = table.value + (entry.type to entry)
    }

    override suspend fun upsertAll(entries: List<RecordEntry>) {
        upsertAllCallCount++
        table.value = table.value + entries.associateBy { it.type }
    }

    override suspend fun deleteAll() {
        table.value = emptyMap()
    }
}
