package com.music.bitchord

import com.music.bitchord.ui.performance.TouchStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchStartTest {
    private val ms = 1_000_000L
    private val down = 5_000 * ms

    /** A frame at [at] ms after the touch that took [total] ms of a 10 ms budget, all of it composition. */
    private fun TouchStart.frame(at: Long, total: Long): Boolean = frame(
        vsyncNanos = down + at * ms,
        totalNanos = total * ms,
        budgetNanos = 10 * ms,
        inputNanos = 0L,
        animationNanos = total * ms,
        layoutNanos = 0L,
        drawNanos = 0L,
        renderNanos = 0L,
        gpuNanos = 0L,
    )

    @Test
    fun `nothing is written with no touch`() {
        val touch = TouchStart()
        assertFalse(touch.frame(0, 4))
        assertNull(touch.summary("mode #5 165.0Hz"))
    }

    @Test
    fun `a tap writes nothing`() {
        val touch = TouchStart()
        touch.down(down, "Home")
        for (at in 6..60L step 6) touch.frame(at, 4)
        assertNull(touch.summary("mode #5 165.0Hz"))
    }

    @Test
    fun `a swipe's first frames are written one by one`() {
        val touch = TouchStart()
        // The last frame before the touch, a long time before it.
        touch.frame(-900, 4)
        touch.down(down, "Home")
        touch.displayAtDown("mode #1 120.0Hz")
        touch.frame(8, 4)
        touch.move(down + 14 * ms, samples = 2)
        touch.frame(16, 4)
        touch.move(down + 20 * ms, samples = 3)
        // Slow, and so the next one comes a beat late.
        touch.frame(22, 12)
        touch.frame(34, 4)
        assertFalse(touch.frame(40, 4))
        // The first frame past the touch's 400 ms.
        assertTrue(touch.frame(406, 4))
        assertEquals(
            "touch on Home: display mode #1 120.0Hz at the touch, mode #5 165.0Hz after 400ms; " +
                "5 touch samples in 2 events, the first 14.0ms after the touch; " +
                "5 frames in 400ms, 1 late, 1 after a missed beat (beat 6.00ms); " +
                "a late frame took on average 12.0ms on the app thread " +
                "(input 0.0, animation and composition 12.0, layout 0.0, draw 0.0), " +
                "0.0ms on the render thread, 0.0ms on the GPU; " +
                "frames as gap/time in ms, (app) the app thread's part of it from 5ms, * late, " +
                "| the finger's first move, [] what the app did in the frame after it: " +
                "rest/4.0 | 8.0/4.0 6.0/12.0(app 12.0)* 12.0/4.0 6.0/4.0",
            touch.summary("mode #5 165.0Hz"),
        )
        // Written once.
        assertNull(touch.summary("mode #5 165.0Hz"))
    }

    @Test
    fun `a display that did not change is named once`() {
        val touch = TouchStart()
        touch.down(down, "Library")
        touch.displayAtDown("mode #5 165.0Hz")
        touch.move(down + 10 * ms, samples = 1)
        for (at in 6..30L step 6) touch.frame(at, 4)
        assertEquals(
            "touch on Library: display mode #5 165.0Hz at the touch and after 400ms; " +
                "1 touch samples in 1 events, the first 10.0ms after the touch; " +
                "5 frames in 400ms, 0 late, 0 after a missed beat (beat 6.00ms); " +
                "frames as gap/time in ms, (app) the app thread's part of it from 5ms, * late, " +
                "| the finger's first move, [] what the app did in the frame after it: " +
                "-/4.0 | 6.0/4.0 6.0/4.0 6.0/4.0 6.0/4.0",
            touch.summary("mode #5 165.0Hz"),
        )
    }

    @Test
    fun `what the app did is shown at the frame it fell in`() {
        val touch = TouchStart()
        touch.down(down, "Home")
        touch.move(down + 10 * ms, samples = 1)
        touch.frame(6, 4)
        touch.frame(12, 4)
        // During the frame that began at 18 ms.
        touch.frame(18, 12)
        touch.mark(down + 21 * ms, "bar small")
        touch.frame(30, 12)
        touch.frame(42, 4)
        // Not in this touch's frames.
        touch.mark(down + 900 * ms, "bar full")
        assertEquals(
            "touch on Home: display mode #5 165.0Hz after 400ms; " +
                "1 touch samples in 1 events, the first 10.0ms after the touch; " +
                "5 frames in 400ms, 2 late, 2 after a missed beat (beat 6.00ms); " +
                "a late frame took on average 12.0ms on the app thread " +
                "(input 0.0, animation and composition 12.0, layout 0.0, draw 0.0), " +
                "0.0ms on the render thread, 0.0ms on the GPU; " +
                "frames as gap/time in ms, (app) the app thread's part of it from 5ms, * late, " +
                "| the finger's first move, [] what the app did in the frame after it: " +
                "-/4.0 | 6.0/4.0 [bar small] 6.0/12.0(app 12.0)* 12.0/12.0(app 12.0)* 12.0/4.0",
            touch.summary("mode #5 165.0Hz"),
        )
    }

    @Test
    fun `a move long after the touch is not counted`() {
        val touch = TouchStart()
        touch.down(down, "Home")
        for (at in 6..30L step 6) touch.frame(at, 4)
        touch.move(down + 900 * ms, samples = 4)
        assertNull(touch.summary("mode #5 165.0Hz"))
    }

    @Test
    fun `a new touch starts from nothing`() {
        val touch = TouchStart()
        touch.down(down, "Home")
        touch.move(down + 10 * ms, samples = 1)
        for (at in 6..30L step 6) touch.frame(at, 12)
        touch.down(down + 100 * ms, "Home")
        touch.move(down + 110 * ms, samples = 1)
        for (at in 106..130L step 6) touch.frame(at, 4)
        assertEquals(
            "touch on Home: display mode #5 165.0Hz after 400ms; " +
                "1 touch samples in 1 events, the first 10.0ms after the touch; " +
                "5 frames in 400ms, 0 late, 0 after a missed beat (beat 6.00ms); " +
                "frames as gap/time in ms, (app) the app thread's part of it from 5ms, * late, " +
                "| the finger's first move, [] what the app did in the frame after it: " +
                "76.0/4.0 | 6.0/4.0 6.0/4.0 6.0/4.0 6.0/4.0",
            touch.summary("mode #5 165.0Hz"),
        )
    }
}
