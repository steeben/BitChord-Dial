package com.music.bitchord.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BrightArtTest {
    @Test
    fun inkTintScalesWithBrightnessUpToFortyPercent() {
        assertEquals(0.28f, playerInkTint(null), 0.0001f)
        assertEquals(0.28f, playerInkTint(0.05f), 0.0001f)
        assertEquals(0.40f, playerInkTint(0.95f), 0.0001f)
        val mid = playerInkTint(0.48f)
        assertTrue("mid was $mid", mid > 0.28f && mid < 0.40f)
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
