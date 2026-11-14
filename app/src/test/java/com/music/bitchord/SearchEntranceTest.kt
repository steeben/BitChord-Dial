package com.music.bitchord

import com.music.bitchord.ui.screens.SearchEntrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the search page brings in the rows of a list that has just arrived. */
class SearchEntranceTest {

    @Test
    fun `rows of one arrival set off one after another from the top down`() {
        val pace = SearchEntrance.Pace.ARRIVE
        val run = SearchEntrance.Run(startedAtMs = 1_000, pace = pace)
        val starts = (0 until 6).map { run.join(nowMs = 1_000) }
        assertEquals(1_000L, starts.first())
        starts.filterNotNull().zipWithNext().forEach { (a, b) -> assertEquals(pace.stepMs, b - a) }
    }

    @Test
    fun `a row drawn a frame later still follows the rows before it`() {
        val pace = SearchEntrance.Pace.ARRIVE
        val run = SearchEntrance.Run(startedAtMs = 1_000, pace = pace)
        run.join(nowMs = 1_000)
        run.join(nowMs = 1_000)
        assertEquals(1_000 + 2 * pace.stepMs, run.join(nowMs = 1_016))
    }

    @Test
    fun `a row never sets off before it is drawn`() {
        val run = SearchEntrance.Run(startedAtMs = 1_000, pace = SearchEntrance.Pace.TYPING)
        assertEquals(1_300L, run.join(nowMs = 1_300))
    }

    @Test
    fun `only the first screenful of rows plays`() {
        val run = SearchEntrance.Run(startedAtMs = 0, pace = SearchEntrance.Pace.ARRIVE)
        repeat(SearchEntrance.SLOTS) { assertNotNull(run.join(nowMs = 0)) }
        assertNull(run.join(nowMs = 0))
    }

    @Test
    fun `a row drawn later by a scroll is simply there`() {
        val run = SearchEntrance.Run(startedAtMs = 1_000, pace = SearchEntrance.Pace.ARRIVE)
        assertNull(run.join(nowMs = 1_000 + SearchEntrance.JOIN_WINDOW_MS))
        assertNull(run.join(nowMs = 999))
    }

    @Test
    fun `rows that change with each key come in faster than a list that was asked for`() {
        val typing = SearchEntrance.Pace.TYPING
        val arrive = SearchEntrance.Pace.ARRIVE
        assertTrue(typing.stepMs < arrive.stepMs)
        assertTrue(typing.durationMs < arrive.durationMs)
        assertTrue(typing.riseDp < arrive.riseDp)
    }

    @Test
    fun `a row is part of the way in between its start and its end`() {
        assertEquals(0f, SearchEntrance.fraction(startAtMs = 1_000, durationMs = 400, nowMs = 900), 0f)
        assertEquals(0f, SearchEntrance.fraction(startAtMs = 1_000, durationMs = 400, nowMs = 1_000), 0f)
        assertEquals(0.5f, SearchEntrance.fraction(startAtMs = 1_000, durationMs = 400, nowMs = 1_200), 0.001f)
        assertEquals(1f, SearchEntrance.fraction(startAtMs = 1_000, durationMs = 400, nowMs = 5_000), 0f)
    }

    @Test
    fun `a row keeps its key when the rows above it change`() {
        val before = SearchEntrance.rowKeys("library_", listOf("a", "b", "c"))
        val after = SearchEntrance.rowKeys("library_", listOf("x", "b", "c"))
        assertEquals(before[1], after[1])
        assertEquals(before[2], after[2])
    }

    @Test
    fun `two rows of the same song have different keys`() {
        val keys = SearchEntrance.rowKeys("addon_", listOf("a", "b", "a", "a"))
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("addon_a", keys.first())
    }
}
