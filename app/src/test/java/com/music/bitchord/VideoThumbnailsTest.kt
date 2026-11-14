package com.music.bitchord

import com.music.bitchord.data.VideoThumbnails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoThumbnailsTest {

    private val listed = "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg?sqp=-oaymwEWCJADEOEBIAQqCghqEJQEGHgg6AJIWg&rs=AMzJL3kabc"
    private val maxRes = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg"
    private val sd = "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg"

    @Test
    fun `a row keeps the listed picture`() {
        assertEquals(emptyList<String>(), VideoThumbnails.larger(listed, 160))
        assertEquals(emptyList<String>(), VideoThumbnails.larger(listed, VideoThumbnails.LISTED_ENOUGH_PX))
    }

    @Test
    fun `a card asks for the 640 file only`() {
        assertEquals(listOf(sd), VideoThumbnails.larger(listed, 480))
        assertEquals(listOf(sd), VideoThumbnails.larger(listed, VideoThumbnails.SD_ENOUGH_PX))
    }

    @Test
    fun `the player asks for the 1280 file first, then the 640 file`() {
        assertEquals(listOf(maxRes, sd), VideoThumbnails.larger(listed, 1200))
    }

    @Test
    fun `an unknown size asks for nothing`() {
        assertEquals(emptyList<String>(), VideoThumbnails.larger(listed, 0))
    }

    @Test
    fun `the webp address and other names are raised too`() {
        assertEquals(
            listOf(maxRes, sd),
            VideoThumbnails.larger("https://i.ytimg.com/vi_webp/dQw4w9WgXcQ/hqdefault.webp", 1200),
        )
        assertEquals(
            listOf(maxRes, sd),
            VideoThumbnails.larger("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg?sqp=-oaymwEXCNUGEOADIAQqCwjVARCqCBh4INgESFo&rs=AMzJL3k", 1200),
        )
    }

    @Test
    fun `a file already in hand is not asked for again`() {
        assertEquals(emptyList<String>(), VideoThumbnails.larger(maxRes, 1200))
        assertEquals(listOf(maxRes), VideoThumbnails.larger(sd, 1200))
        assertEquals(emptyList<String>(), VideoThumbnails.larger(sd, 480))
    }

    @Test
    fun `the audio-only mark stays on the address`() {
        assertEquals(listOf("$maxRes#audio-only", "$sd#audio-only"), VideoThumbnails.larger("$listed#audio-only", 1200))
    }

    @Test
    fun `other pictures are left alone`() {
        assertEquals(emptyList<String>(), VideoThumbnails.larger("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj", 1200))
        assertEquals(emptyList<String>(), VideoThumbnails.larger("https://i.ytimg.com/pl_c/PL123/studio_square_thumbnail.jpg", 1200))
        assertEquals(emptyList<String>(), VideoThumbnails.larger("content://media/external/audio/albumart/12", 1200))
    }

    @Test
    fun `only the unsigned 4 by 3 names hold bars`() {
        assertTrue(VideoThumbnails.isLetterboxed(sd))
        assertTrue(VideoThumbnails.isLetterboxed("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg#audio-only"))
        assertFalse(VideoThumbnails.isLetterboxed(maxRes))
        assertFalse(VideoThumbnails.isLetterboxed(listed))
    }
}
