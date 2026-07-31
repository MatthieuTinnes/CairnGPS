package app.matthieu.cairngps.testutil

import app.matthieu.cairngps.data.Session
import app.matthieu.cairngps.data.SessionDao
import app.matthieu.cairngps.data.SessionWithTrackPoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory [SessionDao] fake. Joins against [trackPointDao] for [observeAllWithTracks], mirroring
 * the real `@Relation` query — mind that, like Room, ordering of the joined track isn't guaranteed.
 */
class FakeSessionDao(private val trackPointDao: FakeTrackPointDao = FakeTrackPointDao()) : SessionDao {

    private val sessions = MutableStateFlow<List<Session>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(session: Session): Long {
        val id = nextId++
        sessions.value = sessions.value + session.copy(id = id)
        return id
    }

    override suspend fun insertAll(sessions: List<Session>) {
        this.sessions.value = this.sessions.value + sessions
    }

    override suspend fun update(session: Session) {
        sessions.value = sessions.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun getAll(): List<Session> = sessions.value.filterNot { it.isActive }

    override suspend fun deleteAll() {
        sessions.value = emptyList()
    }

    override fun observeAll() = sessions.map { list ->
        list.filterNot { it.isActive }.sortedByDescending { it.startTimestamp }
    }

    override fun observeAllWithTracks() = combine(sessions, trackPointDao.points) { allSessions, allPoints ->
        allSessions.filterNot { it.isActive }
            .sortedByDescending { it.startTimestamp }
            .map { session -> SessionWithTrackPoints(session, allPoints.filter { it.sessionId == session.id }) }
    }

    override suspend fun getById(id: Long): Session? = sessions.value.firstOrNull { it.id == id }

    override suspend fun getActive(): Session? = sessions.value.firstOrNull { it.isActive }

    override suspend fun deleteById(id: Long) {
        sessions.value = sessions.value.filterNot { it.id == id }
    }

    override suspend fun rename(id: Long, name: String) {
        sessions.value = sessions.value.map { if (it.id == id) it.copy(name = name) else it }
    }
}
