package com.music.bitchord

import com.music.bitchord.ui.screens.dialSwipeTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a swipe between the Mixes Dial (page 0) and the speed dial (page 1) lands. */
class DialSwipeTargetTest {

    private val minFling = 1000f

    @Test
    fun aFlickTurnsThePageHoweverLittleWasDragged() {
        // From the speed dial, a finger flicked right drags the mixes in a little: on to the mixes.
        assertEquals(0, dialSwipeTarget(position = 0.9f, velocity = 2000f, minFling = minFling, pageCount = 2))
        // From the mixes, a finger flicked left: on to the speed dial.
        assertEquals(1, dialSwipeTarget(position = 0.1f, velocity = -2000f, minFling = minFling, pageCount = 2))
    }

    @Test
    fun aSlowReleaseSettlesOnTheNearerPage() {
        assertEquals(1, dialSwipeTarget(position = 0.9f, velocity = 100f, minFling = minFling, pageCount = 2))
        assertEquals(0, dialSwipeTarget(position = 0.4f, velocity = -100f, minFling = minFling, pageCount = 2))
        assertEquals(1, dialSwipeTarget(position = 0.6f, velocity = 0f, minFling = minFling, pageCount = 2))
    }

    @Test
    fun aFlickBackTheWayItCameReturnsToThePageItLeft() {
        assertEquals(1, dialSwipeTarget(position = 0.3f, velocity = -2000f, minFling = minFling, pageCount = 2))
        assertEquals(0, dialSwipeTarget(position = 0.7f, velocity = 2000f, minFling = minFling, pageCount = 2))
    }

    @Test
    fun neverPastTheEnds() {
        assertEquals(0, dialSwipeTarget(position = -0.2f, velocity = 2000f, minFling = minFling, pageCount = 2))
        assertEquals(1, dialSwipeTarget(position = 1.2f, velocity = -2000f, minFling = minFling, pageCount = 2))
    }
}
