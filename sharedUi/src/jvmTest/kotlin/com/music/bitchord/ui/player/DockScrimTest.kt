package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockScrimTest {
    @Test
    fun coverTakesNoScrimForMostOfTheTrip() {
        assertEquals(0f, dockScrimShare(0f), 0.0001f)
        assertEquals(0f, dockScrimShare(0.3f), 0.0001f)
        assertEquals(0f, dockScrimShare(DOCK_SCRIM_FROM), 0.0001f)
    }

    @Test
    fun coverHasTheWholeScrimAsItLands() {
        assertEquals(1f, dockScrimShare(1f), 0.0001f)
        // No step left to see in the last of the way.
        assertTrue(dockScrimShare(0.99f) > 0.99f)
    }

    @Test
    fun shareOnlyGrowsOnTheWayUp() {
        var last = 0f
        for (i in 0..100) {
            val share = dockScrimShare(i / 100f)
            assertTrue(share >= last)
            last = share
        }
    }

    @Test
    fun overAndUnderAddUpToTheOneScrim() {
        for (alpha in listOf(0.1f, 0.35f, 0.6f, 1f)) {
            for (share in listOf(0f, 0.25f, 0.5f, 0.9f, 1f)) {
                val over = alpha * share
                val under = scrimUnderCover(alpha, over)
                val both = 1f - (1f - over) * (1f - under)
                assertEquals(alpha, both, 0.0001f)
            }
        }
    }

    @Test
    fun nothingUnderOnceTheCoverHasItAll() {
        assertEquals(0f, scrimUnderCover(0.5f, 0.5f), 0.0001f)
        assertEquals(0.5f, scrimUnderCover(0.5f, 0f), 0.0001f)
    }
}
