package com.music.bitchord.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextCanvasLookaheadTest {
    @Test
    fun dueOnlyInTheLastFifteenSeconds() {
        assertFalse(nextCanvasDue(positionMs = 0L, durationMs = 200_000L))
        assertFalse(nextCanvasDue(positionMs = 184_999L, durationMs = 200_000L))
        assertTrue(nextCanvasDue(positionMs = 185_000L, durationMs = 200_000L))
        assertTrue(nextCanvasDue(positionMs = 199_000L, durationMs = 200_000L))
    }

    @Test
    fun aShortTrackIsDueFromItsStart() {
        assertTrue(nextCanvasDue(positionMs = 0L, durationMs = 12_000L))
    }

    @Test
    fun notDueWithoutADuration() {
        // Between tracks the duration reads zero for a moment; that is not "near the end".
        assertFalse(nextCanvasDue(positionMs = 0L, durationMs = 0L))
        assertFalse(nextCanvasDue(positionMs = 5_000L, durationMs = -1L))
    }
}
