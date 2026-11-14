package com.music.bitchord

import com.music.bitchord.data.LibraryAlbums
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAlbumsTest {
    private fun song(title: String, artist: String, album: String?) =
        Song(videoId = title, title = title, artist = artist, thumbnailUrl = null, albumName = album)

    @Test
    fun findsAlbumsByNameWithTheirTracksAndArtist() {
        val songs = listOf(
            song("a", "Savage Garden", "Affirmation"),
            song("b", "Savage Garden", "Affirmation"),
            song("c", "Guest, Savage Garden", "Affirmation"),
            song("d", "Other", "Garden State"),
            song("e", "Other", null),
        )
        val hit = LibraryAlbums.search(songs, "affirm").single()
        assertEquals("Affirmation", hit.name)
        assertEquals("Savage Garden", hit.artist)
        assertEquals(listOf("a", "b", "c"), hit.tracks.map { it.title })
    }

    @Test
    fun namesThatStartWithTheQueryComeFirst() {
        val songs = listOf(
            song("a", "X", "Secret Garden"),
            song("b", "Y", "Garden State"),
        )
        assertEquals(listOf("Garden State", "Secret Garden"), LibraryAlbums.search(songs, "garden").map { it.name })
    }

    @Test
    fun aBlankQueryFindsNothing() {
        assertTrue(LibraryAlbums.search(listOf(song("a", "X", "Y")), "  ").isEmpty())
    }
}
