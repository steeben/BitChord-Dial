package com.music.bitchord.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoQualityTest {

    @Test
    fun `the defaults keep the rule from before the settings`() {
        val wifi = VideoQuality.WIFI_DEFAULT
        val mobile = VideoQuality.MOBILE_DEFAULT
        assertEquals(1080, VideoQuality.forConnection(wifi, mobile, metered = false).maxHeight)
        assertEquals(1080, VideoQuality.forConnection(wifi, mobile, metered = null).maxHeight)
        assertEquals(480, VideoQuality.forConnection(wifi, mobile, metered = true).maxHeight)
    }

    @Test
    fun `each connection has its own choice`() {
        assertEquals(
            VideoQuality.MAX,
            VideoQuality.forConnection(wifi = VideoQuality.MAX, mobile = VideoQuality.P1080, metered = false),
        )
        assertEquals(
            VideoQuality.P1080,
            VideoQuality.forConnection(wifi = VideoQuality.MAX, mobile = VideoQuality.P1080, metered = true),
        )
        assertEquals(2160, VideoQuality.MAX.maxHeight)
    }

    @Test
    fun `the held height of the quality box has priority`() {
        assertEquals(720, VideoQuality.heightToAsk(VideoQuality.MAX, held = 720, steppedDown = false))
        assertEquals(1440, VideoQuality.heightToAsk(VideoQuality.P480, held = 1440, steppedDown = false))
    }

    @Test
    fun `a video that failed above 1080p goes back to 1080p`() {
        assertEquals(1080, VideoQuality.heightToAsk(VideoQuality.MAX, held = null, steppedDown = true))
        assertEquals(1080, VideoQuality.heightToAsk(VideoQuality.P480, held = 2160, steppedDown = true))
        assertEquals(480, VideoQuality.heightToAsk(VideoQuality.P480, held = null, steppedDown = true))
    }

    @Test
    fun `an unknown stored name gives the default`() {
        assertEquals(VideoQuality.P480, VideoQuality.decode(null, VideoQuality.P480))
        assertEquals(VideoQuality.P1080, VideoQuality.decode("AUTO", VideoQuality.P1080))
        assertEquals(VideoQuality.MAX, VideoQuality.decode("MAX", VideoQuality.P480))
    }
}
