package com.music.bitchord

import com.music.bitchord.ui.components.TopChromeTone
import com.music.bitchord.ui.components.topChromeOverDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopChromeToneTest {
    @Test
    fun aDarkBannerOnALightPageIsDarkWhileItShows() {
        assertEquals(true, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.8f, artShowing = 1f))
        // Scrolled off: the light page is what is left under the controls.
        assertEquals(false, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.8f, artShowing = 0f))
    }

    @Test
    fun aBrightBannerOnADarkPageIsLight() {
        assertEquals(false, topChromeOverDark(artLuminance = 0.9f, washLuminance = 0.03f, artShowing = 1f))
        assertEquals(true, topChromeOverDark(artLuminance = 0.9f, washLuminance = 0.03f, artShowing = 0f))
    }

    @Test
    fun aHeldBannerStillCountsAtItsFadedStrength() {
        // A dark photo held at 45% over a light wash: no longer dark enough for white glyphs.
        assertEquals(false, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.8f, artShowing = 0.45f))
        // Over a dark wash it stays dark.
        assertEquals(true, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.05f, artShowing = 0.45f))
    }

    @Test
    fun nothingIsSaidBeforeThePictureIsRead() {
        assertNull(topChromeOverDark(artLuminance = null, washLuminance = 0.8f, artShowing = 1f))
    }

    @Test
    fun aPageOnlyClearsWhatItSet() {
        val leaving = Any()
        val arriving = Any()
        TopChromeTone.set(leaving, true)
        TopChromeTone.set(arriving, false)
        TopChromeTone.clear(leaving)
        assertEquals(false, TopChromeTone.overDark)
        TopChromeTone.clear(arriving)
        assertNull(TopChromeTone.overDark)
    }
}
