package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverEdgeTest {
    @Test
    fun wholeLineOnTheCover() {
        assertEquals(1f, coverEdgeAt(0f), 0.0001f)
        // No step left to see as the artwork hands back to the cover.
        assertTrue(coverEdgeAt(0.005f) > 0.98f)
    }

    @Test
    fun noLineOnTheOpenPlayer() {
        assertEquals(0f, coverEdgeAt(COVER_EDGE_UNTIL), 0.0001f)
        assertEquals(0f, coverEdgeAt(1f), 0.0001f)
    }

    @Test
    fun lineOnlyFadesOnTheWayUp() {
        var last = 1f
        for (i in 0..100) {
            val edge = coverEdgeAt(i / 100f)
            assertTrue(edge <= last)
            last = edge
        }
    }
}
