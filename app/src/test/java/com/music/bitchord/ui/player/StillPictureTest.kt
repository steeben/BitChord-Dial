package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StillPictureTest {
    private val size = StillPicture.WIDTH * StillPicture.HEIGHT

    private fun gradient(shift: Int = 0) = IntArray(size) { i ->
        val v = ((i * 255 / size) + shift).coerceIn(0, 255)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    @Test
    fun sameFrameIsStill() {
        val a = StillPicture.luma(gradient())
        assertEquals(0f, StillPicture.changedFraction(a, a.copyOf()))
        assertFalse(StillPicture.moved(a, a.copyOf()))
    }

    @Test
    fun compressionNoiseIsStill() {
        val a = StillPicture.luma(gradient())
        val b = StillPicture.luma(gradient(shift = 3))
        assertFalse(StillPicture.moved(a, b))
    }

    @Test
    fun aCutMoves() {
        val a = StillPicture.luma(gradient())
        val b = StillPicture.luma(IntArray(size) { 0xFF000000.toInt() })
        assertTrue(StillPicture.moved(a, b))
    }

    @Test
    fun aLyricLineChangingMoves() {
        val base = gradient()
        val a = StillPicture.luma(base)
        // One line of text, a couple of percent of the frame, turns white.
        val withText = base.copyOf().also { px ->
            for (x in 10 until 38) px[20 * StillPicture.WIDTH + x] = -1
        }
        assertTrue(StillPicture.moved(a, StillPicture.luma(withText)))
    }

    @Test
    fun mismatchedSamplesCountAsMoved() {
        assertTrue(StillPicture.moved(ByteArray(4), ByteArray(5)))
    }
}
