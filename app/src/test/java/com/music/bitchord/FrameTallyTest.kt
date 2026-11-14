package com.music.bitchord

import com.music.bitchord.ui.performance.FrameTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameTallyTest {
    private val ms = 1_000_000L
    private val budget = 8 * ms

    /** A frame of [total] ms, split [wait] / [ui] / [render] / [gpu]. */
    private fun FrameTally.frame(total: Long, wait: Long, ui: Long, render: Long, gpu: Long, given: Long = budget) =
        add(total * ms, given, wait * ms, ui * ms, render * ms, gpu * ms)

    @Test
    fun `nothing is written for no frames`() {
        assertNull(FrameTally().summary())
    }

    @Test
    fun `frames inside the budget are not late`() {
        val tally = FrameTally()
        repeat(10) { tally.frame(6, 0, 3, 1, 1) }
        tally.frame(8, 0, 4, 2, 2)
        assertEquals(11, tally.frames)
        assertEquals(0, tally.late)
        assertEquals("11 frames, worst 8.0ms, budget 8.0ms, none late", tally.summary())
    }

    @Test
    fun `late frames are counted and their time kept by where it went`() {
        val tally = FrameTally()
        repeat(8) { tally.frame(5, 0, 2, 1, 1) }
        tally.frame(12, 0, 8, 2, 2)
        tally.frame(40, 10, 20, 6, 4)
        assertEquals(10, tally.frames)
        assertEquals(2, tally.late)
        assertEquals(1, tally.slow)
        assertEquals(0, tally.frozen)
        assertEquals(
            "10 frames, worst 40.0ms, budget 8.0ms, 2 late (20.0%), 1 over twice the budget, 0 frozen; " +
                "a late frame took on average 5.0ms waiting for the app thread, 14.0ms on the app thread, " +
                "4.0ms on the render thread, 3.0ms on the GPU",
            tally.summary(),
        )
    }

    @Test
    fun `the rate is the frames over the time from the first to the last`() {
        val tally = FrameTally()
        repeat(121) { tally.frame(6, 0, 3, 1, 1) }
        assertEquals(
            "121 frames (120 a second), worst 6.0ms, budget 8.0ms, none late",
            tally.summary(spanNanos = 1_000 * ms),
        )
        // Too short a stretch to have a rate.
        assertEquals("121 frames, worst 6.0ms, budget 8.0ms, none late", tally.summary(spanNanos = 100 * ms))
    }

    @Test
    fun `the budget shown is the least a frame was given`() {
        val tally = FrameTally()
        tally.frame(6, 0, 3, 1, 1, given = 30 * ms)
        tally.frame(6, 0, 3, 1, 1, given = 8 * ms)
        tally.frame(6, 0, 3, 1, 1, given = 22 * ms)
        assertEquals("3 frames, worst 6.0ms, budget 8.0ms, none late", tally.summary())
    }

    @Test
    fun `a frame of most of a second is frozen`() {
        val tally = FrameTally()
        tally.frame(900, 0, 880, 10, 10)
        assertEquals(1, tally.frozen)
        assertEquals(1, tally.slow)
    }

    @Test
    fun `a frame with no budget known is counted and never late`() {
        val tally = FrameTally()
        tally.frame(50, 0, 40, 5, 5, given = 0L)
        assertEquals(1, tally.frames)
        assertEquals(0, tally.late)
    }

    @Test
    fun `a reset starts the count again`() {
        val tally = FrameTally()
        tally.frame(40, 0, 30, 6, 4)
        tally.reset()
        assertNull(tally.summary())
        tally.frame(5, 0, 2, 1, 1, given = 16 * ms)
        assertEquals("1 frames, worst 5.0ms, budget 16.0ms, none late", tally.summary())
    }
}
