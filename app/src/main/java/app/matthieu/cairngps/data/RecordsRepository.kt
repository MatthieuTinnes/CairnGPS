package app.matthieu.cairngps.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for cross-session [RecordEntry] extremes, wrapping the Room [RecordDao].
 *
 * Each [RecordType] only ever improves: [submit] compares a candidate value against the one
 * currently stored and replaces it only when the candidate is strictly better (per
 * [RecordType.higherIsBetter]). That makes it safe for [GamificationManager] to feed it both live
 * fixes and finished sessions indiscriminately — this behaves like a "hall of fame", not a plain
 * overwrite.
 *
 * ViewModels talk to this repository and never touch the DAO or [AppDatabase] directly, keeping
 * persistence details out of the UI layer.
 */
class RecordsRepository(private val dao: RecordDao) {

    // Live tracking and the per-session collector in GamificationManager can both call submit()
    // for the same type concurrently; the mutex makes the read-compare-write below atomic so a
    // marginally-better value can't be lost to a stale concurrent write.
    private val mutex = Mutex()

    /** Cold flow of every record currently held; re-emits whenever the table changes. */
    fun records(): Flow<List<RecordEntry>> = dao.observeAll()

    /**
     * Registers [value] as the record for [type] if it beats the current one (or none exists
     * yet). Returns `true` if it became the new record.
     */
    suspend fun submit(
        type: RecordType,
        value: Double,
        latitude: Double? = null,
        longitude: Double? = null,
        achievedAt: Long = System.currentTimeMillis(),
        sessionId: Long? = null,
    ): Boolean = mutex.withLock {
        val current = dao.getByType(type.name)
        val improves = current == null ||
            if (type.higherIsBetter) value > current.value else value < current.value
        if (improves) {
            dao.upsert(
                RecordEntry(
                    type = type.name,
                    value = value,
                    latitude = latitude,
                    longitude = longitude,
                    achievedAt = achievedAt,
                    sessionId = sessionId,
                ),
            )
        }
        improves
    }

    /**
     * Registers every candidate in [candidates] in one pass: one [RecordDao.getAll] read and, if
     * anything improved, one [RecordDao.upsertAll] write — instead of one read + conditional write
     * per candidate under the mutex (audit 4.2: [GamificationManager] resubmits every session's 9
     * metrics on every `sessions()` emission, an O(N×9) DB round-trip pattern otherwise). Same
     * "strictly better only" semantics as [submit], and still atomic under the same [mutex].
     */
    suspend fun submitAll(candidates: List<RecordCandidate>) {
        if (candidates.isEmpty()) return
        mutex.withLock {
            val current = dao.getAll().associateBy { it.type }
            val best = LinkedHashMap<RecordType, RecordCandidate>()
            for (candidate in candidates) {
                val existing = current[candidate.type.name]
                val improvesStored = existing == null ||
                    if (candidate.type.higherIsBetter) {
                        candidate.value > existing.value
                    } else {
                        candidate.value < existing.value
                    }
                if (!improvesStored) continue

                val bestSoFar = best[candidate.type]
                val improvesBatch = bestSoFar == null ||
                    if (candidate.type.higherIsBetter) {
                        candidate.value > bestSoFar.value
                    } else {
                        candidate.value < bestSoFar.value
                    }
                if (improvesBatch) best[candidate.type] = candidate
            }
            if (best.isNotEmpty()) {
                dao.upsertAll(best.values.map { it.toRecordEntry() })
            }
        }
    }
}

/** One candidate value considered by [RecordsRepository.submitAll]; mirrors [RecordsRepository.submit]'s parameters. */
data class RecordCandidate(
    val type: RecordType,
    val value: Double,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val achievedAt: Long = System.currentTimeMillis(),
    val sessionId: Long? = null,
) {
    fun toRecordEntry() = RecordEntry(
        type = type.name,
        value = value,
        latitude = latitude,
        longitude = longitude,
        achievedAt = achievedAt,
        sessionId = sessionId,
    )
}
