package app.matthieu.cairngps.ui.gamification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LevelsTest {

    @Test
    fun `forXp at zero is level 1`() {
        val info = Levels.forXp(0)
        assertEquals(1, info.level)
        assertEquals(0, info.xpIntoLevel)
        assertEquals(50, info.xpForNextLevel)
        assertEquals(50, info.xpRemaining)
        assertEquals(0f, info.fraction, 0f)
        assertFalse(info.isMaxLevel)
    }

    @Test
    fun `forXp just under the level 2 band stays level 1`() {
        val info = Levels.forXp(49)
        assertEquals(1, info.level)
        assertEquals(49, info.xpIntoLevel)
        assertEquals(1, info.xpRemaining)
    }

    @Test
    fun `forXp at the level 2 band boundary starts level 2 fresh`() {
        val info = Levels.forXp(50)
        assertEquals(2, info.level)
        assertEquals(0, info.xpIntoLevel)
        assertEquals(70, info.xpForNextLevel)
        assertEquals(0f, info.fraction, 0f)
    }

    @Test
    fun `forXp just under the max band is level 9`() {
        val info = Levels.forXp(1799)
        assertEquals(9, info.level)
        assertEquals(449, info.xpIntoLevel)
        assertEquals(1, info.xpRemaining)
        assertFalse(info.isMaxLevel)
    }

    @Test
    fun `forXp at the max band boundary is level 10 and max`() {
        val info = Levels.forXp(1800)
        assertEquals(10, info.level)
        assertEquals(0, info.xpIntoLevel)
        assertNull(info.xpForNextLevel)
        assertNull(info.xpRemaining)
        assertEquals(1f, info.fraction, 0f)
        assertTrue(info.isMaxLevel)
    }

    @Test
    fun `forXp well past the max band stays level 10`() {
        val info = Levels.forXp(5000)
        assertEquals(10, info.level)
        assertTrue(info.isMaxLevel)
        assertEquals(1f, info.fraction, 0f)
        assertEquals(5000, info.totalXp)
    }

    @Test
    fun `forXp mid-band computes a fractional progress`() {
        val info = Levels.forXp(85)
        assertEquals(2, info.level)
        assertEquals(35, info.xpIntoLevel)
        assertEquals(70, info.xpForNextLevel)
        assertEquals(0.5f, info.fraction, 1e-6f)
    }
}
