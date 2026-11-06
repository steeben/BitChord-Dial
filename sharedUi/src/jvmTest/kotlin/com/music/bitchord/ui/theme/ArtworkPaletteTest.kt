package com.music.bitchord.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkPaletteTest {
    @Test
    fun grayscaleDoesNotAcquireTheDefaultRedHue() {
        assertEquals(0f, adaptedArtworkSaturation(0f, minimum = 0.20f, maximum = 0.62f), 0f)
        assertEquals(0f, adaptedArtworkSaturation(0f, minimum = 0.55f, maximum = 1f), 0f)
    }

    @Test
    fun nearlyNeutralArtworkKeepsItsSubtleSaturation() {
        assertEquals(0.08f, adaptedArtworkSaturation(0.08f, minimum = 0.20f, maximum = 0.62f), 0f)
    }

    @Test
    fun genuinelyChromaticArtworkIsStillStrengthenedAndCapped() {
        assertEquals(0.55f, adaptedArtworkSaturation(0.25f, minimum = 0.55f, maximum = 1f), 0f)
        assertEquals(0.62f, adaptedArtworkSaturation(0.90f, minimum = 0.20f, maximum = 0.62f), 0f)
    }

    @Test
    fun bandBrightnessCountsAHalfWhiteBandAsBright() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        // Half white, half black: the mean would call it mid-grey; the text over
        // the white half is what has to stay readable.
        val half = IntArray(100) { if (it % 2 == 0) white else black }
        assertEquals(1f, bandBrightness(half), 0.0001f)
        // Mostly black with a little white stays dark.
        val mostlyDark = IntArray(100) { if (it < 20) white else black }
        assertEquals(0f, bandBrightness(mostlyDark), 0.0001f)
        assertEquals(0f, bandBrightness(IntArray(0)), 0f)
    }
}
