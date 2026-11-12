package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.screens.collageCovers
import com.music.bitchord.ui.screens.pageColourSource
import com.music.bitchord.ui.screens.playlistTakesGeneratedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCollageCoversTest {

    private fun song(id: String, artist: String, album: String, art: String = "https://i.ytimg.com/$album.jpg") =
        Song(videoId = id, title = id, artist = artist, thumbnailUrl = art, albumId = album)

    @Test
    fun `Spotify playlists and mixes get a generated image, as YouTube Music's do`() {
        assertTrue(playlistTakesGeneratedImage("VLPL123"))
        assertTrue(playlistTakesGeneratedImage("spotify:playlist:37i9dQZF1EQpoj8u9Hn81e"))
        assertTrue(!playlistTakesGeneratedImage("MPREb_album"))
        assertTrue(!playlistTakesGeneratedImage("local:songs"))
    }

    @Test
    fun `a playlist's newest songs are at its foot`() {
        val songs = listOf(song("1", "A", "a1"), song("2", "B", "b1"), song("3", "C", "c1"))
        assertEquals(
            listOf("https://i.ytimg.com/c1.jpg", "https://i.ytimg.com/b1.jpg", "https://i.ytimg.com/a1.jpg"),
            collageCovers(songs, newestFirst = false),
        )
    }

    @Test
    fun `liked music leads with its newest`() {
        val songs = listOf(song("1", "A", "a1"), song("2", "B", "b1"))
        assertEquals(
            listOf("https://i.ytimg.com/a1.jpg", "https://i.ytimg.com/b1.jpg"),
            collageCovers(songs, newestFirst = true),
        )
    }

    @Test
    fun `no album or artwork twice, and artists repeat only to fill`() {
        val songs = listOf(
            song("1", "A", "a1"),
            song("2", "A", "a1"),
            song("3", "A", "a2"),
            song("4", "B", "b1"),
        )
        assertEquals(
            listOf("https://i.ytimg.com/a1.jpg", "https://i.ytimg.com/b1.jpg", "https://i.ytimg.com/a2.jpg"),
            collageCovers(songs, newestFirst = true),
        )
    }

    @Test
    fun `stops at the most covers a collage holds`() {
        val songs = (1..20).map { song("$it", "Artist $it", "album$it") }
        assertEquals(3, collageCovers(songs, newestFirst = true, max = 3).size)
    }

    @Test
    fun `picking again draws other songs under the same rules`() {
        val songs = (1..40).map { song("$it", "Artist ${it % 20}", "album$it") }
        val newest = collageCovers(songs, newestFirst = true)
        val again = collageCovers(songs, newestFirst = true, shuffle = kotlin.random.Random(7))
        assertEquals(9, again.size)
        assertNotEquals(newest, again)
        assertEquals(again.size, again.toSet().size)
        assertTrue(again.all { url -> songs.any { it.thumbnailUrl == url } })
    }

    @Test
    fun `a playlist keeps the colours of the picture it opened with once its collage is in`() {
        assertEquals("cover", pageColourSource(null, "cover", null))
        assertEquals("cover", pageColourSource(null, "cover", "sleeve"))
    }

    @Test
    fun `a page with no picture of its own takes its colours from the collage`() {
        assertEquals("sleeve", pageColourSource(null, null, "sleeve"))
        assertEquals("sleeve", pageColourSource(null, "", "sleeve"))
        assertEquals(null, pageColourSource(null, null, null))
    }

    @Test
    fun `an artist photo still comes first`() {
        assertEquals("hero", pageColourSource("hero", "cover", "sleeve"))
    }
}
