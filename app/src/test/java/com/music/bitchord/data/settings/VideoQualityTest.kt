package com.music.bitchord.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoQualityTest {

    @Test
    fun `auto keeps the rule from before the setting`() {
        assertEquals(1080, VideoQuality.AUTO.maxHeight(metered = false))
        assertEquals(1080, VideoQuality.AUTO.maxHeight(metered = null))
        assertEquals(480, VideoQuality.AUTO.maxHeight(metered = true))
    }

    @Test
    fun `a fixed quality is the same on all connections`() {
        for (metered in listOf(true, false, null)) {
            assertEquals(480, VideoQuality.P480.maxHeight(metered))
            assertEquals(1080, VideoQuality.P1080.maxHeight(metered))
            assertEquals(2160, VideoQuality.MAX.maxHeight(metered))
        }
    }

    @Test
    fun `the held height of the quality box has priority`() {
        assertEquals(720, VideoQuality.heightToAsk(VideoQuality.MAX, held = 720, metered = false, steppedDown = false))
        assertEquals(1440, VideoQuality.heightToAsk(VideoQuality.P480, held = 1440, metered = true, steppedDown = false))
    }

    @Test
    fun `a video that failed above 1080p goes back to 1080p`() {
        assertEquals(1080, VideoQuality.heightToAsk(VideoQuality.MAX, held = null, metered = false, steppedDown = true))
        assertEquals(1080, VideoQuality.heightToAsk(VideoQuality.AUTO, held = 2160, metered = false, steppedDown = true))
        assertEquals(480, VideoQuality.heightToAsk(VideoQuality.P480, held = null, metered = false, steppedDown = true))
    }

    @Test
    fun `an unknown stored name gives auto`() {
        assertEquals(VideoQuality.AUTO, VideoQuality.decode(null))
        assertEquals(VideoQuality.AUTO, VideoQuality.decode("P720"))
        assertEquals(VideoQuality.MAX, VideoQuality.decode("MAX"))
    }
}
