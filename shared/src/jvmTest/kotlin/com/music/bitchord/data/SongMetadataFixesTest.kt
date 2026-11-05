package com.music.bitchord.data

import com.music.bitchord.data.innertube.UploadedTracks
import com.music.bitchord.data.model.Song
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongMetadataFixesTest {
    private val saved = mutableListOf<Map<String, SongMetadataFixes.Fix>>()

    init {
        SongMetadataFixes.load(emptyMap())
        SongMetadataFixes.persist = { saved += it }
        UploadedTracks.clear()
    }

    @AfterTest
    fun tearDown() {
        SongMetadataFixes.persist = null
        SongMetadataFixes.load(emptyMap())
        UploadedTracks.clear()
    }

    private val file = Song(
        videoId = "content://media/external/audio/media/12",
        title = "track01",
        artist = "Unknown Artist",
        thumbnailUrl = "content://media/external/audio/albumart/3",
        localUri = "content://media/external/audio/media/12",
    )

    private val match = Song(
        videoId = "abcdefghijk",
        title = "Real Title",
        artist = "Real Artist",
        thumbnailUrl = "https://lh3.googleusercontent.com/cover",
        albumName = "Real Album",
        albumId = "MPREb_album",
        artistId = "UCartist",
    )

    @Test
    fun `only files and uploads can be fixed`() {
        assertTrue(SongMetadataFixes.canFix(file))
        val catalogue = Song(videoId = "zyxwvutsrqp", title = "A", artist = "B", thumbnailUrl = null)
        assertFalse(SongMetadataFixes.canFix(catalogue))
        // A download of a catalogue track has a file, but its details are the catalogue's.
        assertFalse(SongMetadataFixes.canFix(catalogue.copy(localUri = "file:///downloads/zyxwvutsrqp.m4a")))
        UploadedTracks.mark(catalogue.videoId)
        assertTrue(SongMetadataFixes.canFix(catalogue))
    }

    @Test
    fun `a fixed file reads its pick wherever it is built, and restores`() {
        val renamed = SongMetadataFixes.fix(file, match)!!
        assertEquals("Real Title", renamed.title)
        assertEquals("Real Artist", renamed.artist)
        assertEquals("Real Album", renamed.albumName)
        assertEquals("MPREb_album", renamed.albumId)
        assertEquals("https://lh3.googleusercontent.com/cover", renamed.thumbnailUrl)
        assertEquals(file.videoId, renamed.videoId)
        assertEquals(file.localUri, renamed.localUri)
        assertEquals(1, saved.size)

        // Built again from the file's own tags, it still reads the pick.
        assertEquals(renamed, SongMetadataFixes.apply(file))

        // A second pick keeps the first original.
        SongMetadataFixes.fix(renamed, match.copy(title = "Other"))
        assertEquals("track01", SongMetadataFixes.fixOf(file)!!.original.title)

        val restored = SongMetadataFixes.restore(renamed)!!
        assertEquals("track01", restored.title)
        assertEquals("Unknown Artist", restored.artist)
        assertEquals(file.thumbnailUrl, restored.thumbnailUrl)
        assertNull(SongMetadataFixes.fixOf(file))
        assertSame(file, SongMetadataFixes.apply(file))
    }

    @Test
    fun `a pick without a cover keeps the song's own`() {
        val renamed = SongMetadataFixes.fix(file, match.copy(thumbnailUrl = null))!!
        assertEquals(file.thumbnailUrl, renamed.thumbnailUrl)
    }

    @Test
    fun `a catalogue track is never renamed`() {
        val catalogue = Song(videoId = "zyxwvutsrqp", title = "A", artist = "B", thumbnailUrl = null)
        assertNull(SongMetadataFixes.fix(catalogue, match))
        assertTrue(saved.isEmpty())
    }
}
