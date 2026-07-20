package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.Waypoint
import app.matthieu.cairngps.data.WaypointDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [WaypointDao] fake. */
class FakeWaypointDao : WaypointDao {

    private val waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(waypoint: Waypoint): Long {
        val id = nextId++
        waypoints.value = waypoints.value + waypoint.copy(id = id)
        return id
    }

    override suspend fun insertAll(waypoints: List<Waypoint>) {
        this.waypoints.value = this.waypoints.value + waypoints
    }

    override suspend fun getAll(): List<Waypoint> = waypoints.value

    override suspend fun deleteAll() {
        waypoints.value = emptyList()
    }

    override fun observeAll() = waypoints.map { list -> list.sortedByDescending { it.timestamp } }

    override suspend fun getById(id: Long): Waypoint? = waypoints.value.firstOrNull { it.id == id }

    override suspend fun deleteById(id: Long) {
        waypoints.value = waypoints.value.filterNot { it.id == id }
    }

    override fun observeBySession(sessionId: Long) = waypoints.map { list ->
        list.filter { it.sessionId == sessionId }.sortedByDescending { it.timestamp }
    }

    override suspend fun attachToSession(ids: List<Long>, sessionId: Long) {
        waypoints.value = waypoints.value.map { if (it.id in ids) it.copy(sessionId = sessionId) else it }
    }

    override suspend fun rename(id: Long, name: String) {
        waypoints.value = waypoints.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun updateIcon(id: Long, icon: String) {
        waypoints.value = waypoints.value.map { if (it.id == id) it.copy(icon = icon) else it }
    }

    override suspend fun updateNameAndIcon(id: Long, name: String, icon: String) {
        waypoints.value = waypoints.value.map { if (it.id == id) it.copy(name = name, icon = icon) else it }
    }
}
