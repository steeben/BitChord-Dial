package com.music.bitchord

import com.music.bitchord.ui.screens.CLOSE_DIP
import com.music.bitchord.ui.screens.closeCurve
import com.music.bitchord.ui.screens.launchEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a launched page moves back to its tile, and what of it shows when the tile is partly out of view. */
class DialLaunchMotionTest {
    private val steps = (0..1000).map { it / 1000f }

    @Test
    fun theCloseStartsAtFullSizeAndEndsOnTheTile() {
        assertEquals(0f, closeCurve(0f), 0f)
        assertEquals(1f, closeCurve(1f), 0f)
        // No jump into the last frame.
        assertEquals(1f, closeCurve(0.999f), 0.002f)
    }

    @Test
    fun theCloseStartsGently() {
        // A tenth of the way through, well under a tenth of the way there, and still gathering speed.
        assertTrue(closeCurve(0.1f) < 0.25f)
        assertTrue(closeCurve(0.1f) - closeCurve(0.05f) > closeCurve(0.05f) - closeCurve(0f))
    }

    @Test
    fun theCloseDipsALittleUnderTheTileOnceAndSettles() {
        val deepest = steps.maxOf { closeCurve(it) }
        assertEquals(1f + CLOSE_DIP, deepest, 0.01f)
        assertTrue("a subtle bounce", deepest - 1f in 0.03f..0.06f)
        // Back out to the tile's size, and not past it the other way by anything that shows.
        val after = steps.dropWhile { closeCurve(it) < deepest }
        assertTrue(after.all { closeCurve(it) > 0.995f })
    }

    @Test
    fun theCloseOnlyGoesOneWayUntilItsBounce() {
        // What a back swipe's seek relies on: one place in the run for each size on the way down.
        val down = steps.takeWhile { closeCurve(it) < 1f }
        assertTrue(down.zipWithNext().all { (a, b) -> closeCurve(b) >= closeCurve(a) })
        assertTrue(down.size > 400)
    }

    @Test
    fun aPagesEdgeRunsFromItsTilesToItsOwn() {
        assertEquals(100f, launchEdge(tile = 100f, page = 0f, centre = 200f, progress = 0f), 0f)
        assertEquals(0f, launchEdge(tile = 100f, page = 0f, centre = 200f, progress = 1f), 0f)
        assertEquals(50f, launchEdge(tile = 100f, page = 0f, centre = 200f, progress = 0.5f), 0f)
        // In the bounce, drawn in towards the tile's centre.
        assertEquals(105f, launchEdge(tile = 100f, page = 0f, centre = 200f, progress = -0.05f), 0.001f)
    }

    @Test
    fun aCardHalfOffTheScreenKeepsItsProportionsAndIsCutAtTheScreensEdge() {
        // A 300 wide card with its left half off a 1080 wide page: all of it is closed onto...
        val left = -150f
        val right = 150f
        val centre = 0f
        for (p in listOf(1f, 0.6f, 0.2f, 0f)) {
            val width = launchEdge(right, 1080f, centre, p) - launchEdge(left, 0f, centre, p)
            assertEquals(300f + (1080f - 300f) * p, width, 0.001f)
            // ...and what shows of the page starts at the screen's edge, where the card is cut.
            assertEquals(0f, launchEdge(tile = 0f, page = 0f, centre = centre, progress = p), 0f)
        }
        // At rest the page is as wide as the card, not as the half of it in view.
        assertEquals(300f, launchEdge(right, 1080f, centre, 0f) - launchEdge(left, 0f, centre, 0f), 0f)
    }
}
