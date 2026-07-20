package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.RecordingCheckpoint
import app.matthieu.cairngps.data.RecordingCheckpointDao

/** In-memory [RecordingCheckpointDao] fake: at most one row, as in the real table. */
class FakeRecordingCheckpointDao : RecordingCheckpointDao {

    private var checkpoint: RecordingCheckpoint? = null

    /** Number of [upsert] calls made so far, to verify "at most once per sampling interval" contracts. */
    var upsertCallCount: Int = 0
        private set

    override suspend fun upsert(checkpoint: RecordingCheckpoint) {
        upsertCallCount++
        this.checkpoint = checkpoint
    }

    override suspend fun get(): RecordingCheckpoint? = checkpoint

    override suspend fun deleteAll() {
        checkpoint = null
    }
}
