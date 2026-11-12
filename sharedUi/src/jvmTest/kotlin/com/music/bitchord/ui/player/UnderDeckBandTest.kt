package com.music.bitchord.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnderDeckBandTest {
    @Test
    fun nothingGoesSoftWithNoDeckUp() {
        assertNull(underDeckBand(height = 1800f, deck = 0f, fade = 80f))
        assertNull(underDeckBand(height = 0f, deck = 600f, fade = 80f))
    }

    @Test
    fun listGoesSoftFromJustAboveTheDeck() {
        val band = underDeckBand(height = 1800f, deck = 600f, fade = 80f)!!
        assertEquals(1200f, band.deckTop, 0.001f)
        assertEquals(1120f, band.fadeTop, 0.001f)
    }

    @Test
    fun bandFollowsTheDeckAsItSlides() {
        val half = underDeckBand(height = 1800f, deck = 300f, fade = 80f)!!
        assertEquals(1500f, half.deckTop, 0.001f)
        assertEquals(1420f, half.fadeTop, 0.001f)
    }

    @Test
    fun bandStaysInsideAPanelShorterThanTheDeck() {
        val band = underDeckBand(height = 400f, deck = 600f, fade = 80f)!!
        assertEquals(0f, band.deckTop, 0.001f)
        assertEquals(0f, band.fadeTop, 0.001f)
    }
}
