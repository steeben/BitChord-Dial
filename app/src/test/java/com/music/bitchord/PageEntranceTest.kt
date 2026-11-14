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

    @Test
    fun `placeholder rows follow the known song count`() {
        assertEquals(1, PageEntrance.placeholderRows(1))
        assertEquals(5, PageEntrance.placeholderRows(5))
        assertEquals(PageEntrance.PLACEHOLDER_ROWS_MAX, PageEntrance.placeholderRows(400))
    }

    @Test
    fun `there is never no placeholder row`() {
        assertEquals(1, PageEntrance.placeholderRows(0))
        assertEquals(1, PageEntrance.placeholderRows(-2))
        assertEquals(PageEntrance.PLACEHOLDER_ROWS_UNKNOWN, PageEntrance.placeholderRows(null))
        assertTrue(PageEntrance.PLACEHOLDER_ROWS_UNKNOWN in 1 until PageEntrance.PLACEHOLDER_ROWS_MAX)
    }

    @Test
    fun `a subtitle's song count is read`() {
        assertEquals(12, PageEntrance.trackCountIn("Playlist • 12 songs"))
        assertEquals(1, PageEntrance.trackCountIn("Some Artist · 1 song"))
        assertEquals(1204, PageEntrance.trackCountIn("Playlist • 1,204 tracks • 80 hours"))
        assertEquals(1, PageEntrance.trackCountIn("Single • Some Artist • 2024"))
    }

    @Test
    fun `a subtitle without a count gives none`() {
        assertEquals(null, PageEntrance.trackCountIn(""))
        assertEquals(null, PageEntrance.trackCountIn("Album • Some Artist • 2019"))
        assertEquals(null, PageEntrance.trackCountIn("Single Ladies • 2008"))
    }

    @Test
    fun `a row swapped for another mid entrance carries on rather than starting over`() {
        val run = PageEntrance.Run(startedAtMs = 1_000)
        run.startOf(slot = 0, nowMs = 1_010)
        val first = run.startOf(slot = 2, nowMs = 1_010)
        assertEquals(1_010 + PageEntrance.delayMs(2), first)
        // The Spotify row gives way to its match while still coming in.
        assertEquals(first, run.startOf(slot = 2, nowMs = 1_300))
        assertTrue(PageEntrance.fraction(first, 1_300) > 0f)
    }

    @Test
    fun `a row swapped after the entrance is over is simply there`() {
        val run = PageEntrance.Run(startedAtMs = 1_000)
        val first = run.startOf(slot = 0, nowMs = 1_000)
        val later = run.startOf(slot = 0, nowMs = 1_000 + PageEntrance.totalMs + 5_000)
        assertEquals(first, later)
        assertEquals(1f, PageEntrance.fraction(later, 1_000 + PageEntrance.totalMs + 5_000))
    }

    @Test
    fun `a slot first drawn after the entrance has no part in it`() {
        val run = PageEntrance.Run(startedAtMs = 1_000)
        assertEquals(null, run.startOf(slot = 3, nowMs = 1_000 + PageEntrance.JOIN_WINDOW_MS))
        run.startOf(slot = 0, nowMs = 1_000)
        assertEquals(null, run.startOf(slot = PageEntrance.SLOTS, nowMs = 1_000))
        assertEquals(1f, PageEntrance.fraction(null, 1_000))
    }

    @Test
    fun `the turns are counted from the first thing drawn`() {
        // The page was scrolled down when its songs landed: row 5 is the top one in view.
        val run = PageEntrance.Run(startedAtMs = 1_000)
        assertEquals(1_000L, run.startOf(slot = 5, nowMs = 1_000))
        assertEquals(1_000 + PageEntrance.delayMs(2), run.startOf(slot = 7, nowMs = 1_000))
        // A screenful on from there, not from the top of the page.
        assertEquals(
            1_000 + PageEntrance.delayMs(PageEntrance.SLOTS - 1),
            run.startOf(slot = 5 + PageEntrance.SLOTS - 1, nowMs = 1_000),
        )
        assertEquals(null, run.startOf(slot = 5 + PageEntrance.SLOTS, nowMs = 1_000))
        // One above the first has no wait.
        assertEquals(1_020L, run.startOf(slot = 3, nowMs = 1_020))
    }

    @Test
    fun `a scroll sets off what was waiting and leaves the rest simply there`() {
        val run = PageEntrance.Run(startedAtMs = 1_000)
        val first = run.startOf(slot = 0, nowMs = 1_000)!!
        val waiting = run.startOf(slot = 6, nowMs = 1_000)!!
        assertEquals(waiting, run.setOff(waiting))
        run.hurry(nowMs = 1_100)
        // Already on its way: as it was.
        assertEquals(first, run.setOff(first))
        // Still to come: now.
        assertEquals(1_100L, run.setOff(waiting))
        // Scrolled into view: no part in the entrance.
        assertEquals(null, run.startOf(slot = 8, nowMs = 1_120))
        // A row swapped for another keeps its slot's time.
        assertEquals(waiting, run.startOf(slot = 6, nowMs = 1_120))
        // Only the first scroll counts.
        run.hurry(nowMs = 1_400)
        assertEquals(1_100L, run.setOff(waiting))
    }

    @Test
    fun `a slot waits its turn then runs its length`() {
        val run = PageEntrance.Run(1_000)
        run.startOf(slot = 0, nowMs = 1_000)
        val start = run.startOf(slot = 4, nowMs = 1_000)!!
        assertEquals(1_000 + PageEntrance.delayMs(4), start)
        assertEquals(0f, PageEntrance.fraction(start, 1_000))
        assertEquals(0.5f, PageEntrance.fraction(start, start + PageEntrance.DURATION_MS / 2))
        assertEquals(1f, PageEntrance.fraction(start, start + PageEntrance.DURATION_MS))
    }
}
