package com.music.bitchord.data

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasAudioFixesTest {
    private val saved = mutableListOf<CanvasAudioFixes.State>()

    init {
        CanvasAudioFixes.load(CanvasAudioFixes.State())
        CanvasAudioFixes.persist = { saved += it }
    }

    @AfterTest
    fun tearDown() {
        CanvasAudioFixes.persist = null
        CanvasAudioFixes.load(CanvasAudioFixes.State())
    }

    @Test
    fun `each fix is saved and read back for its song`() {
        CanvasAudioFixes.keepVideoAudio("video")
        CanvasAudioFixes.hideCanvas("song")
        CanvasAudioFixes.setSyncOffset("video", "song", -31_500L)

        assertTrue(CanvasAudioFixes.keepsVideoAudio("video"))
        assertTrue("song" in CanvasAudioFixes.state.value.hiddenCanvas)
        assertEquals(-31_500L, CanvasAudioFixes.syncOffset("video", "song"))
        assertNull(CanvasAudioFixes.syncOffset("song", "video"))
        assertEquals(3, saved.size)
        assertEquals(CanvasAudioFixes.state.value, saved.last())
    }

    @Test
    fun `an unchanged answer is not written again`() {
        CanvasAudioFixes.hideVideoCanvas("song")
        CanvasAudioFixes.hideVideoCanvas("song")
        assertEquals(1, saved.size)
    }

    @Test
    fun `undo forgets every fix naming the song or its video`() {
        CanvasAudioFixes.keepVideoAudio("video")
        CanvasAudioFixes.hideCanvas("song")
        CanvasAudioFixes.setSyncOffset("video", "song", 1_000L)
        CanvasAudioFixes.hideCanvas("other")

        assertTrue(CanvasAudioFixes.state.value.touches("video"))
        CanvasAudioFixes.reset("song", "video")

        val state = CanvasAudioFixes.state.value
        assertFalse(state.touches("song"))
        assertFalse(state.touches("video"))
        assertTrue("other" in state.hiddenCanvas)
    }
}
