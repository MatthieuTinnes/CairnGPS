package app.matthieu.cairngps.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPromptTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `no request before the third completed session`() {
        assertFalse(
            ReviewPrompt.shouldRequest(ReviewTrigger.COMPLETED_SESSIONS, count = 2, lastRequestedAt = null, now = now),
        )
    }

    @Test
    fun `first request once enough sessions have been recorded`() {
        assertTrue(
            ReviewPrompt.shouldRequest(ReviewTrigger.COMPLETED_SESSIONS, count = 3, lastRequestedAt = null, now = now),
        )
    }

    @Test
    fun `no request before the third unlocked achievement`() {
        assertFalse(
            ReviewPrompt.shouldRequest(ReviewTrigger.UNLOCKED_ACHIEVEMENTS, count = 2, lastRequestedAt = null, now = now),
        )
    }

    @Test
    fun `first request once enough achievements have been unlocked`() {
        assertTrue(
            ReviewPrompt.shouldRequest(ReviewTrigger.UNLOCKED_ACHIEVEMENTS, count = 3, lastRequestedAt = null, now = now),
        )
    }

    @Test
    fun `the minimum interval is shared across triggers`() {
        // Une invitation vient d'être affichée (peu importe le déclencheur) : le troisième succès
        // ne doit pas en relancer une aussitôt.
        val lastRequestedAt = now - ReviewPrompt.MIN_INTERVAL_MS + 1
        assertFalse(
            ReviewPrompt.shouldRequest(ReviewTrigger.UNLOCKED_ACHIEVEMENTS, count = 3, lastRequestedAt = lastRequestedAt, now = now),
        )
        assertFalse(
            ReviewPrompt.shouldRequest(ReviewTrigger.COMPLETED_SESSIONS, count = 10, lastRequestedAt = lastRequestedAt, now = now),
        )
    }

    @Test
    fun `request again once the minimum interval has elapsed`() {
        val lastRequestedAt = now - ReviewPrompt.MIN_INTERVAL_MS
        assertTrue(
            ReviewPrompt.shouldRequest(ReviewTrigger.COMPLETED_SESSIONS, count = 10, lastRequestedAt = lastRequestedAt, now = now),
        )
    }

    @Test
    fun `a clock moved backwards does not re-trigger a request`() {
        assertFalse(
            ReviewPrompt.shouldRequest(ReviewTrigger.COMPLETED_SESSIONS, count = 10, lastRequestedAt = now + 1, now = now),
        )
    }
}
