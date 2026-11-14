package com.music.bitchord

import com.music.bitchord.ui.performance.FramePacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramePacingTest {
    /** One refresh of a 165 Hz display, as the phone in the logs reports it. */
    private val beat = 6_041_700L
    private val ms = 1_000_000L

    /** Frames [gaps] apart, in refreshes of the display, each [total] ms long. */
    private fun FramePacing.frames(vararg gaps: Int, from: Long = 1_000 * ms, total: Long = 4, each: Long = beat): Long {
        var at = from
        for (gap in gaps) {
            at += gap * each
            add(at, total * ms)
        }
        return at
    }

    private fun steady(count: Int) = IntArray(count) { 1 }

    @Test
    fun `nothing is written for a stretch too short to have a beat`() {
        assertNull(FramePacing().summary())
        val pacing = FramePacing()
        pacing.frames(*steady(20))
        // Twenty frames have nineteen gaps.
        assertNull(pacing.summary())
    }

    @Test
    fun `frames one refresh apart are all in step`() {
        val pacing = FramePacing()
        pacing.frames(*steady(101))
        assertEquals(
            "beat 6.04ms: 100 frames in step, 0 after one missed beat, 0 after two, 0 after a longer gap; " +
                "frame time median 4.0ms, 95% within 4.0ms, 99% within 4.0ms",
            pacing.summary(),
        )
    }

    @Test
    fun `a refresh with no frame is one missed beat`() {
        val pacing = FramePacing()
        pacing.frames(*steady(41), 2, *steady(20), 3, *steady(20), 2, *steady(15))
        assertEquals(
            "beat 6.04ms: 95 frames in step, 2 after one missed beat, 1 after two, 0 after a longer gap; " +
                "frame time median 4.0ms, 95% within 4.0ms, 99% within 4.0ms",
            pacing.summary(),
        )
    }

    @Test
    fun `a clip on a page that scrolled does not set the beat`() {
        val pacing = FramePacing()
        // A short scroll, then a clip at 30 a second for longer: 5 and 6
        // refreshes of the display between its frames, in turn.
        val at = pacing.frames(*steady(31))
        pacing.frames(*IntArray(60) { if (it % 2 == 0) 5 else 6 }, from = at)
        assertEquals(
            "beat 6.04ms: 30 frames in step, 0 after one missed beat, 0 after two, 60 after a longer gap; " +
                "frame time median 4.0ms, 95% within 4.0ms, 99% within 4.0ms",
            pacing.summary(),
        )
    }

    @Test
    fun `a display that changed its rate shows as frames off the beat`() {
        val pacing = FramePacing()
        val at = pacing.frames(*steady(61))
        // 120 Hz.
        pacing.frames(*steady(30), from = at, each = 8_333_333L)
        assertEquals(
            "beat 6.04ms: 60 frames in step, 0 after one missed beat, 0 after two, 0 after a longer gap, " +
                "30 off the beat (most at 8.33ms); " +
                "frame time median 4.0ms, 95% within 4.0ms, 99% within 4.0ms",
            pacing.summary(),
        )
    }

    @Test
    fun `the screen at rest between two frames is not a gap`() {
        val pacing = FramePacing()
        val at = pacing.frames(*steady(31))
        // Two seconds later.
        pacing.frames(*steady(30), from = at + 2_000 * ms)
        assertEquals(
            "beat 6.04ms: 59 frames in step, 0 after one missed beat, 0 after two, 0 after a longer gap; " +
                "frame time median 4.0ms, 95% within 4.0ms, 99% within 4.0ms",
            pacing.summary(),
        )
    }

    @Test
    fun `frame times are given as the median and the slow end`() {
        val pacing = FramePacing()
        var at = 1_000 * ms
        // 1 ms to 100 ms, one of each.
        for (total in 1..100L) {
            at += beat
            pacing.add(at, total * ms)
        }
        assertEquals(
            "beat 6.04ms: 99 frames in step, 0 after one missed beat, 0 after two, 0 after a longer gap; " +
                "frame time median 50.0ms, 95% within 95.0ms, 99% within 99.0ms",
            pacing.summary(),
        )
    }

    @Test
    fun `a new stretch starts from nothing but keeps the last frame`() {
        val pacing = FramePacing()
        val at = pacing.frames(*steady(41))
        pacing.reset()
        assertNull(pacing.summary())
        // The first frame of the next stretch is one refresh after the last
        // of this one, and that gap is the next stretch's.
        pacing.frames(*steady(40), from = at)
        assertEquals(
            "beat 6.04ms: 40 frames in step, 0 after one missed beat, 0 after two, 0 after a longer gap; " +
                "frame time median 4.0ms, 95% within 4.0ms, 99% within 4.0ms",
            pacing.summary(),
        )
    }
}
