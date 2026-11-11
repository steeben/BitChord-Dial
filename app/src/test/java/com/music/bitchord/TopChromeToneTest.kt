package com.music.bitchord

import com.music.bitchord.ui.components.TopChromeTone
import com.music.bitchord.ui.components.bannerArtLuminance
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
        // A dark photo held at 20% over a white wash: no longer dark enough for white glyphs.
        assertEquals(false, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.95f, artShowing = 0.2f))
        // Over a dark wash it stays dark.
        assertEquals(true, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.05f, artShowing = 0.2f))
    }

    @Test
    fun onlyAVeryBrightBannerTurnsTheGlyphsBlack() {
        // Sunlit grass and flowers (the album header Stephen reported): white still reads.
        assertEquals(true, topChromeOverDark(artLuminance = 0.39f, washLuminance = 0.05f, artShowing = 1f))
        assertEquals(true, topChromeOverDark(artLuminance = 0.55f, washLuminance = 0.05f, artShowing = 1f))
        // A cream or white sleeve: white would be lost.
        assertEquals(false, topChromeOverDark(artLuminance = 0.7f, washLuminance = 0.05f, artShowing = 1f))
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

    @Test
    fun aDarkClipOverABrightPhotoMakesTheControlsWhite() {
        // The artist's photo is bright, the clip playing over it is black at the top.
        val art = bannerArtLuminance(still = 0.7f, clip = 0.01f, clipShowing = 1f)
        assertEquals(0.01f, art!!, 0.0001f)
        assertEquals(true, topChromeOverDark(art, washLuminance = 0.8f, artShowing = 1f))
    }

    @Test
    fun theClipCountsOnlyAsFarAsItHasFadedIn() {
        assertEquals(0.7f, bannerArtLuminance(still = 0.7f, clip = 0.01f, clipShowing = 0f)!!, 0.0001f)
        assertEquals(0.355f, bannerArtLuminance(still = 0.7f, clip = 0.01f, clipShowing = 0.5f)!!, 0.0001f)
        // No frame read yet: the photo stands.
        assertEquals(0.7f, bannerArtLuminance(still = 0.7f, clip = null, clipShowing = 1f)!!, 0.0001f)
        // A clip up before the photo has been read.
        assertEquals(0.01f, bannerArtLuminance(still = null, clip = 0.01f, clipShowing = 1f)!!, 0.0001f)
        assertNull(bannerArtLuminance(still = null, clip = null, clipShowing = 1f))
    }
}
