package com.music.bitchord

import com.music.bitchord.data.model.SPOTIFY_PENDING_PREFIX
import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.screens.HeldRowArt
import com.music.bitchord.ui.screens.songRowKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwappedRowsTest {

    private fun pending(id: String, art: String?) =
        Song(videoId = SPOTIFY_PENDING_PREFIX + id, title = id, artist = "", thumbnailUrl = art)

    private fun matched(id: String, art: String?) =
        Song(videoId = id, title = id, artist = "", thumbnailUrl = art)

    @Test
    fun `a row swapped in place keeps its key when its match lands`() {
        assertEquals(
            songRowKey(3, SPOTIFY_PENDING_PREFIX + "a", swapsInPlace = true),
            songRowKey(3, "yt-a", swapsInPlace = true),
        )
        assertNotEquals(
            songRowKey(3, "yt-a", swapsInPlace = true),
            songRowKey(4, "yt-a", swapsInPlace = true),
        )
    }

    @Test
    fun `other pages still key a row by its song`() {
        assertNotEquals(
            songRowKey(3, "a", swapsInPlace = false),
            songRowKey(3, "b", swapsInPlace = false),
        )
        assertEquals("3_a", songRowKey(3, "a", swapsInPlace = false))
    }

    @Test
    fun `a matched row keeps the artwork it waited with`() {
        val art = HeldRowArt()
        art.over(listOf(pending("a", "sp-a"), pending("b", "sp-b")))
        val after = art.over(listOf(matched("yt-a", "yt-art-a"), pending("b", "sp-b")))
        assertEquals("sp-a", after["yt-a"])
        assertEquals("sp-b", after[SPOTIFY_PENDING_PREFIX + "b"])
    }

    @Test
    fun `rows that came in later pages of the playlist are held too`() {
        val art = HeldRowArt()
        art.over(listOf(pending("a", "sp-a")))
        art.over(listOf(pending("a", "sp-a"), pending("b", "sp-b")))
        val after = art.over(listOf(matched("yt-a", "x"), matched("yt-b", "y")))
        assertEquals(mapOf("yt-a" to "sp-a", "yt-b" to "sp-b"), after)
    }

    @Test
    fun `a page whose rows never waited holds nothing`() {
        assertTrue(HeldRowArt().over(listOf(matched("a", "x"), matched("b", null))).isEmpty())
    }

    @Test
    fun `a waiting row with no artwork takes its match's`() {
        val art = HeldRowArt()
        art.over(listOf(pending("a", null)))
        assertTrue(art.over(listOf(matched("yt-a", "yt-art-a"))).isEmpty())
    }
}
