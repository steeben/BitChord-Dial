package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BrightArtTest {
    @Test
    fun inkTintIsEitherOr() {
        assertEquals(0.28f, playerInkTint(null), 0.0001f)
        assertEquals(0.28f, playerInkTint(0.05f), 0.0001f)
        assertEquals(0.28f, playerInkTint(0.48f), 0.0001f)
        assertEquals(0.40f, playerInkTint(0.60f), 0.0001f)
        assertEquals(0.40f, playerInkTint(0.95f), 0.0001f)
    }

    @Test
    fun inkDarkensFiftyPercentOnlyOnMostlyWhiteArt() {
        assertEquals(0f, playerInkDarken(null), 0.0001f)
        assertEquals(0f, playerInkDarken(0.05f), 0.0001f)
        // Mid-toned art (the old ramp already darkened here) keeps light ink.
        assertEquals(0f, playerInkDarken(0.48f), 0.0001f)
        assertEquals(0f, playerInkDarken(0.59f), 0.0001f)
        assertEquals(0.50f, playerInkDarken(0.60f), 0.0001f)
        assertEquals(0.50f, playerInkDarken(0.95f), 0.0001f)
    }

    @Test
    fun bandInkGoesDarkOnlyNearWhiteAndHoldsAcrossTheGap() {
        // From light: only a near-white band flips it.
        assertEquals(false, bandInkIsDark(0.30f, wasDark = false))
        assertEquals(false, bandInkIsDark(0.75f, wasDark = false))
        assertEquals(true, bandInkIsDark(0.80f, wasDark = false))
        // From dark: it stays dark through the gap, so a wobbling clip can't flicker it.
        assertEquals(true, bandInkIsDark(0.75f, wasDark = true))
        assertEquals(true, bandInkIsDark(0.68f, wasDark = true))
        assertEquals(false, bandInkIsDark(0.67f, wasDark = true))
    }

    @Test
    fun inkStaysLightOverAClipUnlessPicked() {
        // Over a clip: light, even when the band or the sleeve says dark.
        assertEquals(false, playerInkDark(null, overClip = true, bandDark = true, artLuminance = 0.9f))
        assertEquals(false, playerInkDark(null, overClip = true, bandDark = null, artLuminance = 0.9f))
        // A Song fixes pick still wins.
        assertEquals(true, playerInkDark(true, overClip = true, bandDark = false, artLuminance = 0.1f))
        // Over the sleeve the band decides, else the whole sleeve.
        assertEquals(true, playerInkDark(null, overClip = false, bandDark = true, artLuminance = 0.1f))
        assertEquals(false, playerInkDark(null, overClip = false, bandDark = false, artLuminance = 0.9f))
        assertEquals(true, playerInkDark(null, overClip = false, bandDark = null, artLuminance = 0.9f))
    }

    @Test
    fun controlsScrimRisesWithTheBand() {
        assertEquals(0f, controlsScrimAlpha(0.05f), 0.0001f)
        assertEquals(0f, controlsScrimAlpha(0.20f), 0.0001f)
        val mid = controlsScrimAlpha(0.42f)
        assertTrue("mid was $mid", mid > 0.1f && mid < 0.4f)
        assertEquals(0.50f, controlsScrimAlpha(0.65f), 0.0001f)
        assertEquals(0.50f, controlsScrimAlpha(0.95f), 0.0001f)
    }

    @Test
    fun meshPrefersColourOverCommonWhites() {
        val swatches = listOf(
            Color(0xFFF2F2F2), // paper, most common
            Color(0xFFD8D8D8),
            Color(0xFFB0B0B0),
            Color(0xFF8A8A8A),
            Color(0xFFB8A0D0), // lilac
            Color(0xFF6A5AA8), // violet
        )
        val mesh = swatches.meshColors()
        assertEquals(4, mesh.size)
        val greys = mesh.count { maxOf(it.red, it.green, it.blue) - minOf(it.red, it.green, it.blue) < 0.10f }
        assertTrue("expected at most one grey blob, got $greys in $mesh", greys <= 1)
    }
}
