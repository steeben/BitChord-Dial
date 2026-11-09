package com.music.bitchord

import com.music.bitchord.data.model.Song
import com.music.bitchord.ui.screens.collageCovers
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCollageCoversTest {

    private fun song(id: String, artist: String, album: String, art: String = "https://i.ytimg.com/$album.jpg") =
        Song(videoId = id, title = id, artist = artist, thumbnailUrl = art, albumId = album)

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
}
