package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.TrackPoint
import app.matthieu.cairngps.data.TrackPointDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TrackPointDao] fake. Exposes [points] so [FakeSessionDao] can join against it. */
class FakeTrackPointDao : TrackPointDao {

    val points = MutableStateFlow<List<TrackPoint>>(emptyList())
    private var nextId = 1L

    override suspend fun insertAll(points: List<TrackPoint>) {
        this.points.value = this.points.value + points
    }

    override suspend fun insert(point: TrackPoint): Long {
        val id = nextId++
        points.value = points.value + point.copy(id = id)
        return id
    }

    override suspend fun getAll(): List<TrackPoint> = points.value

    override suspend fun getBySession(sessionId: Long): List<TrackPoint> =
        points.value.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }

    override suspend fun deleteAll() {
        points.value = emptyList()
    }

    override suspend fun deleteBySession(sessionId: Long) {
        points.value = points.value.filterNot { it.sessionId == sessionId }
    }

    override fun observeBySession(sessionId: Long) =
        points.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.timestamp } }
}
