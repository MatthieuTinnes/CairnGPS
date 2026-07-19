package app.matthieu.cairngps.data

import app.matthieu.cairngps.testutil.FakeRecordDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsRepositoryTest {

    private val dao = FakeRecordDao()
    private val repository = RecordsRepository(dao)

    @Test
    fun `submit into an empty table stores the row and returns true`() = runTest {
        val improved = repository.submit(
            type = RecordType.MAX_SPEED,
            value = 12.0,
            latitude = 47.0,
            longitude = 6.0,
            achievedAt = 1_000L,
            sessionId = 42L,
        )

        assertTrue(improved)
        val stored = dao.getByType(RecordType.MAX_SPEED.name)
        requireNotNull(stored)
        assertEquals(12.0, stored.value, 0.0)
        assertEquals(47.0, stored.latitude)
        assertEquals(6.0, stored.longitude)
        assertEquals(1_000L, stored.achievedAt)
        assertEquals(42L, stored.sessionId)
    }

    @Test
    fun `submit replaces a higherIsBetter record with a strictly higher value`() = runTest {
        repository.submit(RecordType.MAX_SPEED, 10.0)

        assertTrue(repository.submit(RecordType.MAX_SPEED, 15.0))
        assertEquals(15.0, dao.getByType(RecordType.MAX_SPEED.name)?.value)
    }

    @Test
    fun `submit rejects a lower value for a higherIsBetter type`() = runTest {
        repository.submit(RecordType.MAX_SPEED, 10.0)

        assertFalse(repository.submit(RecordType.MAX_SPEED, 5.0))
        assertEquals(10.0, dao.getByType(RecordType.MAX_SPEED.name)?.value)
    }

    @Test
    fun `submit rejects an equal value, only a strictly better one improves`() = runTest {
        repository.submit(RecordType.MAX_SPEED, 10.0)

        assertFalse(repository.submit(RecordType.MAX_SPEED, 10.0))
    }

    @Test
    fun `submit replaces a lowerIsBetter record with a strictly lower value`() = runTest {
        repository.submit(RecordType.MIN_ALTITUDE, 100.0)

        assertTrue(repository.submit(RecordType.MIN_ALTITUDE, 50.0))
        assertEquals(50.0, dao.getByType(RecordType.MIN_ALTITUDE.name)?.value)
    }

    @Test
    fun `submit rejects a higher value for a lowerIsBetter type`() = runTest {
        repository.submit(RecordType.MIN_ALTITUDE, 100.0)

        assertFalse(repository.submit(RecordType.MIN_ALTITUDE, 150.0))
        assertEquals(100.0, dao.getByType(RecordType.MIN_ALTITUDE.name)?.value)
    }

    @Test
    fun `records reemits after a successful submit`() = runTest {
        assertTrue(repository.records().first().isEmpty())

        repository.submit(RecordType.MAX_SPEED, 10.0)

        assertEquals(1, repository.records().first().size)
    }

    @Test
    fun `submitAll on an empty list makes no DAO calls`() = runTest {
        repository.submitAll(emptyList())

        assertEquals(0, dao.upsertAllCallCount)
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `submitAll writes only the best candidate per type in a single batch call`() = runTest {
        repository.submitAll(
            listOf(
                RecordCandidate(RecordType.MAX_SPEED, 10.0),
                RecordCandidate(RecordType.MAX_SPEED, 25.0),
                RecordCandidate(RecordType.MAX_SPEED, 18.0),
            ),
        )

        assertEquals(1, dao.upsertAllCallCount)
        assertEquals(25.0, dao.getByType(RecordType.MAX_SPEED.name)?.value)
    }

    @Test
    fun `submitAll drops candidates that do not improve the stored value`() = runTest {
        repository.submit(RecordType.MAX_SPEED, 30.0)

        repository.submitAll(listOf(RecordCandidate(RecordType.MAX_SPEED, 20.0)))

        // No improving candidate: no batch write at all.
        assertEquals(0, dao.upsertAllCallCount)
        assertEquals(30.0, dao.getByType(RecordType.MAX_SPEED.name)?.value)
    }

    @Test
    fun `submitAll writes only the improving types from a mixed batch`() = runTest {
        repository.submit(RecordType.MAX_SPEED, 30.0)
        repository.submit(RecordType.MIN_ALTITUDE, 100.0)

        repository.submitAll(
            listOf(
                RecordCandidate(RecordType.MAX_SPEED, 20.0), // worse, dropped
                RecordCandidate(RecordType.MIN_ALTITUDE, 40.0), // better, kept
            ),
        )

        assertEquals(1, dao.upsertAllCallCount)
        assertEquals(30.0, dao.getByType(RecordType.MAX_SPEED.name)?.value)
        assertEquals(40.0, dao.getByType(RecordType.MIN_ALTITUDE.name)?.value)
    }
}
