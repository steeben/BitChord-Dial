package com.music.bitchord

import com.music.bitchord.data.DeviceArtists
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceArtistsTest {
    private fun file(n: Int, title: String, artist: String, album: String? = null, length: String = "4:00") =
        Song(
            videoId = "content://media/external/audio/media/$n",
            title = title,
            artist = artist,
            thumbnailUrl = null,
            durationText = length,
            albumName = album,
            localUri = "content://media/external/audio/media/$n",
        )

    private fun download(id: String, title: String, artist: String, artistId: String?, length: String = "4:00") =
        Song(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = null,
            durationText = length,
            artistId = artistId,
            artists = listOf(ArtistRef(artist, artistId)),
            localUri = "file:///downloads/$id.m4a",
        )

    @Test
    fun filesByTheArtistAreFoundByName() {
        val device = listOf(
            file(1, "Shelter", "Porter Robinson, Madeon", "Shelter"),
            file(2, "Language", "Porter Robinson", "Worlds"),
            file(3, "Sad Machine", "porter robinson", "Worlds"),
            file(4, "Icarus", "Madeon", "Adventure"),
        )
        val songs = DeviceArtists.songs(device, "UCporter", "Porter Robinson")
        assertEquals(listOf("Language", "Sad Machine", "Shelter"), songs.map { it.title })
    }

    @Test
    fun anAmpersandCreditCountsWhenTheOtherSideIsKnown() {
        val device = listOf(
            file(1, "Shelter", "Porter Robinson & Madeon"),
            file(2, "Icarus", "Madeon"),
            file(3, "The Boxer", "Simon & Garfunkel"),
        )
        assertEquals(listOf("Shelter"), DeviceArtists.songs(device, null, "Porter Robinson").map { it.title })
        assertEquals(emptyList<String>(), DeviceArtists.songs(device, null, "Simon").map { it.title })
    }

    @Test
    fun aDownloadIsFoundByIdAndKeptOnceBesideItsFile() {
        val device = listOf(
            download("aaaaaaaaaaa", "Look at the Sky", "Porter Robinson", "UCporter"),
            file(1, "Look At The Sky", "Porter Robinson"),
            download("bbbbbbbbbbb", "Something Else", "Someone", "UCporter"),
        )
        val songs = DeviceArtists.songs(device, "UCporter", "Porter Robinson")
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), songs.map { it.videoId })
    }

    @Test
    fun onlyTheArtistsOwnAlbumsAreListed() {
        val device = listOf(
            file(1, "Language", "Porter Robinson", "Worlds"),
            file(2, "Sad Machine", "Porter Robinson", "Worlds"),
            file(3, "Fellow Feeling", "Porter Robinson", "Worlds"),
            file(4, "Hit One", "Someone", "Now 2014"),
            file(5, "Lionhearted", "Porter Robinson", "Now 2014"),
            file(6, "Hit Two", "Someone Else", "Now 2014"),
        )
        val songs = DeviceArtists.songs(device, null, "Porter Robinson")
        val albums = DeviceArtists.albums(device, songs, "Porter Robinson")
        assertEquals(listOf("Worlds"), albums.map { it.first })
        assertEquals(3, albums.single().second.size)
        assertEquals(4, songs.size)
    }
}
