package com.music.bitchord

import com.music.bitchord.data.DetailChoice
import com.music.bitchord.data.SongDetailChoices
import com.music.bitchord.data.SongMetadataFixes
import com.music.bitchord.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page that compares a file's details with the ones found for it: which
 * side is kept for each, what typing over one does, and the song that comes
 * out for the fix.
 */
class SongDetailChoicesTest {

    private val file = SongMetadataFixes.Details(
        title = "03 night drive (final mix)",
        artist = "Unknown artist",
        albumName = "Coastlines",
        thumbnailUrl = "content://media/external/audio/albumart/3",
    )

    private val match = Song(
        videoId = "abcdefghijk",
        title = "Night Drive",
        artist = "Velvet Harbor",
        thumbnailUrl = "https://lh3.googleusercontent.com/cover",
        albumName = "Coastlines",
        albumId = "MPREb_album",
        artistId = "UCartist",
    )

    private val start = SongDetailChoices.of(file, match)

    @Test
    fun `it opens with every found detail kept`() {
        val resolved = start.resolve(match)
        assertEquals("Night Drive", resolved.title)
        assertEquals("Velvet Harbor", resolved.artist)
        assertEquals("UCartist", resolved.artistId)
        assertEquals("Coastlines", resolved.albumName)
        assertEquals("MPREb_album", resolved.albumId)
        assertEquals(match.thumbnailUrl, resolved.thumbnailUrl)
        assertEquals("abcdefghijk", resolved.videoId)
        assertTrue(start.album.same)
        assertFalse(start.title.same)
        assertTrue(start.coverDiffers)
    }

    @Test
    fun `a detail kept from the file drops what was found with it`() {
        val chosen = start.copy(artist = start.artist.pick(foundSide = false), useFoundCover = false)
        val resolved = chosen.resolve(match)
        assertEquals("Night Drive", resolved.title)
        assertEquals("Unknown artist", resolved.artist)
        assertNull(resolved.artistId)
        // Null leaves the song the picture it has.
        assertNull(resolved.thumbnailUrl)
        // The album is still the found one, so its id still belongs to it.
        assertEquals("MPREb_album", resolved.albumId)
    }

    @Test
    fun `typing over the kept side is what the song gets`() {
        val typed = start.copy(title = start.title.typed(foundSide = true, typed = " Night Drive (Final Mix) "))
        assertTrue(typed.title.edited(foundSide = true))
        assertEquals("Night Drive (Final Mix)", typed.resolve(match).title)
        // The other side still reads as it did, and choosing it keeps the typing for later.
        val other = typed.title.pick(foundSide = false)
        assertEquals("03 night drive (final mix)", other.value)
        assertEquals("Night Drive (Final Mix)", other.pick(foundSide = true).value)
    }

    @Test
    fun `typing nothing or the same words is no edit`() {
        val choice = DetailChoice(file = "a", found = "b")
        assertFalse(choice.typed(foundSide = true, typed = "  ").edited(foundSide = true))
        assertFalse(choice.typed(foundSide = true, typed = "b").edited(foundSide = true))
        assertEquals("b", choice.typed(foundSide = true, typed = "c").typed(foundSide = true, typed = "").value)
    }

    @Test
    fun `a typed artist or album is not the found one's`() {
        val typed = start.copy(
            artist = start.artist.typed(foundSide = true, typed = "Velvet Harbour"),
            album = start.album.typed(foundSide = true, typed = "Coastlines (Deluxe)"),
        )
        val resolved = typed.resolve(match)
        assertEquals("Velvet Harbour", resolved.artist)
        assertNull(resolved.artistId)
        assertEquals("Coastlines (Deluxe)", resolved.albumName)
        assertNull(resolved.albumId)
    }

    @Test
    fun `use all takes one side and leaves agreed details alone`() {
        val edited = start.copy(album = start.album.typed(foundSide = true, typed = "Coastlines (Deluxe)"))
        val fromFile = edited.useAll(foundSide = false)
        val resolved = fromFile.resolve(match)
        assertEquals("03 night drive (final mix)", resolved.title)
        assertEquals("Unknown artist", resolved.artist)
        assertEquals("Coastlines (Deluxe)", resolved.albumName)
        assertNull(resolved.thumbnailUrl)
        assertEquals(start.copy(album = edited.album), fromFile.useAll(foundSide = true))
    }

    @Test
    fun `no album and no new cover`() {
        val bare = SongDetailChoices.of(file.copy(albumName = null), match.copy(albumName = null, albumId = null, thumbnailUrl = null))
        assertTrue(bare.album.same)
        assertFalse(bare.coverDiffers)
        val resolved = bare.resolve(match)
        assertNull(resolved.albumName)
        assertNull(resolved.thumbnailUrl)
    }

    @Test
    fun `the fix stores what was chosen`() {
        SongMetadataFixes.load(emptyMap())
        val song = Song(
            videoId = "content://media/external/audio/media/12",
            title = file.title,
            artist = file.artist,
            thumbnailUrl = file.thumbnailUrl,
            albumName = file.albumName,
            localUri = "content://media/external/audio/media/12",
        )
        val chosen = start.copy(
            title = start.title.typed(foundSide = true, typed = "Night Drive (Final Mix)"),
            useFoundCover = false,
        )
        val renamed = SongMetadataFixes.fix(song, chosen.resolve(match))!!
        assertEquals("Night Drive (Final Mix)", renamed.title)
        assertEquals("Velvet Harbor", renamed.artist)
        assertEquals(file.thumbnailUrl, renamed.thumbnailUrl)
        assertEquals("03 night drive (final mix)", SongMetadataFixes.fixOf(song)!!.original.title)
        SongMetadataFixes.load(emptyMap())
    }
}
