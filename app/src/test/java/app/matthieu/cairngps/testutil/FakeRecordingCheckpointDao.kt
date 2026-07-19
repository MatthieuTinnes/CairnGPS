package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.RecordingCheckpoint
import app.matthieu.cairngps.data.RecordingCheckpointDao

/** In-memory [RecordingCheckpointDao] fake: at most one row, as in the real table. */
class FakeRecordingCheckpointDao : RecordingCheckpointDao {

    private var checkpoint: RecordingCheckpoint? = null

    override suspend fun upsert(checkpoint: RecordingCheckpoint) {
        this.checkpoint = checkpoint
    }

    override suspend fun get(): RecordingCheckpoint? = checkpoint

    override suspend fun deleteAll() {
        checkpoint = null
    }
}
