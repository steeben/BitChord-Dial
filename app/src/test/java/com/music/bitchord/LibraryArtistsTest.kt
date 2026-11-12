package com.music.bitchord

import com.music.bitchord.data.LibraryArtists
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryArtistsTest {
    private fun song(artist: String, album: String? = null) =
        Song(videoId = artist + album, title = "t", artist = artist, thumbnailUrl = null, albumName = album)

    @Test
    fun commasAndSemicolonsSeparateArtists() {
        assertEquals(listOf("Porter Robinson", "Imaginary Cities"), LibraryArtists.split("Porter Robinson, Imaginary Cities"))
        assertEquals(listOf("A", "B", "C"), LibraryArtists.split("A; B;C"))
        assertEquals(listOf("A", "B"), LibraryArtists.split("A / B"))
    }

    @Test
    fun featuringCreditsSeparateArtists() {
        assertEquals(listOf("A", "B"), LibraryArtists.split("A feat. B"))
        assertEquals(listOf("A", "B"), LibraryArtists.split("A (ft. B)"))
        assertEquals(listOf("A", "B", "C"), LibraryArtists.split("A featuring B, C"))
    }

    @Test
    fun namesWrittenWithACommaStayWhole() {
        assertEquals(listOf("Tyler, The Creator"), LibraryArtists.split("Tyler, The Creator"))
        assertEquals(listOf("Tyler, The Creator", "Kali Uchis"), LibraryArtists.split("Tyler, The Creator, Kali Uchis"))
        assertEquals(listOf("Earth, Wind & Fire"), LibraryArtists.split("Earth, Wind & Fire"))
        assertEquals(listOf("10,000 Maniacs"), LibraryArtists.split("10,000 Maniacs"))
        assertEquals(listOf("AC/DC"), LibraryArtists.split("AC/DC"))
    }

    @Test
    fun ampersandSplitsOnlyBetweenKnownArtists() {
        assertEquals(listOf("Simon & Garfunkel"), LibraryArtists.split("Simon & Garfunkel", setOf("simon")))
        val known = LibraryArtists.knownArtists(listOf("Porter Robinson", "Madeon"))
        assertEquals(listOf("Porter Robinson", "Madeon"), LibraryArtists.split("Porter Robinson & Madeon", known))
        assertEquals(listOf("Porter Robinson", "Madeon"), LibraryArtists.split("Porter Robinson x Madeon", known))
    }

    @Test
    fun collaborationsListUnderEveryArtist() {
        val songs = listOf(
            song("Porter Robinson", "Worlds"),
            song("Porter Robinson, Imaginary Cities", "Worlds"),
            song("porter robinson", "Nurture"),
        )
        val groups = LibraryArtists.group(songs).toMap()
        assertEquals(setOf("Porter Robinson", "Imaginary Cities"), groups.keys)
        assertEquals(3, groups.getValue("Porter Robinson").size)
        assertEquals(1, groups.getValue("Imaginary Cities").size)
    }

    @Test
    fun searchFindsArtistsByName() {
        val songs = listOf(
            song("Madeon"),
            song("Porter Robinson, Madeon"),
            song("The Port"),
            song("Porter Robinson"),
            song("Someone Else", "Porter"),
        )
        val hits = LibraryArtists.search(songs, " port ")
        // Starts-with first, then a name that only contains it; an album called
        // "Porter" is not an artist.
        assertEquals(listOf("Porter Robinson", "The Port"), hits.map { it.first })
        assertEquals(2, hits.first().second.size)
        assertEquals(listOf("Madeon"), LibraryArtists.search(songs, "MADE").map { it.first })
        assertEquals(emptyList<String>(), LibraryArtists.search(songs, "  ").map { it.first })
        assertEquals(emptyList<String>(), LibraryArtists.search(songs, "zzz").map { it.first })
    }

    @Test
    fun albumArtistIsTheMostCredited() {
        val tracks = listOf(
            song("Imaginary Cities, Porter Robinson"),
            song("Porter Robinson"),
            song("Porter Robinson"),
        )
        assertEquals("Porter Robinson", LibraryArtists.albumArtist(tracks))
    }
}
