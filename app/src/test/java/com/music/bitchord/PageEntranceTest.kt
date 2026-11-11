package com.music.bitchord

import com.music.bitchord.ui.screens.FirstShowings
import com.music.bitchord.ui.screens.PageEntrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEntranceTest {

    @Test
    fun `slots start one after another from the top down`() {
        val delays = (0 until PageEntrance.SLOTS).map(PageEntrance::delayMs)
        assertEquals(0L, delays.first())
        assertEquals(delays.sorted(), delays)
        assertEquals(delays.size, delays.toSet().size)
        delays.zipWithNext().forEach { (a, b) -> assertEquals(PageEntrance.STEP_MS, b - a) }
    }

    @Test
    fun `a slot out of range never waits longer than the last one`() {
        assertEquals(0L, PageEntrance.delayMs(-3))
        assertEquals(PageEntrance.delayMs(PageEntrance.SLOTS - 1), PageEntrance.delayMs(500))
    }

    @Test
    fun `the first slots play when drawn as the page arrives`() {
        assertTrue(PageEntrance.plays(slot = 0, startedAtMs = 1_000, nowMs = 1_000))
        assertTrue(PageEntrance.plays(slot = PageEntrance.SLOTS - 1, startedAtMs = 1_000, nowMs = 1_016))
    }

    @Test
    fun `rows further down than the played slots are simply there`() {
        assertFalse(PageEntrance.plays(slot = PageEntrance.SLOTS, startedAtMs = 1_000, nowMs = 1_000))
        assertFalse(PageEntrance.plays(slot = -1, startedAtMs = 1_000, nowMs = 1_000))
    }

    @Test
    fun `a row drawn later by a scroll or a filter does not replay`() {
        val later = 1_000 + PageEntrance.JOIN_WINDOW_MS
        assertFalse(PageEntrance.plays(slot = 0, startedAtMs = 1_000, nowMs = later))
        assertTrue(PageEntrance.plays(slot = 0, startedAtMs = 1_000, nowMs = later - 1))
    }

    @Test
    fun `nothing plays when this showing has no entrance`() {
        assertFalse(PageEntrance.plays(slot = 0, startedAtMs = null, nowMs = 1_000))
    }

    @Test
    fun `a clock that went backwards does not start an entrance`() {
        assertFalse(PageEntrance.plays(slot = 0, startedAtMs = 1_000, nowMs = 900))
    }

    @Test
    fun `the whole entrance is slow but over in about a second`() {
        assertTrue(PageEntrance.totalMs in 900..1_500)
        // Each one is still moving when the next few start, so it reads as one motion.
        assertTrue(PageEntrance.DURATION_MS > PageEntrance.STEP_MS * 3)
    }

    @Test
    fun `a page is claimed once per opening`() {
        val showings = FirstShowings()
        val page = Any()
        val other = Any()
        assertTrue(showings.claim(page))
        assertFalse(showings.claim(page))
        assertTrue(showings.claim(other))
        assertFalse(showings.claim(page))
    }
}
