package com.music.bitchord

import com.music.bitchord.ui.screens.FrameBeat
import com.music.bitchord.ui.screens.evenValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EvenFlingTest {
    private val ms = 1_000_000L

    /** One refresh of a 165 Hz display, as the phone in the logs reports it. */
    private val beat = 6_041_700L

    /** A curve that can only be read at whole milliseconds: 10 px for each. */
    private val straight: (Long) -> Float = { it * 10f }

    @Test
    fun `at a whole millisecond the value is the curve's own`() {
        assertEquals(0f, evenValue(0L, straight), 0f)
        assertEquals(60f, evenValue(6 * ms, straight), 0f)
    }

    @Test
    fun `between two milliseconds the value is between theirs`() {
        assertEquals(65f, evenValue(6 * ms + ms / 2, straight), 0.001f)
        assertEquals(60.417f, evenValue(beat, straight), 0.001f)
        // A curve that bends: 0, 1, 4, 9 at 0, 1, 2, 3 ms.
        val bent: (Long) -> Float = { (it * it).toFloat() }
        assertEquals(6.5f, evenValue(2 * ms + ms / 2, bent), 0.001f)
    }

    @Test
    fun `frames of a 165 Hz display all move the same distance`() {
        // What Compose does: the time cut to whole milliseconds.
        val cut = (1..48).map { straight(it * beat / ms) - straight((it - 1) * beat / ms) }
        assertEquals(46, cut.count { it == 60f })
        assertEquals(2, cut.count { it == 70f })

        val even = (1..48).map { evenValue(it * beat, straight) - evenValue((it - 1) * beat, straight) }
        assertTrue(even.all { abs(it - 60.417f) < 0.01f })
    }

    @Test
    fun `the beat is the shortest of the last four frames`() {
        val frames = FrameBeat()
        // 120 Hz until a frame is seen.
        assertEquals(8_333_333L, frames.nanos)
        frames.seen(beat)
        assertEquals(beat, frames.nanos)
        // A frame that came a refresh late is not a slower display.
        frames.seen(2 * beat)
        frames.seen(beat + 300)
        assertEquals(beat, frames.nanos)
    }

    @Test
    fun `a display that slowed down is believed after four frames`() {
        val frames = FrameBeat()
        repeat(4) { frames.seen(beat) }
        repeat(3) { frames.seen(8_333_333L) }
        assertEquals(beat, frames.nanos)
        frames.seen(8_333_333L)
        assertEquals(8_333_333L, frames.nanos)
    }

    @Test
    fun `a gap that is no frame time is passed over`() {
        val frames = FrameBeat()
        frames.seen(beat)
        // Two frames in the same refresh, and a screen that stood still.
        frames.seen(0L)
        frames.seen(900 * ms)
        assertEquals(beat, frames.nanos)
    }
}
