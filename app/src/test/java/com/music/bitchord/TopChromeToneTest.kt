package com.music.bitchord

import com.music.bitchord.data.settings.AppStyle
import com.music.bitchord.ui.components.TopChromeTone
import com.music.bitchord.ui.components.topControlsDark
import com.music.bitchord.ui.components.bannerArtLuminance
import com.music.bitchord.ui.components.topChromeOverDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopChromeToneTest {
    @Test
    fun aDarkBannerOnALightPageIsDarkWhileItShows() {
        assertEquals(true, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.8f, artShowing = 1f, themeDark = false))
        // Scrolled off: the light page is what is left under the controls.
        assertEquals(false, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.8f, artShowing = 0f, themeDark = false))
    }

    @Test
    fun aBrightBannerOnADarkPageIsLight() {
        assertEquals(false, topChromeOverDark(artLuminance = 0.9f, washLuminance = 0.03f, artShowing = 1f, themeDark = true))
        assertEquals(true, topChromeOverDark(artLuminance = 0.9f, washLuminance = 0.03f, artShowing = 0f, themeDark = true))
    }

    @Test
    fun aHeldBannerStillCountsAtItsFadedStrength() {
        // A dark photo held at 45% over a light wash: no longer dark enough for white glyphs.
        assertEquals(false, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.8f, artShowing = 0.45f, themeDark = false))
        // Over a dark wash it stays dark.
        assertEquals(true, topChromeOverDark(artLuminance = 0.02f, washLuminance = 0.05f, artShowing = 0.45f, themeDark = true))
    }

    @Test
    fun inTheDarkThemeOnlyAVeryBrightBannerTurnsTheGlyphsBlack() {
        // Sunlit grass and flowers (the album header Stephen reported): white still reads.
        assertEquals(true, topChromeOverDark(artLuminance = 0.39f, washLuminance = 0.05f, artShowing = 1f, themeDark = true))
        assertEquals(true, topChromeOverDark(artLuminance = 0.55f, washLuminance = 0.05f, artShowing = 1f, themeDark = true))
        // A cream or white sleeve: white would be lost.
        assertEquals(false, topChromeOverDark(artLuminance = 0.7f, washLuminance = 0.05f, artShowing = 1f, themeDark = true))
    }

    @Test
    fun inTheLightThemeOnlyADarkBannerTurnsTheGlyphsWhite() {
        // The same grass in the light theme: black glyphs, as on the rest of the app.
        assertEquals(false, topChromeOverDark(artLuminance = 0.39f, washLuminance = 0.8f, artShowing = 1f, themeDark = false))
        assertEquals(false, topChromeOverDark(artLuminance = 0.25f, washLuminance = 0.8f, artShowing = 1f, themeDark = false))
        // A dim stage photograph: black would be lost.
        assertEquals(true, topChromeOverDark(artLuminance = 0.1f, washLuminance = 0.8f, artShowing = 1f, themeDark = false))
    }

    @Test
    fun nothingIsSaidBeforeThePictureIsRead() {
        assertNull(topChromeOverDark(artLuminance = null, washLuminance = 0.8f, artShowing = 1f, themeDark = false))
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
        assertEquals(true, topChromeOverDark(art, washLuminance = 0.8f, artShowing = 1f, themeDark = false))
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

    @Test
    fun onlyLiquidGlassTakesTheBannersTone() {
        // Light theme over a dark banner: glass goes white, frost and solid keep their own light pill.
        assertEquals(true, topControlsDark(AppStyle.GLASS, overDark = true, themeDark = false))
        assertEquals(false, topControlsDark(AppStyle.FROST, overDark = true, themeDark = false))
        assertEquals(false, topControlsDark(AppStyle.SOLID, overDark = true, themeDark = false))
        // Dark theme over a very bright banner: the same the other way about.
        assertEquals(false, topControlsDark(AppStyle.GLASS, overDark = false, themeDark = true))
        assertEquals(true, topControlsDark(AppStyle.FROST, overDark = false, themeDark = true))
        // Nothing read yet: the theme stands.
        assertEquals(true, topControlsDark(AppStyle.GLASS, overDark = null, themeDark = true))
    }
}
