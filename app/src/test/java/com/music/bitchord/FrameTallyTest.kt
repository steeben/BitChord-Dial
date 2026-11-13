package com.music.bitchord

import com.music.bitchord.ui.performance.FrameTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameTallyTest {
    private val ms = 1_000_000L
    private val budget = 8 * ms

    @Test
    fun `nothing is written for no frames`() {
        assertNull(FrameTally().summary())
    }

    @Test
    fun `frames inside the budget are not late`() {
        val tally = FrameTally()
        repeat(10) { tally.add(6 * ms, budget, 3 * ms, 1 * ms, 1 * ms) }
        tally.add(budget, budget, 4 * ms, 2 * ms, 2 * ms)
        assertEquals(11, tally.frames)
        assertEquals(0, tally.late)
        assertEquals("11 frames, worst 8.0ms, budget 8.0ms, none late", tally.summary())
    }

    @Test
    fun `late frames are counted and their time kept by where it went`() {
        val tally = FrameTally()
        repeat(8) { tally.add(5 * ms, budget, 2 * ms, 1 * ms, 1 * ms) }
        tally.add(12 * ms, budget, 8 * ms, 2 * ms, 2 * ms)
        tally.add(40 * ms, budget, 30 * ms, 6 * ms, 4 * ms)
        assertEquals(10, tally.frames)
        assertEquals(2, tally.late)
        assertEquals(1, tally.slow)
        assertEquals(0, tally.frozen)
        assertEquals(
            "10 frames, worst 40.0ms, budget 8.0ms, 2 late (20.0%), 1 over twice the budget, 0 frozen; " +
                "a late frame took on average 19.0ms on the app thread, 4.0ms on the render thread, 3.0ms on the GPU",
            tally.summary(),
        )
    }

    @Test
    fun `a frame of most of a second is frozen`() {
        val tally = FrameTally()
        tally.add(900 * ms, budget, 880 * ms, 10 * ms, 10 * ms)
        assertEquals(1, tally.frozen)
        assertEquals(1, tally.slow)
    }

    @Test
    fun `a frame with no budget known is counted and never late`() {
        val tally = FrameTally()
        tally.add(50 * ms, 0L, 40 * ms, 5 * ms, 5 * ms)
        assertEquals(1, tally.frames)
        assertEquals(0, tally.late)
    }

    @Test
    fun `a reset starts the count again`() {
        val tally = FrameTally()
        tally.add(40 * ms, budget, 30 * ms, 6 * ms, 4 * ms)
        tally.reset()
        assertNull(tally.summary())
        tally.add(5 * ms, budget, 2 * ms, 1 * ms, 1 * ms)
        assertEquals("1 frames, worst 5.0ms, budget 8.0ms, none late", tally.summary())
    }
}
