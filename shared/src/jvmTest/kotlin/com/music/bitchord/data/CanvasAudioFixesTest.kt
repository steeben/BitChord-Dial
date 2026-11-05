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

    @Test
    fun `a picked music video undoes doesn't-match and outranks a found one`() {
        CanvasAudioFixes.hideVideoCanvas("song")
        VideoCanvases.link("song", "found")
        CanvasAudioFixes.pairVideo("song", "picked")

        assertEquals("picked", VideoCanvases.pictureFor("song"))
        assertFalse("song" in CanvasAudioFixes.state.value.hiddenVideoCanvas)
        assertEquals("found", VideoCanvases.foundFor("song"))
    }

    @Test
    fun `a picked song replaces keeping the video's audio and is undone with the rest`() {
        CanvasAudioFixes.keepVideoAudio("video")
        CanvasAudioFixes.pickSong("video", com.music.bitchord.data.model.Song("right", "Title", "Artist", null))

        assertEquals("right", CanvasAudioFixes.songPick("video")?.videoId)
        assertFalse(CanvasAudioFixes.keepsVideoAudio("video"))
        assertTrue(CanvasAudioFixes.state.value.touches("video"))

        CanvasAudioFixes.reset("video")
        assertNull(CanvasAudioFixes.songPick("video"))
    }

    @Test
    fun `album art brings back a hidden Canvas and drops a picked video`() {
        CanvasAudioFixes.hideCanvas("song")
        CanvasAudioFixes.pairVideo("song", "video")
        CanvasAudioFixes.useAlbumArt("song")

        val state = CanvasAudioFixes.state.value
        assertTrue(CanvasAudioFixes.showsAlbumArt("song"))
        assertFalse("song" in state.hiddenCanvas)
        assertNull(CanvasAudioFixes.pairedVideo("song"))
    }

    @Test
    fun `lyrics text is picked apart for album art and Canvas, and goes back to auto`() {
        CanvasAudioFixes.setLyricInk("song", overCanvas = false, CanvasAudioFixes.LyricInk.DARK)
        CanvasAudioFixes.setLyricInk("song", overCanvas = true, CanvasAudioFixes.LyricInk.LIGHT)

        assertEquals(CanvasAudioFixes.LyricInk.DARK, CanvasAudioFixes.lyricInk("song", overCanvas = false))
        assertEquals(CanvasAudioFixes.LyricInk.LIGHT, CanvasAudioFixes.lyricInk("song", overCanvas = true))
        assertNull(CanvasAudioFixes.lyricInk("other", overCanvas = false))

        CanvasAudioFixes.setLyricInk("song", overCanvas = false, null)
        assertNull(CanvasAudioFixes.lyricInk("song", overCanvas = false))
        assertEquals(CanvasAudioFixes.LyricInk.LIGHT, CanvasAudioFixes.lyricInk("song", overCanvas = true))
        assertEquals(CanvasAudioFixes.state.value, saved.last())
    }

    @Test
    fun `video doesn't match drops a searched-for video too`() {
        CanvasAudioFixes.pairVideo("song", "picked")
        CanvasAudioFixes.hideVideoCanvas("song")

        assertNull(CanvasAudioFixes.pairedVideo("song"))
        assertTrue("song" in CanvasAudioFixes.state.value.hiddenVideoCanvas)
    }
}
